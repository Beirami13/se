import os
import tempfile
import unittest

import database
import handlers
from bale_api import BaleAPIError
from database import get_media, init_db, list_media

CHAT = 100
BALE_ID = 100


class FakeAPI:
    """Records what the bot sends, like a tiny fake Bale server."""

    def __init__(self):
        self.messages = []       # every sent/edited message, in order
        self.answers = []
        self.last_message_id = 0
        self.fail_edit = False

    def send_message(self, chat_id, text, reply_markup=None):
        self.last_message_id += 1
        self.messages.append({"id": self.last_message_id, "text": text, "markup": reply_markup, "edited": False})

    def edit_message(self, chat_id, message_id, text, reply_markup=None):
        if self.fail_edit:
            raise BaleAPIError("message to edit not found", 400)
        self.messages.append({"id": message_id, "text": text, "markup": reply_markup, "edited": True})

    def answer_callback(self, callback_id, text=None):
        self.answers.append(text)

    @property
    def last(self):
        return self.messages[-1]

    def buttons(self):
        markup = self.last["markup"] or {}
        return [b for row in markup.get("inline_keyboard", []) for b in row]


class BotTestCase(unittest.TestCase):
    def setUp(self):
        handle, self.path = tempfile.mkstemp(suffix=".db")
        os.close(handle)
        database.DATABASE_PATH = self.path
        init_db()
        handlers._states.clear()
        handlers._list_ctx.clear()
        self.api = FakeAPI()
        self.user = database.get_or_create_user(str(BALE_ID))

    def tearDown(self):
        os.remove(self.path)

    # -- helpers that act like a person using Bale --
    def say(self, text, bale_id=BALE_ID, chat_type="private"):
        handlers.handle_update({
            "update_id": 1,
            "message": {"text": text, "from": {"id": bale_id}, "chat": {"id": bale_id, "type": chat_type}},
        }, self.api)

    def press_data(self, data, bale_id=BALE_ID, message_id=None):
        handlers.handle_update({
            "update_id": 1,
            "callback_query": {
                "id": "cb", "from": {"id": bale_id}, "data": data,
                "message": {"message_id": message_id or self.api.last["id"], "chat": {"id": bale_id}},
            },
        }, self.api)

    def press(self, label):
        """Press the visible button whose text contains `label`."""
        matches = [b for b in self.api.buttons() if label in b["text"]]
        self.assertTrue(matches, f"no button '{label}' in: {[b['text'] for b in self.api.buttons()]}")
        self.press_data(matches[0]["callback_data"])

    def add_item(self, title="Arrival", media_type="movie", year=2016, status="watched", rating=9, review="Great"):
        return database.add_media(self.user, title, media_type, year, status, rating, review)


