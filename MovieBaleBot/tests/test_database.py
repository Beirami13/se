import os
import sqlite3
import tempfile
import unittest

import database
from database import (
    add_media, count_media, delete_media, get_media, get_or_create_user,
    init_db, list_media, update_media,
)


class DatabaseTests(unittest.TestCase):
    def setUp(self):
        handle, self.path = tempfile.mkstemp(suffix=".db")
        os.close(handle)
        database.DATABASE_PATH = self.path  # fresh database for every test
        init_db()

    def tearDown(self):
        os.remove(self.path)

    def test_users_are_separate(self):
        u1 = get_or_create_user("user-1", "first")
        u2 = get_or_create_user("user-2", "second")
        add_media(u1, "Arrival", "movie", 2016, "watched", 9, "Great")
        self.assertEqual(len(list_media(u1)), 1)
        self.assertEqual(len(list_media(u2)), 0)

    def test_get_or_create_user_is_idempotent(self):
        self.assertEqual(get_or_create_user("x", "a"), get_or_create_user("x", "b"))

    def test_update_and_delete(self):
        user = get_or_create_user("user-3")
        media_id = add_media(user, "Dark", "series", 2017, "watchlist")
        self.assertTrue(update_media(user, media_id, status="watched", rating=8, review="Nice"))
        item = get_media(user, media_id)
        self.assertEqual((item["status"], item["rating"], item["review"]), ("watched", 8, "Nice"))
        self.assertIsInstance(item["rating"], int)
        self.assertTrue(delete_media(user, media_id))
        self.assertEqual(list_media(user), [])

    def test_update_can_clear_optional_fields(self):
        user = get_or_create_user("u")
        media_id = add_media(user, "A", "movie", 2000, "watched", 7, "ok")
        update_media(user, media_id, rating=None, review=None, release_year=None)
        item = get_media(user, media_id)
        self.assertIsNone(item["rating"])
        self.assertIsNone(item["review"])
        self.assertIsNone(item["release_year"])

    def test_update_changes_only_given_fields(self):
        user = get_or_create_user("u")
        media_id = add_media(user, "A", "movie", 2000, "watched", 7, "ok")
        update_media(user, media_id, title="B")
        item = get_media(user, media_id)
        self.assertEqual((item["title"], item["rating"], item["review"]), ("B", 7, "ok"))

    def test_update_rejects_unknown_field(self):
        user = get_or_create_user("u")
        media_id = add_media(user, "A", "movie", None, "watchlist")
        with self.assertRaises(ValueError):
            update_media(user, media_id, created_at="2000-01-01")

    def test_cannot_touch_another_users_media(self):
        owner = get_or_create_user("owner")
        other = get_or_create_user("other")
        media_id = add_media(owner, "Film", "movie", None, "watchlist")
        self.assertFalse(delete_media(other, media_id))
        self.assertFalse(update_media(other, media_id, title="Hacked"))
        self.assertIsNone(get_media(other, media_id))
        self.assertEqual(get_media(owner, media_id)["title"], "Film")

    def test_database_rejects_invalid_values(self):
        user = get_or_create_user("u")
        with self.assertRaises(sqlite3.IntegrityError):
            add_media(user, "A", "movie", None, "watched", 11)
        with self.assertRaises(sqlite3.IntegrityError):
            add_media(user, "A", "book", None, "watched")
        with self.assertRaises(sqlite3.IntegrityError):
            add_media(user, "A", "movie", None, "maybe")

    def test_filter_count_and_pagination(self):
        user = get_or_create_user("u")
        for i in range(5):
            add_media(user, f"W{i}", "movie", None, "watched")
        for i in range(3):
            add_media(user, f"P{i}", "series", None, "watchlist")
        self.assertEqual(count_media(user), 8)
        self.assertEqual(count_media(user, "watched"), 5)
        self.assertEqual({m["status"] for m in list_media(user, "watchlist")}, {"watchlist"})
        page1 = list_media(user, limit=3, offset=0)
        page2 = list_media(user, limit=3, offset=3)
        self.assertEqual(len(page1), 3)
        self.assertEqual(len(page2), 3)
        self.assertFalse({m["id"] for m in page1} & {m["id"] for m in page2})
        # newest first
        self.assertGreater(page1[0]["id"], page1[1]["id"])


if __name__ == "__main__":
    unittest.main()
