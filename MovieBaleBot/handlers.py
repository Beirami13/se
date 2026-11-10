"""Conversation logic for CineLog: menus, buttons, guided flows and formatting.

The only entry point is handle_update(update, api). `api` is any object with the
methods send_message / edit_message / answer_callback (see bale_api.py), which makes
this module easy to test without a network.
"""
import datetime
import logging
import math

import database as db
from bale_api import BaleAPIError
from config import PAGE_SIZE

logger = logging.getLogger(__name__)

MAX_TITLE = 200
MAX_REVIEW = 1500
MIN_YEAR = 1888  # the first film

TYPE_LABEL = {"movie": "فیلم", "series": "سریال"}
TYPE_ICON = {"movie": "🎬", "series": "📺"}
STATUS_LABEL = {"watched": "دیده‌شده", "watchlist": "در انتظار تماشا"}
STATUS_ICON = {"watched": "✅", "watchlist": "⏳"}

# list filter code -> (status in database, title shown to the user)
FILTERS = {
    "a": (None, "📋 همه آثار"),
    "w": ("watched", "✅ آثار دیده‌شده"),
    "p": ("watchlist", "⏳ آثار در انتظار تماشا"),
}

# Database column behind each editable field name used in callback data.
FIELD_COLUMN = {
    "title": "title",
    "type": "media_type",
    "year": "release_year",
    "status": "status",
    "rating": "rating",
    "review": "review",
}

# Persistent menu (reply keyboard) shown at the bottom of the chat.
BTN_ADD = "➕ افزودن اثر"
BTN_ALL = "📋 همه آثار"
BTN_WATCHED = "✅ دیده‌شده‌ها"
BTN_WATCHLIST = "⏳ در انتظار تماشا"
BTN_HELP = "ℹ️ راهنما"

MAIN_MENU = {
    "keyboard": [
        [{"text": BTN_ADD}],
        [{"text": BTN_ALL}],
        [{"text": BTN_WATCHED}, {"text": BTN_WATCHLIST}],
        [{"text": BTN_HELP}],
    ]
}

MENU_TO_COMMAND = {
    BTN_ADD: "/add",
    BTN_ALL: "/list",
    BTN_WATCHED: "/watched",
    BTN_WATCHLIST: "/watchlist",
    BTN_HELP: "/help",
}

HELP = (
    "🎬 به CineLog خوش آمدی!\n\n"
    "اینجا دفترچه‌ی شخصی فیلم و سریال خودته: آثاری که دیدی یا می‌خوای ببینی رو ثبت کن، "
    "امتیاز بده و نقد بنویس.\n\n"
    "با دکمه‌های پایین صفحه کار کن:\n"
    f"{BTN_ADD} — ثبت فیلم یا سریال جدید (قدم‌به‌قدم می‌پرسم)\n"
    f"{BTN_ALL} — فهرست همه‌ی آثار\n"
    f"{BTN_WATCHED} — فقط آثاری که دیدی\n"
    f"{BTN_WATCHLIST} — آثاری که می‌خوای ببینی\n\n"
    "توی هر فهرست روی اسم یک اثر بزن تا جزئیاتش رو ببینی؛ از اونجا می‌تونی "
    "ویرایشش کنی (عنوان، نوع، سال، وضعیت، امتیاز، نقد) یا حذفش کنی.\n\n"
    "دستورهای متنی هم کار می‌کنن:\n"
    "/add  /list  /watched  /watchlist  /cancel  /help"
)

# ---------------------------------------------------------------------------
# In-memory conversation state (cleared when the bot restarts - that is fine,
# the user just starts the step again).
#   _states[user_id]   -> what the bot is waiting for from this user
#   _list_ctx[user_id] -> (filter code, page) of the list the user last looked at
# ---------------------------------------------------------------------------
_states = {}
_list_ctx = {}


# ---------------------------------------------------------------------------
# Small helpers
# ---------------------------------------------------------------------------
_DIGITS = str.maketrans("۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩", "01234567890123456789")


def _normalize_digits(text):
    """Turn Persian/Arabic digits into ASCII digits so int() works."""
    return text.translate(_DIGITS)


def _parse_year(text):
    value = int(_normalize_digits(text.strip()))
    if not MIN_YEAR <= value <= datetime.date.today().year + 5:
        raise ValueError("year out of range")
    return value


def _parse_rating(text):
    value = int(_normalize_digits(text.strip()))
    if not 1 <= value <= 10:
        raise ValueError("rating out of range")
    return value


