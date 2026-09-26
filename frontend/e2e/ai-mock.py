"""Deterministic upstream for isolated E2E only; never a teaching-quality oracle."""

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def completion(payload):
    messages = payload.get("messages", [])
    question = next((m.get("content", "") for m in reversed(messages) if m.get("role") == "user"), "")
    if question == "E2E_FAIL_AGENT":
        return 503, {"error": {"message": "Isolated E2E upstream failure"}}
    message = {"role": "assistant"}
    finish = "stop"
    if payload.get("tools") and not any(m.get("role") == "tool" for m in messages):
        finish = "tool_calls"
        message["tool_calls"] = [{"id": "e2e-lesson", "type": "function", "function": {
            "name": "read_tutor_lesson", "arguments": "{}"}}]
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
