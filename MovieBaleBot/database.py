"""SQLite database operations for CineLog.

Every function that touches works (media) takes `user_id` and always filters by it,
so one user can never read, edit or delete another user's data.
"""
import sqlite3
from contextlib import contextmanager

from config import DATABASE_PATH

# Columns that update_media() is allowed to change.
EDITABLE_COLUMNS = ("title", "media_type", "release_year", "status", "rating", "review")


@contextmanager
def get_connection():
    connection = sqlite3.connect(DATABASE_PATH)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")
    try:
        yield connection
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def init_db():
    with get_connection() as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                bale_user_id TEXT NOT NULL UNIQUE,
                username TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS media (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                media_type TEXT NOT NULL CHECK(media_type IN ('movie', 'series')),
                release_year INTEGER,
                status TEXT NOT NULL CHECK(status IN ('watched', 'watchlist')),
                rating INTEGER CHECK(rating IS NULL OR (rating >= 1 AND rating <= 10)),
                review TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """)
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_media_user_status ON media(user_id, status)"
        )


def get_or_create_user(bale_user_id, username=None):
    """Return the internal id of the user, creating the row if needed."""
    with get_connection() as conn:
        conn.execute(
            """INSERT INTO users (bale_user_id, username)
               VALUES (?, ?)
               ON CONFLICT(bale_user_id) DO UPDATE SET username=excluded.username""",
            (bale_user_id, username),
        )
        row = conn.execute(
            "SELECT id FROM users WHERE bale_user_id = ?", (bale_user_id,)
        ).fetchone()
        return row["id"]


def add_media(user_id, title, media_type, release_year, status, rating=None, review=None):
    with get_connection() as conn:
        cursor = conn.execute(
            """INSERT INTO media
               (user_id, title, media_type, release_year, status, rating, review)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            (user_id, title, media_type, release_year, status, rating, review),
        )
        return cursor.lastrowid


def count_media(user_id, status=None):
    with get_connection() as conn:
        if status:
            row = conn.execute(
                "SELECT COUNT(*) AS n FROM media WHERE user_id=? AND status=?",
                (user_id, status),
            ).fetchone()
        else:
            row = conn.execute(
                "SELECT COUNT(*) AS n FROM media WHERE user_id=?", (user_id,)
            ).fetchone()
        return row["n"]


def list_media(user_id, status=None, limit=None, offset=0):
    """Newest first. `status` filters; `limit`/`offset` paginate."""
    query = "SELECT * FROM media WHERE user_id=?"
    params = [user_id]
    if status:
        query += " AND status=?"
        params.append(status)
    query += " ORDER BY id DESC"
    if limit is not None:
        query += " LIMIT ? OFFSET ?"
        params += [limit, offset]
    with get_connection() as conn:
        return [dict(row) for row in conn.execute(query, params).fetchall()]


def get_media(user_id, media_id):
    with get_connection() as conn:
        row = conn.execute(
            "SELECT * FROM media WHERE id=? AND user_id=?", (media_id, user_id)
        ).fetchone()
        return dict(row) if row else None


def update_media(user_id, media_id, **fields):
    """Change one or more fields of a work. Passing None clears an optional field.

    Example: update_media(1, 5, rating=8, review=None)
    Returns True if the work exists (and belongs to the user), else False.
    """
    if not fields:
        return get_media(user_id, media_id) is not None
    unknown = set(fields) - set(EDITABLE_COLUMNS)
    if unknown:
        raise ValueError(f"Unknown field(s): {', '.join(sorted(unknown))}")
    assignments = ", ".join(f"{column}=?" for column in fields)  # names are whitelisted above
    with get_connection() as conn:
        cursor = conn.execute(
            f"UPDATE media SET {assignments} WHERE id=? AND user_id=?",
            (*fields.values(), media_id, user_id),
        )
        return cursor.rowcount > 0


def delete_media(user_id, media_id):
    with get_connection() as conn:
        cursor = conn.execute(
            "DELETE FROM media WHERE id=? AND user_id=?", (media_id, user_id)
        )
        return cursor.rowcount > 0