def _kb(*rows):
    """Build an inline keyboard from rows of (text, callback_data) pairs."""
    return {
        "inline_keyboard": [
            [{"text": text, "callback_data": data} for text, data in row] for row in rows
        ]
    }


def _present(api, chat_id, message_id, text, markup=None):
    """Edit the existing message if we have one, otherwise send a new message."""
    if message_id is not None:
        try:
            api.edit_message(chat_id, message_id, text, markup)
            return
        except BaleAPIError as exc:
            if "not modified" in str(exc).lower():
                return  # same content, nothing to do
            logger.warning("Could not edit message (%s); sending a new one.", exc)
    api.send_message(chat_id, text, markup)


def _safe_answer(api, callback_id, text=None):
    try:
        api.answer_callback(callback_id, text)
    except Exception as exc:  # noqa: BLE001 - never let this break the flow
        logger.warning("answerCallbackQuery failed: %s", exc)


def _shorten(text, limit):
    return text if len(text) <= limit else text[: limit - 1] + "…"


# ---------------------------------------------------------------------------
# Formatting
# ---------------------------------------------------------------------------
def _list_label(item):
    label = f"{TYPE_ICON[item['media_type']]} {_shorten(item['title'], 32)}"
    if item["release_year"]:
        label += f" ({item['release_year']})"
    if item["rating"] is not None:
        label += f" ⭐{item['rating']}"
    return label


def _format_detail(item):
    lines = [
        f"{TYPE_ICON[item['media_type']]} {item['title']}",
        "",
        f"نوع: {TYPE_LABEL[item['media_type']]}",
        f"سال انتشار: {item['release_year'] or 'ثبت نشده'}",
        f"وضعیت: {STATUS_ICON[item['status']]} {STATUS_LABEL[item['status']]}",
        f"امتیاز: {'⭐ ' + str(item['rating']) + '/10' if item['rating'] is not None else 'ثبت نشده'}",
    ]
    if item["review"]:
        lines += ["", "📝 نقد من:", item["review"]]
    else:
        lines.append("نقد: ثبت نشده")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Screens: list, item details, edit menu, delete confirmation
# ---------------------------------------------------------------------------
def _show_list(api, chat_id, message_id, user_id, code, page, notice=""):
    status, heading = FILTERS[code]
    total = db.count_media(user_id, status)
    pages = max(1, math.ceil(total / PAGE_SIZE))
    page = min(max(page, 0), pages - 1)
    _list_ctx[user_id] = (code, page)

    items = db.list_media(user_id, status, limit=PAGE_SIZE, offset=page * PAGE_SIZE)
    if total == 0:
        text = f"{heading}\n\nهنوز اثری در این فهرست نداری."
    else:
        text = f"{heading} ({total} اثر)\nروی هر اثر بزن تا جزئیاتش رو ببینی:"
    if notice:
        text = f"{notice}\n\n{text}"

    rows = [[(_list_label(item), f"v:{item['id']}")] for item in items]
    if pages > 1:
        nav = []
        if page > 0:
            nav.append(("◀ قبلی", f"l:{code}:{page - 1}"))
        nav.append((f"{page + 1}/{pages}", "noop"))
        if page < pages - 1:
            nav.append(("بعدی ▶", f"l:{code}:{page + 1}"))
        rows.append(nav)
    rows.append([
        (("🔘 " if code == key else "") + label, f"l:{key}:0")
        for key, label in (("a", "همه"), ("w", "دیده‌شده"), ("p", "در انتظار"))
    ])
    rows.append([("➕ افزودن اثر", "a:new")])
    _present(api, chat_id, message_id, text, _kb(*rows))


def _show_item(api, chat_id, message_id, user_id, media_id, notice=""):
    item = db.get_media(user_id, media_id)
    if not item:
        _present(api, chat_id, message_id, "این اثر پیدا نشد (شاید قبلاً حذف شده).",
                 _kb([("🔙 بازگشت به فهرست", "back")]))
        return
    if item["status"] == "watchlist":
        toggle = ("✅ دیدمش", f"set:{media_id}:status:watched")
    else:
        toggle = ("⏳ برگردان به انتظار", f"set:{media_id}:status:watchlist")
    text = _format_detail(item)
    if notice:
        text = f"{notice}\n\n{text}"
    _present(api, chat_id, message_id, text, _kb(
        [toggle],
        [("✏️ ویرایش", f"e:{media_id}"), ("🗑 حذف", f"d:{media_id}")],
        [("🔙 بازگشت به فهرست", "back")],
    ))


