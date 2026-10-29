"""Timestamped, persistent Kurumi conversation and long-term memory (stdlib SQLite)."""
from __future__ import annotations
import sqlite3
import threading
from datetime import datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo


class KurumiMemory:
    def __init__(self, file, timezone="Asia/Shanghai"):
        self.path = Path(file)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.zone = ZoneInfo(timezone)
        self.lock = threading.RLock()
        with self._connect() as db:
            db.execute("""CREATE TABLE IF NOT EXISTS conversation (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT NOT NULL, sender TEXT NOT NULL,
                role TEXT NOT NULL, content TEXT NOT NULL)""")
            db.execute("""CREATE INDEX IF NOT EXISTS conversation_by_day
                ON conversation(sender, timestamp)""")
            db.execute("""CREATE TABLE IF NOT EXISTS facts (
                id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp TEXT NOT NULL,
                sender TEXT NOT NULL, content TEXT NOT NULL,
                UNIQUE(sender, content))""")

    def _connect(self):
        return sqlite3.connect(self.path, timeout=15)

    def now(self):
        return datetime.now(self.zone)

    def remember_turn(self, sender, role, content):
        with self.lock, self._connect() as db:
            db.execute("INSERT INTO conversation(timestamp,sender,role,content) VALUES (?,?,?,?)",
                       (self.now().isoformat(), sender, role, str(content)[:12000]))

    def recent(self, sender, limit=20):
        with self.lock, self._connect() as db:
            rows = db.execute("""SELECT role, content FROM conversation WHERE sender=?
                ORDER BY id DESC LIMIT ?""", (sender, min(max(limit, 1), 100))).fetchall()
        return [{"role": role, "content": text} for role, text in reversed(rows)]

    def day(self, sender, day, limit=60):
        # ISO timestamp comparisons are safe within a consistent instance timezone.
        start = day.isoformat()
        end = (day + timedelta(days=1)).isoformat()
        with self.lock, self._connect() as db:
            rows = db.execute("""SELECT timestamp,role,content FROM conversation
                WHERE sender=? AND substr(timestamp,1,10)>=? AND substr(timestamp,1,10)<?
                ORDER BY id DESC LIMIT ?""", (sender, start, end, limit)).fetchall()
        return list(reversed(rows))

    def remember_fact(self, sender, fact):
        value = str(fact).strip()[:1200]
        if not value:
            raise ValueError("Empty fact")
        with self.lock, self._connect() as db:
            db.execute("""INSERT INTO facts(timestamp,sender,content) VALUES (?,?,?)
                ON CONFLICT(sender,content) DO UPDATE SET timestamp=excluded.timestamp""",
                       (self.now().isoformat(), sender, value))

    def facts(self, sender, limit=30):
        with self.lock, self._connect() as db:
            return [row[0] for row in db.execute("""SELECT content FROM facts WHERE sender=?
                ORDER BY timestamp DESC LIMIT ?""", (sender, limit)).fetchall()]

    def context(self, sender, query="", max_chars=3000):
        """Bounded, query-relevant memory. Never inject yesterday's full log by default."""
        now = self.now()
        date = now.date()
        head = ("当前日期时间：" + now.strftime("%Y-%m-%d %A %H:%M:%S %Z")
                + "\\n昨天日期：" + (date - timedelta(days=1)).isoformat())
        fact_lines = []
        seen = set()
        for fact in self.facts(sender, 30):
            normalized = " ".join(fact.casefold().split())
            if normalized in seen:
                continue
            seen.add(normalized)
            fact_lines.append("- " + fact[:250])
        fact_part = "\\n长期事实（可能不完整）：\\n" + ("\\n".join(fact_lines) or "无")

        # Temporal retrieval is opt-in based on the user's actual message.
        wants_yesterday = any(word in query.lower() for word in
                              ("昨天", "昨日", "yesterday", "きのう", "昨日の"))
        yesterday_part = ""
        if wants_yesterday:
            rows = self.day(sender, date - timedelta(days=1), 12)
            yesterday_part = "\\n昨天聊天（按日期检索）：\\n" + (
                "\\n".join(f"[{timestamp}] {role}: {content[:180]}"
                          for timestamp, role, content in rows) or "无记录")
        # Apply a strict character budget (not a true model token count).
        available = max(0, max_chars - len(head) - 35)
        fact_part = fact_part[:available]
        available = max(0, available - len(fact_part))
        yesterday_part = yesterday_part[:available]
        return (head + fact_part + yesterday_part
                + "\\n未检索到的事情不能声称记得。")