class AddFlowTests(BotTestCase):
    def test_full_add_flow_watched(self):
        self.say("/add")
        self.say("Arrival")
        self.press("فیلم")
        self.say("2016")
        self.press("دیدمش")
        self.press("9")
        self.say("ایده داستانی عالی بود | با خط جداکننده")
        item = list_media(self.user)[0]
        self.assertEqual(item["title"], "Arrival")
        self.assertEqual(item["media_type"], "movie")
        self.assertEqual(item["release_year"], 2016)
        self.assertEqual(item["status"], "watched")
        self.assertEqual(item["rating"], 9)
        self.assertEqual(item["review"], "ایده داستانی عالی بود | با خط جداکننده")
        self.assertIn("اضافه شد", self.api.last["text"])
        self.assertIn("ایده داستانی عالی بود", self.api.last["text"])

    def test_watchlist_with_skipped_year_saves_immediately(self):
        self.say("➕ افزودن اثر")  # menu button works like /add
        self.say("Dark")
        self.press("سریال")
        self.press("رد کردن")
        self.press("می‌خوام ببینمش")
        item = list_media(self.user)[0]
        self.assertEqual((item["title"], item["media_type"], item["status"]), ("Dark", "series", "watchlist"))
        self.assertIsNone(item["release_year"])
        self.assertIsNone(item["rating"])
        self.assertIsNone(item["review"])

    def test_skip_rating_and_review(self):
        self.say("/add"); self.say("X"); self.press("فیلم"); self.press("رد کردن"); self.press("دیدمش")
        self.press("رد کردن")  # rating
        self.press("رد کردن")  # review
        item = list_media(self.user)[0]
        self.assertEqual(item["status"], "watched")
        self.assertIsNone(item["rating"])
        self.assertIsNone(item["review"])

    def test_persian_digits_for_year_and_rating_typed(self):
        self.say("/add"); self.say("X"); self.press("فیلم")
        self.say("۲۰۱۶")
        self.press("دیدمش")
        self.say("۸")
        self.press("رد کردن")
        item = list_media(self.user)[0]
        self.assertEqual((item["release_year"], item["rating"]), (2016, 8))

    def test_invalid_year_and_rating_are_rejected_and_reasked(self):
        self.say("/add"); self.say("X"); self.press("فیلم")
        for bad in ("abc", "1500", "3000", "2016.5"):
            self.say(bad)
            self.assertIn("سال معتبر نیست", self.api.last["text"])
        self.say("2020")
        self.press("دیدمش")
        for bad in ("0", "11", "ده", "7.5"):
            self.say(bad)
            self.assertIn("امتیاز باید", self.api.last["text"])
        self.say("10")
        self.press("رد کردن")
        self.assertEqual(list_media(self.user)[0]["rating"], 10)

    def test_text_during_button_step_reshows_buttons(self):
        self.say("/add"); self.say("X")
        self.say("فیلم")  # typed instead of pressing the button
        self.assertIn("دکمه", self.api.last["text"])
        self.assertTrue(any("فیلم" in b["text"] for b in self.api.buttons()))

    def test_title_too_long(self):
        self.say("/add")
        self.say("x" * 201)
        self.assertIn("طولانی", self.api.last["text"])
        self.assertEqual(handlers._states[self.user]["step"], "title")

    def test_cancel_button_and_command(self):
        self.say("/add"); self.say("X")
        self.press("انصراف")
        self.assertEqual(list_media(self.user), [])
        self.assertNotIn(self.user, handlers._states)
        self.say("/add"); self.say("/cancel")
        self.assertNotIn(self.user, handlers._states)
        self.say("random text")  # not captured by an old flow
        self.assertIn("متوجه نشدم", self.api.last["text"])

    def test_command_in_the_middle_restarts_cleanly(self):
        self.say("/add"); self.say("Half done")
        self.say("/list")
        self.assertEqual(list_media(self.user), [])
        self.assertNotIn(self.user, handlers._states)

    def test_stale_button_after_restart_is_harmless(self):
        self.say("/add"); self.say("X")
        keep = self.api.last["markup"]["inline_keyboard"][0][0]["callback_data"]
        handlers._states.clear()  # simulates the bot restarting
        self.press_data(keep)
        self.assertEqual(len(self.api.answers), 1)  # answered exactly once
        self.assertIn("منقضی", self.api.answers[0])
        self.assertEqual(list_media(self.user), [])