def _show_edit_menu(api, chat_id, message_id, user_id, media_id):
    item = db.get_media(user_id, media_id)
    if not item:
        _show_item(api, chat_id, message_id, user_id, media_id)
        return
    text = "✏️ کدوم بخش رو ویرایش کنیم؟\n\n" + _format_detail(item)
    _present(api, chat_id, message_id, text, _kb(
        [("عنوان", f"ef:{media_id}:title"), ("نوع", f"ef:{media_id}:type")],
        [("سال", f"ef:{media_id}:year"), ("وضعیت", f"ef:{media_id}:status")],
        [("⭐ امتیاز", f"ef:{media_id}:rating"), ("📝 نقد", f"ef:{media_id}:review")],
        [("🔙 بازگشت", f"v:{media_id}")],
    ))


def _start_edit_field(api, chat_id, message_id, user_id, media_id, field):
    item = db.get_media(user_id, media_id)
    if not item:
        _show_item(api, chat_id, message_id, user_id, media_id)
        return
    back = ("🔙 انصراف", f"e:{media_id}")

    if field == "title":
        _states[user_id] = {"flow": "edit", "step": "title", "id": media_id}
        _present(api, chat_id, message_id,
                 f"عنوان فعلی: {item['title']}\n\nعنوان جدید رو بنویس:", _kb([back]))
    elif field == "year":
        _states[user_id] = {"flow": "edit", "step": "year", "id": media_id}
        _present(api, chat_id, message_id,
                 f"سال فعلی: {item['release_year'] or 'ثبت نشده'}\n\n"
                 "سال انتشار جدید (میلادی، مثل 2016) رو بنویس:",
                 _kb([("🗑 حذف سال", f"clr:{media_id}:year")], [back]))
    elif field == "review":
        _states[user_id] = {"flow": "edit", "step": "review", "id": media_id}
        current = item["review"] or "ثبت نشده"
        _present(api, chat_id, message_id,
                 f"نقد فعلی:\n{current}\n\nنقد جدیدت رو بنویس:",
                 _kb([("🗑 حذف نقد", f"clr:{media_id}:review")], [back]))
    elif field == "type":
        _present(api, chat_id, message_id, "نوع اثر چیه؟", _kb(
            [("🎬 فیلم", f"set:{media_id}:type:movie"),
             ("📺 سریال", f"set:{media_id}:type:series")],
            [back]))
    elif field == "status":
        _present(api, chat_id, message_id, "وضعیت اثر چیه؟", _kb(
            [("✅ دیده‌شده", f"set:{media_id}:status:watched")],
            [("⏳ در انتظار تماشا", f"set:{media_id}:status:watchlist")],
            [back]))
    elif field == "rating":
        _present(api, chat_id, message_id, "امتیازت از ۱ تا ۱۰ چنده؟", _kb(
            [(str(n), f"set:{media_id}:rating:{n}") for n in range(1, 6)],
            [(str(n), f"set:{media_id}:rating:{n}") for n in range(6, 11)],
            [("🗑 حذف امتیاز", f"clr:{media_id}:rating")],
            [back]))
    else:
        raise ValueError("unknown field")


def _apply_set(api, chat_id, message_id, user_id, media_id, field, value):
    """Apply a value chosen with a button (type / status / rating)."""
    if field == "type" and value in TYPE_LABEL:
        parsed, notice = value, "✅ نوع اثر ویرایش شد."
    elif field == "status" and value in STATUS_LABEL:
        parsed = value
        notice = ("✅ به «دیده‌شده» تغییر کرد. اگه خواستی از ✏️ ویرایش امتیاز و نقد هم ثبت کن."
                  if value == "watched" else "✅ به «در انتظار تماشا» تغییر کرد.")
    elif field == "rating":
        parsed, notice = _parse_rating(value), "✅ امتیاز ثبت شد."
    else:
        raise ValueError("bad value")
    if not db.update_media(user_id, media_id, **{FIELD_COLUMN[field]: parsed}):
        _show_item(api, chat_id, message_id, user_id, media_id)
        return
    _show_item(api, chat_id, message_id, user_id, media_id, notice)


