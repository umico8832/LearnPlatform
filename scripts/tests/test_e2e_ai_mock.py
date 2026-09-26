import importlib.util
import json
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location(
    "e2e_ai_mock", Path(__file__).resolve().parents[2] / "frontend/e2e/ai-mock.py")
MOCK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MOCK)


class E2eAiMockTest(unittest.TestCase):
    def test_existing_asset_generation_keeps_structured_answer(self):
        status, response = MOCK.completion({"messages": [{"role": "user", "content": "解释"}]})
        self.assertEqual(200, status)
        result = json.loads(response["choices"][0]["message"]["content"])
        self.assertEqual(["A"], result["answerLabels"])

    def test_agent_requests_lesson_then_uses_a_final_response(self):
        payload = {"tools": [{"type": "function"}], "messages": [{"role": "user", "content": "解释"}]}
        status, response = MOCK.completion(payload)
        self.assertEqual(200, status)
        self.assertEqual("tool_calls", response["choices"][0]["finish_reason"])
        self.assertEqual("read_tutor_lesson", response["choices"][0]["message"]["tool_calls"][0]["function"]["name"])
        payload["messages"].append({"role": "tool", "content": "{}", "tool_call_id": "e2e-lesson"})
        _, response = MOCK.completion(payload)
        self.assertEqual("stop", response["choices"][0]["finish_reason"])

    def test_failure_injection_only_uses_the_latest_user_message(self):
        messages = [{"role": "user", "content": "E2E_FAIL_AGENT"}]
        self.assertEqual(503, MOCK.completion({"messages": messages})[0])
        messages.append({"role": "user", "content": "重试"})
        self.assertEqual(200, MOCK.completion({"messages": messages})[0])
