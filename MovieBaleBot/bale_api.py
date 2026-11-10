"""Tiny wrapper around the Bale Bot API (a Telegram-style HTTP API).

Network errors are re-raised as BaleAPIError WITHOUT the original message, because
requests' error messages contain the full URL, and that URL contains your bot token.
"""
import requests

from config import API_BASE_URL, POLLING_TIMEOUT


class BaleAPIError(RuntimeError):
    def __init__(self, message, code=None):
        super().__init__(message)
        self.code = code


class BaleAPI:
    def __init__(self, token, base_url=API_BASE_URL):
        self.base_url = f"{base_url}/bot{token}"

    def call(self, method, http_timeout=30, **payload):
        try:
            response = requests.post(
                f"{self.base_url}/{method}", json=payload, timeout=http_timeout
            )
        except requests.RequestException as exc:
            raise BaleAPIError(f"Network error ({type(exc).__name__})") from None

        try:
            data = response.json()
        except ValueError:
            data = None
        if not isinstance(data, dict):
            raise BaleAPIError(f"Invalid response (HTTP {response.status_code})", response.status_code)
        if not data.get("ok", False):
            raise BaleAPIError(
                data.get("description") or f"HTTP {response.status_code}",
                data.get("error_code") or response.status_code,
            )
        return data.get("result")

    def get_me(self):
        return self.call("getMe")

    def get_updates(self, offset=None):
        payload = {"timeout": POLLING_TIMEOUT}
        if offset is not None:
            payload["offset"] = offset
        return self.call("getUpdates", http_timeout=POLLING_TIMEOUT + 10, **payload) or []

    def send_message(self, chat_id, text, reply_markup=None):
        payload = {"chat_id": chat_id, "text": text}
        if reply_markup:
            payload["reply_markup"] = reply_markup
        return self.call("sendMessage", **payload)

    def edit_message(self, chat_id, message_id, text, reply_markup=None):
        payload = {"chat_id": chat_id, "message_id": message_id, "text": text}
        if reply_markup:
            payload["reply_markup"] = reply_markup
        return self.call("editMessageText", **payload)

    def answer_callback(self, callback_query_id, text=None):
        payload = {"callback_query_id": callback_query_id}
        if text:
            payload["text"] = text
        return self.call("answerCallbackQuery", **payload)