# ---------------------------------------------------------------------------
# Add flow (guided, step by step)
#   title -> type -> year -> status -> (rating -> review, only if watched)
# ---------------------------------------------------------------------------
_CANCEL_ROW = [("❌ انصراف", "a:cancel")]


def _add_prompt(step):
    if step == "title":
        return "🎬 اسم فیلم یا سریال رو بنویس:", _kb(_CANCEL_ROW)
    if step == "type":
        return "نوعش چیه؟", _kb(
            [("🎬 فیلم", "a:type:movie"), ("📺 سریال", "a:type:series")], _CANCEL_ROW)
    if step == "year":
        return ("سال انتشارش رو بنویس (میلادی، مثل 2016) یا رد کن:",
                _kb([("⏭ رد کردن", "a:skip:year")], _CANCEL_ROW))
    if step == "status":
        return "وضعیتش چیه؟", _kb(
            [("✅ دیدمش", "a:status:watched")],
            [("⏳ می‌خوام ببینمش", "a:status:watchlist")],
            _CANCEL_ROW)
    if step == "rating":
        return ("امتیازت از ۱ تا ۱۰ چنده؟ (دکمه بزن یا عدد بنویس)", _kb(
            [(str(n), f"a:rating:{n}") for n in range(1, 6)],
            [(str(n), f"a:rating:{n}") for n in range(6, 11)],
            [("⏭ رد کردن", "a:skip:rating")],
            _CANCEL_ROW))
    if step == "review":
        return ("نقد یا نظرت درباره‌ش رو بنویس، یا رد کن:",
                _kb([("⏭ رد کردن", "a:skip:review")], _CANCEL_ROW))
    raise ValueError(step)


def _add_goto(api, chat_id, message_id, user_id, step, notice=""):
    _states[user_id]["step"] = step
    text, markup = _add_prompt(step)
    if notice:
        text = f"{notice}\n\n{text}"
    _present(api, chat_id, message_id, text, markup)


def _add_start(api, chat_id, message_id, user_id):
    _states[user_id] = {"flow": "add", "step": "title"}
    _add_goto(api, chat_id, message_id, user_id, "title")


def _add_finish(api, chat_id, message_id, user_id):
    state = _states.pop(user_id)
    media_id = db.add_media(
        user_id, state["title"], state["media_type"], state.get("year"),
        state["status"], state.get("rating"), state.get("review"),
    )
    _show_item(api, chat_id, message_id, user_id, media_id,
               notice="✅ به دفترچه‌ات اضافه شد.")


def _add_on_callback(api, chat_id, message_id, user_id, parts):
    """Handle a button of the add flow. Returns a short popup text, or None."""
    action = parts[1]
    if action == "new":
        _add_start(api, chat_id, message_id, user_id)
        return None
    if action == "cancel":
        _states.pop(user_id, None)
        _present(api, chat_id, message_id, "❌ لغو شد.")
        return None

    state = _states.get(user_id)
    if not state or state.get("flow") != "add":
        return "این مرحله منقضی شده. دوباره از ➕ افزودن اثر شروع کن."
    step = state["step"]
    value = parts[2] if len(parts) > 2 else None

    if action == "type" and step == "type" and value in TYPE_LABEL:
        state["media_type"] = value
        _add_goto(api, chat_id, message_id, user_id, "year")
    elif action == "status" and step == "status" and value in STATUS_LABEL:
        state["status"] = value
        if value == "watchlist":
            _add_finish(api, chat_id, message_id, user_id)
        else:
            _add_goto(api, chat_id, message_id, user_id, "rating")
    elif action == "rating" and step == "rating":
        state["rating"] = _parse_rating(value)
        _add_goto(api, chat_id, message_id, user_id, "review")
    elif action == "skip" and value == step:
        if step == "year":
            state["year"] = None
            _add_goto(api, chat_id, message_id, user_id, "status")
        elif step == "rating":
            state["rating"] = None
            _add_goto(api, chat_id, message_id, user_id, "review")
        elif step == "review":
            state["review"] = None
            _add_finish(api, chat_id, message_id, user_id)
        else:
            return "این مرحله رو نمی‌شه رد کرد."
    else:
        return "این دکمه دیگه معتبر نیست."
    return None


