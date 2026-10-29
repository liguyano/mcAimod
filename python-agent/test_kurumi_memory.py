import tempfile
import unittest
from datetime import timedelta
from pathlib import Path
from kurumi_memory import KurumiMemory


class MemoryTests(unittest.TestCase):
    def test_persists_turns_and_facts(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "memory.db"
            memory = KurumiMemory(path)
            memory.remember_turn("SekaiSaionji", "user", "记得我的家在樱花林")
            memory.remember_fact("SekaiSaionji", "家在樱花林")
            reopened = KurumiMemory(path)
            self.assertEqual(reopened.recent("SekaiSaionji")[0]["content"], "记得我的家在樱花林")
            self.assertIn("家在樱花林", reopened.facts("SekaiSaionji"))
            self.assertIn("昨天日期：", reopened.context("SekaiSaionji"))

    def test_separates_senders(self):
        with tempfile.TemporaryDirectory() as folder:
            memory = KurumiMemory(Path(folder) / "memory.db")
            memory.remember_turn("owner", "user", "private")
            self.assertEqual(memory.recent("other"), [])


if __name__ == "__main__":
    unittest.main()
