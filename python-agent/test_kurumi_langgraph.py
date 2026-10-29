"""Run: python -m unittest discover -s python-agent -p 'test_*.py'"""
import unittest
from kurumi_langgraph import KurumiGraph, Stopped


class GraphTests(unittest.TestCase):
    def test_tool_then_finish(self):
        answers = iter([{"type": "tool", "name": "get_position", "args": []},
                        {"type": "finish", "message": "位置已确认"}])
        seen = []
        agent = KurumiGraph(lambda _: next(answers),
                            lambda name, args: seen.append(name) or {"success": True},
                            lambda: False, allowed_tools={"get_position"})
        result = agent.run("owner", "where", "system")
        self.assertEqual(seen, ["get_position"])
        self.assertEqual(result["reply"], "位置已确认")

    def test_reject_unapproved_tool(self):
        agent = KurumiGraph(lambda _: {"type": "tool", "name": "drop_all", "args": []},
                            lambda *args: self.fail("unauthorized tool executed"),
                            lambda: False, allowed_tools={"get_position"})
        with self.assertRaises(PermissionError):
            agent.run("owner", "drop", "system")

    def test_stop(self):
        agent = KurumiGraph(lambda _: self.fail("model should not be called"),
                            lambda *args: None, lambda: True, allowed_tools=set())
        with self.assertRaises(Stopped):
            agent.run("owner", "hello", "system")


if __name__ == "__main__":
    unittest.main()
