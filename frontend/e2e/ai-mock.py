"""Deterministic upstream for isolated E2E only; never a teaching-quality oracle."""

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


CHECK_REQUEST = "E2E_REQUEST_CHECK"
CHECK_FOLLOW_UP = "请根据我本节理解检查的实际作答，继续指导我。"


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


def result_from_current_turn(messages):
    calls = {
        call.get("id"): call.get("function", {}).get("name")
        for message in messages
        if message.get("role") == "assistant"
        for call in message.get("tool_calls", [])
    }
    for message in reversed(messages):
        if message.get("role") == "tool" and calls.get(message.get("tool_call_id")) == "read_tutor_check_result":
            try:
                return json.loads(message.get("content", "{}")).get("result", {})
            except (TypeError, ValueError):
                return {}
    return {}


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
    elif question == CHECK_FOLLOW_UP:
        correct = result_from_current_turn(turn).get("correct")
        message["content"] = f"服务端判分结果：{'回答正确' if correct else '回答不正确'}。"
    elif payload.get("tools"):
        message["content"] = "本节教学内容已读取。你可以结合步骤继续提问。"
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
