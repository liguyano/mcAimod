"""Small standalone tests for Kurumi 0.6 Python agent core.

Run: python -m unittest discover -s python-agent -p 'test_*.py'
"""
import unittest
from kurumi_agent import KurumiAgent, AgentStopped, AgentBudgetExceeded


class AgentTests(unittest.TestCase):
    def test_tool_then_finish(self):
        output = []
        replies = iter([
            '{"type":"tool","name":"get_position","args":[]}',
            '{"type":"finish","message":"done"}',
        ])
        agent = KurumiAgent(lambda _: next(replies),
                            lambda name, *args: {"success": True, "name": name},
                            output.append, lambda: False)
        self.assertTrue(agent.respond("owner", "where am I", "prompt")["success"])
        self.assertEqual(output, ["done"])

    def test_stop_before_model_call(self):
        agent = KurumiAgent(lambda _: self.fail("model should not be called"),
                            lambda name, *args: None,
                            lambda msg: None, lambda: True)
        with self.assertRaises(AgentStopped):
            agent.respond("owner", "go", "prompt")

    def test_stop_after_tool(self):
        stopped = [False]
        def game_tool(name, *args):
            stopped[0] = True
            return {"success": True}
        agent = KurumiAgent(lambda _: '{"type":"tool","name":"get_position","args":[]}',
                            game_tool, lambda _: None, lambda: stopped[0])
        with self.assertRaises(AgentStopped):
            agent.respond("owner", "go", "prompt")

    def test_budget(self):
        agent = KurumiAgent(lambda _: '{"type":"say","message":""}',
                            lambda *_: None, lambda _: None, lambda: False, max_steps=2)
        with self.assertRaises(AgentBudgetExceeded):
            agent.respond("owner", "go", "prompt")


if __name__ == "__main__":
    unittest.main()
