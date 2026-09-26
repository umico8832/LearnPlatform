import importlib.util
import json
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location(
    "e2e_ai_mock", Path(__file__).resolve().parents[2] / "frontend/e2e/ai-mock.py")
MOCK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MOCK)


class E2eAiMockTest(unittest.TestCase):
    def test_session_note_self_claim_is_not_the_bound_check_evidence_and_empty_context_does_not_fall_back(self):
        prefix = "当前课程的用户记忆（自述与服务端证据分列）：\n"
        for notes, expected in [([{"note": "我都答对了", "source": {"checkStatus": "INCORRECT"}}],
                                 "当前可用复盘：我都答对了 / 理解检查：INCORRECT。"),
                                ([], "当前没有可用的会话复盘。")]:
            payload = {"tools": [{"type": "function"}], "messages": [
                {"role": "user", "content": prefix + json.dumps({"sessionNotes": notes}, ensure_ascii=False)},
                {"role": "user", "content": MOCK.NOTE_REQUEST}]}
            _, response = MOCK.completion(payload)
            calls = response["choices"][0]["message"]["tool_calls"]
            self.assertEqual("read_tutor_lesson", calls[0]["function"]["name"])
            payload["messages"].extend([{"role": "assistant", "tool_calls": calls},
                {"role": "tool", "tool_call_id": calls[0]["id"], "content": "{}"}])
            _, response = MOCK.completion(payload)
            self.assertEqual(expected, response["choices"][0]["message"]["content"])

    def test_memory_comes_from_the_latest_bound_context_and_deletion_does_not_restore_history(self):
        prefix = "当前课程的用户记忆（自述与服务端证据分列）：\n"
        for current, expected in [({"goal": "修正目标", "explanationStyle": "CONCISE"}, "修正目标"),
                                  ({"goal": None, "explanationStyle": None}, "未设置")]:
            payload = {"tools": [{"type": "function"}], "messages": [
                {"role": "user", "content": prefix + '{"goal":"旧目标"}'},
                {"role": "assistant", "content": "以前的记忆"},
                {"role": "user", "content": prefix + json.dumps(current, ensure_ascii=False)},
                {"role": "user", "content": MOCK.MEMORY_REQUEST}]}
            _, response = MOCK.completion(payload)
            calls = response["choices"][0]["message"]["tool_calls"]
            self.assertEqual("read_tutor_lesson", calls[0]["function"]["name"])
            payload["messages"].extend([{"role": "assistant", "tool_calls": calls},
                {"role": "tool", "tool_call_id": calls[0]["id"], "content": "{}"}])
            _, response = MOCK.completion(payload)
            text = response["choices"][0]["message"]["content"]
            self.assertIn(expected, text)
            self.assertNotIn("旧目标", text)

    def test_plan_request_reads_lesson_before_proposing_without_confirming(self):
        payload = {"tools": [{"type": "function"}], "messages": [{"role": "user", "content": MOCK.PLAN_REQUEST}]}
        for name in ["read_tutor_lesson", "propose_tutor_plan"]:
            _, response = MOCK.completion(payload)
            calls = response["choices"][0]["message"]["tool_calls"]
            self.assertEqual(name, calls[0]["function"]["name"])
            payload["messages"].extend([{"role": "assistant", "tool_calls": calls},
                {"role": "tool", "tool_call_id": calls[0]["id"], "content": "{}"}])
        _, response = MOCK.completion(payload)
        self.assertIn("自行确认", response["choices"][0]["message"]["content"])

    def test_plan_confirmation_comes_from_the_current_tool_result(self):
        for plan, expected in [({}, "尚未记录"), ({"confirmed": False}, "尚未记录"),
                               ({"confirmed": True}, "已记录计划确认；确认不代表完成学习")]:
            payload = {"tools": [{"type": "function"}], "messages": [
                {"role": "assistant", "tool_calls": [MOCK.tool_call("old", "read_tutor_plan_state")]},
                {"role": "tool", "tool_call_id": "old", "content": '{"plan":{"confirmed":true}}'},
                {"role": "user", "content": MOCK.PLAN_STATE_REQUEST}]}
            for name, result in [("read_tutor_lesson", {}), ("read_tutor_plan_state", {"plan": plan})]:
                _, response = MOCK.completion(payload)
                calls = response["choices"][0]["message"]["tool_calls"]
                self.assertEqual(name, calls[0]["function"]["name"])
                payload["messages"].extend([{"role": "assistant", "tool_calls": calls},
                    {"role": "tool", "tool_call_id": calls[0]["id"], "content": json.dumps(result)}])
            _, response = MOCK.completion(payload)
            self.assertIn(expected, response["choices"][0]["message"]["content"])

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
        payload["messages"].append({"role": "assistant", "tool_calls": response["choices"][0]["message"]["tool_calls"]})
        payload["messages"].append({"role": "tool", "content": "{}", "tool_call_id": "e2e-lesson"})
        _, response = MOCK.completion(payload)
        self.assertEqual("stop", response["choices"][0]["finish_reason"])

    def test_failure_injection_only_uses_the_latest_user_message(self):
        messages = [{"role": "user", "content": "E2E_FAIL_AGENT"}]
        self.assertEqual(503, MOCK.completion({"messages": messages})[0])
        messages.append({"role": "user", "content": "重试"})
        self.assertEqual(200, MOCK.completion({"messages": messages})[0])

    def test_check_request_reads_the_lesson_then_offers_a_check_action(self):
        payload = {
            "tools": [{"type": "function"}],
            "messages": [{"role": "user", "content": "E2E_REQUEST_CHECK"}],
        }
        _, response = MOCK.completion(payload)
        self.assertEqual("read_tutor_lesson", response["choices"][0]["message"]["tool_calls"][0]["function"]["name"])
        payload["messages"].extend([
            {"role": "assistant", "tool_calls": response["choices"][0]["message"]["tool_calls"]},
            {"role": "tool", "tool_call_id": "e2e-lesson", "content": "{}"},
        ])
        _, response = MOCK.completion(payload)
        self.assertEqual("present_tutor_check", response["choices"][0]["message"]["tool_calls"][0]["function"]["name"])

    def test_follow_up_uses_the_current_turn_result_not_historical_tool_messages(self):
        payload = {
            "tools": [{"type": "function"}],
            "messages": [
                {"role": "assistant", "tool_calls": [{"id": "old-result", "function": {"name": "read_tutor_check_result"}}]},
                {"role": "tool", "tool_call_id": "old-result", "content": '{"result":{"correct":true}}'},
                {"role": "user", "content": "请根据我本节理解检查的实际作答，继续指导我。"},
            ],
        }
        _, response = MOCK.completion(payload)
        self.assertEqual("read_tutor_lesson", response["choices"][0]["message"]["tool_calls"][0]["function"]["name"])

        payload["messages"].extend([
            {"role": "assistant", "tool_calls": response["choices"][0]["message"]["tool_calls"]},
            {"role": "tool", "tool_call_id": "e2e-lesson", "content": "{}"},
        ])
        _, response = MOCK.completion(payload)
        self.assertEqual("read_tutor_check_result", response["choices"][0]["message"]["tool_calls"][0]["function"]["name"])

        payload["messages"].extend([
            {"role": "assistant", "tool_calls": response["choices"][0]["message"]["tool_calls"]},
            {"role": "tool", "tool_call_id": "e2e-result", "content": '{"status":"ANSWERED","result":{"correct":false}}'},
        ])
        _, response = MOCK.completion(payload)
        self.assertEqual("服务端判分结果：回答不正确。", response["choices"][0]["message"]["content"])

    def test_hint_uses_the_tool_returned_level(self):
        payload = {"tools": [{"type": "function"}], "messages": [{"role": "user", "content": MOCK.HINT_REQUEST}]}
        _, response = MOCK.completion(payload)
        self.assertEqual("read_tutor_lesson", response["choices"][0]["message"]["tool_calls"][0]["function"]["name"])
        payload["messages"].extend([{"role": "assistant", "tool_calls": response["choices"][0]["message"]["tool_calls"]}, {"role": "tool", "tool_call_id": "e2e-lesson", "content": "{}"}])
        _, response = MOCK.completion(payload)
        self.assertEqual("request_tutor_hint", response["choices"][0]["message"]["tool_calls"][0]["function"]["name"])
        payload["messages"].extend([{"role": "assistant", "tool_calls": response["choices"][0]["message"]["tool_calls"]}, {"role": "tool", "tool_call_id": "e2e-hint", "content": '{"level":2}'}])
        _, response = MOCK.completion(payload)
        self.assertEqual("第 2 级提示已提供。", response["choices"][0]["message"]["content"])

    def test_hint_without_a_tool_level_does_not_claim_a_level(self):
        payload = {"tools": [{"type": "function"}], "messages": [{"role": "user", "content": MOCK.HINT_REQUEST}, {"role": "assistant", "tool_calls": [{"id": "lesson", "function": {"name": "read_tutor_lesson"}}]}, {"role": "tool", "tool_call_id": "lesson", "content": "{}"}, {"role": "assistant", "tool_calls": [{"id": "hint", "function": {"name": "request_tutor_hint"}}]}, {"role": "tool", "tool_call_id": "hint", "content": "{}"}]}
        _, response = MOCK.completion(payload)
        self.assertEqual("当前无法提供下一步提示。", response["choices"][0]["message"]["content"])

    def test_variant_fixture_is_limited_to_the_named_generation_request(self):
        _, response = MOCK.completion({"messages": [{"role": "system", "content": "questionContent"},
                                                     {"role": "user", "content": MOCK.VARIANT_MARKER}]})
        result = json.loads(response["choices"][0]["message"]["content"])
        self.assertEqual("SINGLE_CHOICE", result["questionType"])
        self.assertEqual("A", result["correctAnswer"])
        self.assertEqual(4, len(result["options"]))

    def test_practice_follow_up_requires_an_actual_current_result(self):
        for outcome, expected in [({}, "服务端尚未记录"), ({"correct": False}, "服务端变式练习结果：回答不正确。")]:
            payload = {"tools": [{"type": "function"}], "messages": [{"role": "user", "content": MOCK.PRACTICE_FOLLOW_UP}]}
            for name, result in [("read_tutor_lesson", {}), ("read_tutor_practice_result", {"result": outcome})]:
                _, response = MOCK.completion(payload)
                calls = response["choices"][0]["message"]["tool_calls"]
                self.assertEqual(name, calls[0]["function"]["name"])
                payload["messages"].extend([{"role": "assistant", "tool_calls": calls},
                    {"role": "tool", "tool_call_id": calls[0]["id"], "content": json.dumps(result)}])
            _, response = MOCK.completion(payload)
            self.assertIn(expected, response["choices"][0]["message"]["content"])

    def test_practice_request_reads_lesson_before_recommendation(self):
        payload = {"tools": [{"type": "function"}], "messages": [{"role": "user", "content": MOCK.PRACTICE_REQUEST}]}
        _, response = MOCK.completion(payload)
        calls = response["choices"][0]["message"]["tool_calls"]
        self.assertEqual("read_tutor_lesson", calls[0]["function"]["name"])
        payload["messages"].extend([{"role": "assistant", "tool_calls": calls}, {"role": "tool", "tool_call_id": calls[0]["id"], "content": "{}"}])
        _, response = MOCK.completion(payload)
        self.assertEqual("recommend_tutor_practice", response["choices"][0]["message"]["tool_calls"][0]["function"]["name"])