def _text_in_flow(api, chat_id, user_id, text, state):
    """The user typed text while the bot was waiting for something."""
    flow, step = state["flow"], state["step"]

    # ----- edit flow: waiting for a new title / year / review -----
    if flow == "edit":
        media_id = state["id"]
        rows = [[("🔙 انصراف", f"e:{media_id}")]]
        if step in ("year", "review"):
            label = "🗑 حذف سال" if step == "year" else "🗑 حذف نقد"
            rows.insert(0, [(label, f"clr:{media_id}:{step}")])
        back = _kb(*rows)
        if step == "title":
            if not text or len(text) > MAX_TITLE:
                api.send_message(chat_id, f"⚠️ عنوان باید بین ۱ تا {MAX_TITLE} کاراکتر باشه. دوباره بنویس:", back)
                return
            value, notice = text, "✅ عنوان ویرایش شد."
        elif step == "year":
            try:
                value = _parse_year(text)
            except ValueError:
                api.send_message(chat_id, "⚠️ سال معتبر نیست. یه سال میلادی مثل 2016 بفرست:", back)
                return
            notice = "✅ سال انتشار ویرایش شد."
        elif step == "review":
            if len(text) > MAX_REVIEW:
                api.send_message(chat_id, f"⚠️ نقد خیلی طولانیه (حداکثر {MAX_REVIEW} کاراکتر). کوتاه‌ترش کن:", back)
                return
            value, notice = text, "✅ نقد ویرایش شد."
        else:
            return
        column = {"title": "title", "year": "release_year", "review": "review"}[step]
        _states.pop(user_id, None)
        if not db.update_media(user_id, media_id, **{column: value}):
            api.send_message(chat_id, "این اثر پیدا نشد (شاید حذف شده).")
            return
        _show_item(api, chat_id, None, user_id, media_id, notice)
        return

    # ----- add flow -----
    if step == "title":
        if len(text) > MAX_TITLE:
            _add_goto(api, chat_id, None, user_id, "title",
                      f"⚠️ عنوان خیلی طولانیه (حداکثر {MAX_TITLE} کاراکتر).")
            return
        state["title"] = text
        _add_goto(api, chat_id, None, user_id, "type")
    elif step == "year":
        try:
            state["year"] = _parse_year(text)
        except ValueError:
            _add_goto(api, chat_id, None, user_id, "year",
                      "⚠️ سال معتبر نیست. یه سال میلادی مثل 2016 بفرست یا رد کن.")
            return
        _add_goto(api, chat_id, None, user_id, "status")
    elif step == "rating":
        try:
            state["rating"] = _parse_rating(text)
        except ValueError:
            _add_goto(api, chat_id, None, user_id, "rating",
                      "⚠️ امتیاز باید یه عدد از ۱ تا ۱۰ باشه.")
            return
        _add_goto(api, chat_id, None, user_id, "review")
    elif step == "review":
        if len(text) > MAX_REVIEW:
            _add_goto(api, chat_id, None, user_id, "review",
                      f"⚠️ نقد خیلی طولانیه (حداکثر {MAX_REVIEW} کاراکتر). کوتاه‌ترش کن.")
            return
        state["review"] = text
        _add_finish(api, chat_id, None, user_id)
    else:  # type / status: buttons only
        _add_goto(api, chat_id, None, user_id, step, "لطفاً یکی از دکمه‌های زیر رو بزن 👇")


