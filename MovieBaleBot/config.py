"""Application settings loaded from environment variables (.env file)."""
import os

from dotenv import load_dotenv

load_dotenv()

PLACEHOLDER_TOKEN = "PUT_YOUR_BALE_BOT_TOKEN_HERE"

BOT_TOKEN = os.getenv("BALE_BOT_TOKEN", "").strip()
API_BASE_URL = os.getenv("BALE_API_BASE_URL", "https://tapi.bale.ai").strip().rstrip("/")
DATABASE_PATH = os.getenv("DATABASE_PATH", "cinelog.db").strip()
POLLING_TIMEOUT = int(os.getenv("POLLING_TIMEOUT", "25"))

# How many works are shown on each page of a list.
PAGE_SIZE = 8
