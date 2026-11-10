"""CineLog - a small personal movie/series journal for Bale.

Run with:  python bot.py
"""
import logging
import time

from bale_api import BaleAPI, BaleAPIError
from config import BOT_TOKEN, PLACEHOLDER_TOKEN
from database import init_db
from handlers import handle_update

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(message)s",
)
logger = logging.getLogger("cinelog")


def _chat_id_of(update):
    """Best-effort extraction of the chat id, to tell the user something went wrong."""
    message = update.get("message") or (update.get("callback_query") or {}).get("message") or {}
    return (message.get("chat") or {}).get("id")


def _check_token(api):
    try:
        me = api.get_me() or {}
    except BaleAPIError as exc:
        if exc.code in (401, 403, 404):
            raise SystemExit(
                "Bale rejected the bot token. Check BALE_BOT_TOKEN in your .env file."
            )
        logger.warning("Could not verify the token right now (%s). Continuing anyway.", exc)
        return
    logger.info("Connected as @%s", me.get("username", "?"))


def main():
    if not BOT_TOKEN or BOT_TOKEN == PLACEHOLDER_TOKEN:
        raise SystemExit(
            "Please put your Bale bot token in the .env file "
            "(BALE_BOT_TOKEN=...). See README.md for instructions."
        )

    init_db()
    api = BaleAPI(BOT_TOKEN)
    _check_token(api)

    offset = None
    delay = 3
    logger.info("CineLog is running. Press Ctrl+C to stop.")

    while True:
        try:
            updates = api.get_updates(offset)
            delay = 3
        except KeyboardInterrupt:
            logger.info("CineLog stopped.")
            return
        except BaleAPIError as exc:
            logger.warning("Could not fetch updates (%s). Retrying in %s s.", exc, delay)
            try:
                time.sleep(delay)
            except KeyboardInterrupt:
                logger.info("CineLog stopped.")
                return
            delay = min(delay * 2, 30)
            continue

        for update in updates:
            update_id = update.get("update_id")
            if update_id is not None:
                offset = update_id + 1
            try:
                handle_update(update, api)
            except KeyboardInterrupt:
                logger.info("CineLog stopped.")
                return
            except Exception as exc:  # noqa: BLE001 - one bad update must not stop the bot
                logger.exception("Error while handling an update")
                chat_id = _chat_id_of(update)
                if chat_id is not None:
                    try:
                        api.send_message(chat_id, "⚠️ مشکلی پیش اومد. دوباره امتحان کن یا /start رو بفرست.")
                    except BaleAPIError:
                        pass


if __name__ == "__main__":
    main()