class ListAndDetailTests(BotTestCase):
    def setUp(self):
        super().setUp()
        self.w = self.add_item("Arrival", status="watched")
        self.p = self.add_item("Dark", "series", 2017, "watchlist", None, None)

    def labels(self):
        return [b["text"] for b in self.api.buttons()]

    def test_list_shows_all(self):
        self.say("/list")
        text = " ".join(self.labels())
        self.assertIn("Arrival", text)
        self.assertIn("Dark", text)

    def test_watched_and_watchlist_filters(self):
        # regression: the original bot ignored these filters
        self.say("/watched")
        text = " ".join(self.labels())
        self.assertIn("Arrival", text)
        self.assertNotIn("Dark", text)
        self.say("/watchlist")
        text = " ".join(self.labels())
        self.assertIn("Dark", text)
        self.assertNotIn("Arrival", text)

    def test_menu_buttons_work_as_commands(self):
        self.say(handlers.BTN_WATCHED)
        self.assertNotIn("Dark", " ".join(self.labels()))
        self.say(handlers.BTN_ALL)
        self.assertIn("Dark", " ".join(self.labels()))

    def test_filter_buttons_inside_the_list(self):
        self.say("/list")
        self.press("دیده‌شده")
        self.assertNotIn("Dark", " ".join(self.labels()))
        self.assertTrue(self.api.last["edited"])  # edited in place, no new message

    def test_empty_list(self):
        database.delete_media(self.user, self.w)
        database.delete_media(self.user, self.p)
        self.say("/list")
        self.assertIn("هنوز اثری", self.api.last["text"])

    def test_item_details(self):
        self.say("/list")
        self.press("Arrival")
        text = self.api.last["text"]
        for expected in ("Arrival", "فیلم", "2016", "دیده‌شده", "9/10", "Great"):
            self.assertIn(expected, text)
        self.assertNotIn("9.0", text)

    def test_item_details_with_missing_optional_fields(self):
        self.press_data(f"v:{self.p}", message_id=1)
        self.assertIn("امتیاز: ثبت نشده", self.api.last["text"])
        self.assertIn("نقد: ثبت نشده", self.api.last["text"])

    def test_pagination(self):
        for i in range(12):
            self.add_item(f"Movie {i}")
        self.say("/list")
        self.assertIn("1/2", " ".join(self.labels()))
        self.assertEqual(len([l for l in self.labels() if "Movie" in l or "Arrival" in l or "Dark" in l]), 8)
        self.press("بعدی")
        self.assertIn("2/2", " ".join(self.labels()))
        self.press("قبلی")
        self.assertIn("1/2", " ".join(self.labels()))

    def test_back_returns_to_same_filter(self):
        self.say("/watchlist")
        self.press("Dark")
        self.press("بازگشت")
        self.assertIn("در انتظار", self.api.last["text"])
        self.assertNotIn("Arrival", " ".join(self.labels()))

    def test_page_is_clamped_after_deletions(self):
        self.press_data("l:a:99", message_id=1)
        self.assertIn("Arrival", " ".join(self.labels()))

    def test_edit_falls_back_to_new_message(self):
        self.api.fail_edit = True
        self.say("/list")
        self.press("Arrival")
        self.assertFalse(self.api.last["edited"])
        self.assertIn("Arrival", self.api.last["text"])


class EditAndDeleteTests(BotTestCase):
    def setUp(self):
        super().setUp()
        self.mid = self.add_item("Arrival")

    def open_edit(self, field):
        self.say("/list")
        self.press("Arrival")
        self.press("ویرایش")
        self.press(field)

    def item(self):
        return get_media(self.user, self.mid)

    def test_edit_title(self):
        self.open_edit("عنوان")
        self.say("Interstellar")
        self.assertEqual(self.item()["title"], "Interstellar")
        self.assertIn("Interstellar", self.api.last["text"])

    def test_edit_type(self):
        self.open_edit("نوع")
        self.press("سریال")
        self.assertEqual(self.item()["media_type"], "series")

    def test_edit_year_and_clear_year(self):
        self.open_edit("سال")
        self.say("2014")
        self.assertEqual(self.item()["release_year"], 2014)
        self.press("ویرایش"); self.press("سال")
        self.say("nope")
        self.assertIn("معتبر نیست", self.api.last["text"])
        self.press("حذف سال")
        self.assertIsNone(self.item()["release_year"])

    def test_edit_status_rating_and_clear_rating(self):
        self.open_edit("وضعیت")
        self.press("در انتظار")
        self.assertEqual(self.item()["status"], "watchlist")
        self.press("ویرایش"); self.press("امتیاز")
        self.press("5")
        self.assertEqual(self.item()["rating"], 5)
        self.press("ویرایش"); self.press("امتیاز")
        self.press("حذف امتیاز")
        self.assertIsNone(self.item()["rating"])

    def test_edit_and_clear_review(self):
        self.open_edit("نقد")
        self.say("نظر جدید")
        self.assertEqual(self.item()["review"], "نظر جدید")
        self.press("ویرایش"); self.press("نقد")
        self.press("حذف نقد")
        self.assertIsNone(self.item()["review"])

    def test_quick_status_toggle(self):
        self.say("/list"); self.press("Arrival")
        self.press("برگردان به انتظار")
        self.assertEqual(self.item()["status"], "watchlist")
        self.press("دیدمش")
        self.assertEqual(self.item()["status"], "watched")

    def test_delete_requires_confirmation(self):
        self.say("/list"); self.press("Arrival")
        self.press("حذف")
        self.assertIn("حذف بشه", self.api.last["text"])
        self.assertIsNotNone(self.item())  # not deleted yet
        self.press("نه")
        self.assertIsNotNone(self.item())
        self.press("حذف"); self.press("بله")
        self.assertIsNone(self.item())
        self.assertIn("حذف شد", self.api.last["text"])

    def test_edit_flow_is_interrupted_by_navigation(self):
        self.open_edit("نقد")
        self.press("انصراف")
        self.say("this is not a review")
        self.assertEqual(self.item()["review"], "Great")

    def test_long_review_rejected(self):
        self.open_edit("نقد")
        self.say("x" * 1501)
        self.assertEqual(self.item()["review"], "Great")
        self.assertIn("طولانی", self.api.last["text"])