# ---------------------------------------------------------------------------
# Callback queries (button presses)
# ---------------------------------------------------------------------------
def _on_callback(callback, api):
    callback_id = callback.get("id")
    sender = callback.get("from") or {}
    message = callback.get("message") or {}
    chat_id = (message.get("chat") or {}).get("id")
    message_id = message.get("message_id")
    data = callback.get("data") or ""
    if sender.get("id") is None or chat_id is None:
        _safe_answer(api, callback_id)
        return

    user_id = db.get_or_create_user(str(sender["id"]), sender.get("username"))
    parts = data.split(":")
    action = parts[0]

    if action == "noop":
        _safe_answer(api, callback_id)
        return

    # Stop in-progress text flows, except while inside the add flow itself.
    if action != "a":
        _states.pop(user_id, None)

    toast = None  # optional popup text; a callback may be answered only once
    try:
        if action == "a":
            toast = _add_on_callback(api, chat_id, message_id, user_id, parts)
        elif action == "l":
            _show_list(api, chat_id, message_id, user_id, parts[1], int(parts[2]))
        elif action == "back":
            code, page = _list_ctx.get(user_id, ("a", 0))
            _show_list(api, chat_id, message_id, user_id, code, page)
        elif action == "v":
            _show_item(api, chat_id, message_id, user_id, int(parts[1]))
        elif action == "e":
            _show_edit_menu(api, chat_id, message_id, user_id, int(parts[1]))
        elif action == "ef":
            _start_edit_field(api, chat_id, message_id, user_id, int(parts[1]), parts[2])
        elif action == "set":
            _apply_set(api, chat_id, message_id, user_id, int(parts[1]), parts[2], parts[3])
        elif action == "clr":
            media_id, field = int(parts[1]), parts[2]
            if field not in ("year", "rating", "review"):
                raise ValueError("cannot clear this field")
            if db.update_media(user_id, media_id, **{FIELD_COLUMN[field]: None}):
                _show_item(api, chat_id, message_id, user_id, media_id, "🗑 پاک شد.")
            else:
                _show_item(api, chat_id, message_id, user_id, media_id)
        elif action == "d":
            media_id = int(parts[1])
            item = db.get_media(user_id, media_id)
            if not item:
                _show_item(api, chat_id, message_id, user_id, media_id)
            else:
                _present(api, chat_id, message_id,
                         f"«{item['title']}» حذف بشه؟ این کار برگشت‌پذیر نیست.",
                         _kb([("🗑 بله، حذف کن", f"dy:{media_id}")],
                             [("🔙 نه، بازگشت", f"v:{media_id}")]))
        elif action == "dy":
            deleted = db.delete_media(user_id, int(parts[1]))
            code, page = _list_ctx.get(user_id, ("a", 0))
            _show_list(api, chat_id, message_id, user_id, code, page,
                       notice="🗑 اثر حذف شد." if deleted else "این اثر پیدا نشد.")
        else:
            toast = "دکمه نامعتبر."
    except (ValueError, IndexError, KeyError):
        toast = "دکمه نامعتبر."

    _safe_answer(api, callback_id, toast)


# ---------------------------------------------------------------------------
# Text messages
# ---------------------------------------------------------------------------
def _run_command(command, api, chat_id, user_id):
    if command in ("/start", "/help"):
        api.send_message(chat_id, HELP, MAIN_MENU)
    elif command == "/add":
        _add_start(api, chat_id, None, user_id)
    elif command == "/list":
        _show_list(api, chat_id, None, user_id, "a", 0)
    elif command == "/watched":
        _show_list(api, chat_id, None, user_id, "w", 0)
    elif command == "/watchlist":
        _show_list(api, chat_id, None, user_id, "p", 0)
    elif command == "/cancel":
        api.send_message(chat_id, "❌ لغو شد.", MAIN_MENU)
    elif command in ("/edit", "/delete", "/show"):
        api.send_message(
            chat_id,
            "برای دیدن جزئیات، ویرایش یا حذف: اول /list رو بزن و روی اثر موردنظر بزن.",
            MAIN_MENU,
        )
    else:
        api.send_message(
            chat_id, "دستور را نشناختم. از دکمه‌های پایین صفحه استفاده کن یا /help را بفرست.",
            MAIN_MENU,
        )


def _on_message(message, api):
    chat = message.get("chat") or {}
    sender = message.get("from") or {}
    if chat.get("type") not in (None, "private"):
        return  # the bot is for private chats only
    chat_id, bale_id = chat.get("id"), sender.get("id")
    if chat_id is None or bale_id is None:
        return

    user_id = db.get_or_create_user(str(bale_id), sender.get("username"))
    text = (message.get("text") or "").strip()
    if not text:
        api.send_message(chat_id, "فعلاً فقط پیام متنی پشتیبانی می‌شود. /help")
        return

    # A command or a menu button always interrupts whatever the user was doing.
    if text.startswith("/"):
        command = text.split()[0].split("@")[0].lower()
    else:
        command = MENU_TO_COMMAND.get(text)
    if command:
        _states.pop(user_id, None)
        _run_command(command, api, chat_id, user_id)
        return

    state = _states.get(user_id)
    if state:
        _text_in_flow(api, chat_id, user_id, text, state)
    else:
        api.send_message(
            chat_id, "متوجه نشدم 🤔 از دکمه‌های پایین صفحه استفاده کن یا /help را بفرست.",
            MAIN_MENU,
        )


def handle_update(update, api):
    """Process one raw update from Bale."""
    if update.get("callback_query"):
        _on_callback(update["callback_query"], api)
    elif update.get("message"):
        _on_message(update["message"], api)
    # anything else (edited messages, etc.) is ignored on purpose
