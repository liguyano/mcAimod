"""Skill registry tests; run from python-agent."""
import unittest
from kurumi_skills import SkillRegistry, default_skills


class SkillsTests(unittest.TestCase):
    def test_discovery_and_execution(self):
        calls = []
        reg = default_skills(SkillRegistry(
            lambda name, args: calls.append((name, args)) or {"success": True},
            {"goto", "get_position"}, lambda: False))
        self.assertIn("navigate", [item["name"] for item in reg.catalog()])
        reg.invoke("navigate", 1, 64, 2)
        self.assertEqual(calls, [("goto", [1, 64, 2])])

    def test_denied_tool_cannot_be_reached_through_skill(self):
        reg = default_skills(SkillRegistry(lambda *_: self.fail("unauthorized game call"),
                                          {"goto"}, lambda: False))
        with self.assertRaises(KeyError):
            reg.invoke("fight", "minecraft:skeleton")

    def test_emergency_stop(self):
        reg = default_skills(SkillRegistry(lambda *_: self.fail("stop failed"),
                                          {"goto"}, lambda: True))
        with self.assertRaises(InterruptedError):
            reg.invoke("navigate", 1, 64, 2)


if __name__ == "__main__":
    unittest.main()
