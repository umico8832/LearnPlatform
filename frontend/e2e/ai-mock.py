"""Deterministic upstream for isolated E2E only; never a teaching-quality oracle."""

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


CHECK_REQUEST = "E2E_REQUEST_CHECK"
CHECK_FOLLOW_UP = "请根据我本节理解检查的实际作答，继续指导我。"
PRACTICE_REQUEST = "请推荐一道本节已审查的变式题，让我自己作答。"
PRACTICE_FOLLOW_UP = "请根据我刚才变式练习的服务端结果，继续指导我。"
VARIANT_MARKER = "E2E_TUTOR_VARIANT"
HINT_REQUEST = "请给我本节理解检查的下一步提示，不要直接告诉我答案。"
PLAN_REQUEST = "请建议本课程接下来的学习安排，由我确认是否采用。"
PLAN_STATE_REQUEST = "E2E_READ_PLAN_STATE"


def current_turn(messages):
    for index in range(len(messages) - 1, -1, -1):
        if messages[index].get("role") == "user":
            return messages[index:]
    return []


def tool_names(messages):
    return {
        call.get("function", {}).get("name")
        for message in messages
        if message.get("role") == "assistant"
        for call in message.get("tool_calls", [])
    }


def tool_call(identifier, name):
    return {"id": identifier, "type": "function", "function": {"name": name, "arguments": "{}"}}


def result_from_current_turn(messages, tool_name="read_tutor_check_result", field="result"):
    calls = {
        call.get("id"): call.get("function", {}).get("name")
        for message in messages
        if message.get("role") == "assistant"
        for call in message.get("tool_calls", [])
    }
    for message in reversed(messages):
        if message.get("role") == "tool" and calls.get(message.get("tool_call_id")) == tool_name:
            try:
                return json.loads(message.get("content", "{}")).get(field, {})
            except (TypeError, ValueError):
                return {}
    return {}


def hint_level_from_current_turn(messages):
    calls = {call.get("id"): call.get("function", {}).get("name") for message in messages if message.get("role") == "assistant" for call in message.get("tool_calls", [])}
    for message in reversed(messages):
        if message.get("role") == "tool" and calls.get(message.get("tool_call_id")) == "request_tutor_hint":
            try:
                return json.loads(message.get("content", "{}")).get("level")
            except (TypeError, ValueError):
                return None
    return None


def completion(payload):
    messages = payload.get("messages", [])
    turn = current_turn(messages)
    question = turn[0].get("content", "") if turn else ""
    if question == "E2E_FAIL_AGENT":
        return 503, {"error": {"message": "Isolated E2E upstream failure"}}
    message = {"role": "assistant"}
    finish = "stop"
    calls = tool_names(turn)
    if payload.get("tools") and "read_tutor_lesson" not in calls:
        finish = "tool_calls"
        message["tool_calls"] = [tool_call("e2e-lesson", "read_tutor_lesson")]
    elif payload.get("tools") and question == CHECK_REQUEST and "present_tutor_check" not in calls:
        finish = "tool_calls"
        message["tool_calls"] = [tool_call("e2e-check", "present_tutor_check")]
    elif payload.get("tools") and question == CHECK_FOLLOW_UP and "read_tutor_check_result" not in calls:
        finish = "tool_calls"
        message["tool_calls"] = [tool_call("e2e-result", "read_tutor_check_result")]
    elif payload.get("tools") and question == HINT_REQUEST and "request_tutor_hint" not in calls:
        finish = "tool_calls"
        message["tool_calls"] = [tool_call("e2e-hint", "request_tutor_hint")]
    elif payload.get("tools") and question == PRACTICE_REQUEST and "recommend_tutor_practice" not in calls:
        finish = "tool_calls"
        message["tool_calls"] = [tool_call("e2e-practice", "recommend_tutor_practice")]
    elif payload.get("tools") and question == PRACTICE_FOLLOW_UP and "read_tutor_practice_result" not in calls:
        finish = "tool_calls"
        message["tool_calls"] = [tool_call("e2e-practice-result", "read_tutor_practice_result")]
    elif payload.get("tools") and question == PLAN_REQUEST and "propose_tutor_plan" not in calls:
        finish = "tool_calls"
        message["tool_calls"] = [tool_call("e2e-plan", "propose_tutor_plan")]
    elif payload.get("tools") and question == PLAN_STATE_REQUEST and "read_tutor_plan_state" not in calls:
        finish = "tool_calls"
        message["tool_calls"] = [tool_call("e2e-plan-state", "read_tutor_plan_state")]
    elif question == PLAN_STATE_REQUEST:
        plan = result_from_current_turn(turn, "read_tutor_plan_state", "plan")
        message["content"] = ("服务端已记录计划确认；确认不代表完成学习。" if plan.get("confirmed") is True
                              else "服务端尚未记录计划确认。")
    elif question == PLAN_REQUEST:
        message["content"] = "已核对课程记录，请查看建议并自行确认是否采用。"
    elif question == PRACTICE_FOLLOW_UP:
        correct = result_from_current_turn(turn, "read_tutor_practice_result").get("correct")
        message["content"] = (f"服务端变式练习结果：{'回答正确' if correct else '回答不正确'}。"
                              if isinstance(correct, bool) else "服务端尚未记录变式练习结果，请先自行作答。")
    elif question == HINT_REQUEST:
        level = hint_level_from_current_turn(turn)
        message["content"] = f"第 {level} 级提示已提供。" if level else "当前无法提供下一步提示。"
    elif question == CHECK_FOLLOW_UP:
        correct = result_from_current_turn(turn).get("correct")
        message["content"] = f"服务端判分结果：{'回答正确' if correct else '回答不正确'}。"
    elif payload.get("tools"):
        message["content"] = "本节教学内容已读取。你可以结合步骤继续提问。"
    elif VARIANT_MARKER in question and "questionContent" in messages[0].get("content", ""):
        message["content"] = json.dumps({
            "questionType": "SINGLE_CHOICE", "questionContent": "线性表中除首尾外，每个元素的直接前驱和后继有几个？",
            "options": [{"label": "A", "content": "各一个"}, {"label": "B", "content": "可以有多个"},
                        {"label": "C", "content": "都没有"}, {"label": "D", "content": "无法确定"}],
            "correctAnswer": "A", "analysis": "线性表具有一对一的逻辑关系，中间元素各有一个直接前驱和后继。",
            "difficulty": 1,
        }, ensure_ascii=False)
    else:
        message["content"] = json.dumps({"answerLabels": ["A"], "analysis": "栈遵循后进先出的访问顺序。"}, ensure_ascii=False)
    return 200, {"choices": [{"finish_reason": finish, "message": message}],
                 "usage": {"prompt_tokens": 20, "completion_tokens": 15, "total_tokens": 35}}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def respond(self, status, body):
        data = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self.respond(200 if self.path == "/health" else 404, {})

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self.respond(404, {})
            return
        try:
            payload = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))))
            self.respond(*completion(payload))
        except (ValueError, TypeError):
            self.respond(400, {})


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 80), Handler).serve_forever()