class SecurityAndRobustnessTests(BotTestCase):
    def test_other_user_cannot_view_edit_or_delete(self):
        mid = self.add_item("Secret")
        other = 555
        database.get_or_create_user(str(other))
        for data in (f"v:{mid}", f"e:{mid}", f"ef:{mid}:title", f"set:{mid}:rating:1",
                     f"clr:{mid}:review", f"d:{mid}", f"dy:{mid}"):
            self.press_data(data, bale_id=other, message_id=1)
            self.assertNotIn("Secret", self.api.last["text"], data)
        item = get_media(self.user, mid)
        self.assertEqual((item["title"], item["rating"], item["review"]), ("Secret", 9, "Great"))

    def test_garbage_callback_data_does_not_crash(self):
        for data in ("", "zzz", "v:abc", "v", "l:a", "l:x:0", "set:1:rating:99", "set:1:foo:bar",
                     "clr:1:title", "a", "ef:1:nope", "a:type"):
            self.press_data(data, message_id=1)  # must not raise

    def test_messages_in_groups_are_ignored(self):
        self.say("/list", chat_type="group")
        self.assertEqual(self.api.messages, [])

    def test_non_text_message(self):
        handlers.handle_update({"message": {"from": {"id": 1}, "chat": {"id": 1, "type": "private"}, "photo": []}}, self.api)
        self.assertIn("متنی", self.api.last["text"])

    def test_edited_messages_are_ignored(self):
        handlers.handle_update({"edited_message": {"text": "/add", "from": {"id": 1}, "chat": {"id": 1}}}, self.api)
        self.assertEqual(self.api.messages, [])

    def test_start_help_and_unknown(self):
        self.say("/start")
        self.assertIn("خوش آمدی", self.api.last["text"])
        self.assertIn("keyboard", self.api.last["markup"])
        self.say("/help@MyCineBot")
        self.assertIn("خوش آمدی", self.api.last["text"])
        self.say("/whatever")
        self.assertIn("نشناختم", self.api.last["text"])
        self.say("/edit")
        self.assertIn("/list", self.api.last["text"])

    def test_two_users_have_independent_flows(self):
        self.say("/add", bale_id=1)
        self.say("/add", bale_id=2)
        self.say("Movie A", bale_id=1)
        self.say("Movie B", bale_id=2)
        u1 = database.get_or_create_user("1"); u2 = database.get_or_create_user("2")
        self.assertEqual(handlers._states[u1]["title"], "Movie A")
        self.assertEqual(handlers._states[u2]["title"], "Movie B")


if __name__ == "__main__":
    unittest.main()
