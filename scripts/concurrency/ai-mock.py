"""Local deterministic performance upstream; never a teaching-quality oracle."""
import json
import time
import threading

LOCK = threading.Lock()
STATE = {"active": 0, "peak": 0, "calls": 0, "delay": 1.0}
EVENTS = []
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def do_GET(self):
        if self.path == '/stats':
            with LOCK:
                body = json.dumps({**STATE, 'events': list(EVENTS)}).encode()
            self.send_response(200)
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        self.send_response(200 if self.path == '/health' else 404)
        self.end_headers()

    def do_POST(self):
        payload = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))))
        if self.path == '/control':
            with LOCK:
                if STATE['active']:
                    self.send_error(409)
                    return
                STATE.update(peak=0, calls=0, delay=float(payload.get('delay', 1)))
                EVENTS.clear()
            self.send_response(204)
            self.end_headers()
            return
        with LOCK:
            STATE['active'] += 1
            STATE['calls'] += 1
            STATE['peak'] = max(STATE['peak'], STATE['active'])
            EVENTS.append({'time': time.time(), 'active': STATE['active'], 'event': 'start'})
            delay = STATE['delay']
        try:
            self.respond(payload, delay)
        except (BrokenPipeError, ConnectionResetError):
            pass
        finally:
            with LOCK:
                STATE['active'] -= 1
                EVENTS.append({'time': time.time(), 'active': STATE['active'], 'event': 'finish'})

    def respond(self, payload, delay):
        system = payload.get('messages', [{}])[0].get('content', '')
        text = json.dumps({'safe': True}) if '安全检查器' in system else '先关注栈顶与后进先出的关系，再说明下一步操作。'
        if not payload.get('stream'):
            time.sleep(delay)
            body = json.dumps({'choices': [{'message': {'role': 'assistant', 'content': text},
                'finish_reason': 'stop'}], 'usage': {'prompt_tokens': 20, 'completion_tokens': 15, 'total_tokens': 35}}).encode()
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        try:
            self.send_response(200)
            self.send_header('Content-Type', 'text/event-stream')
            self.end_headers()
            time.sleep(delay)
            for part in [text[:len(text)//2], text[len(text)//2:]]:
                self.frame({'choices': [{'index': 0, 'delta': {'content': part}}]})
                time.sleep(0.1)
            self.frame({'choices': [{'index': 0, 'delta': {}, 'finish_reason': 'stop'}]})
            self.frame({'choices': [], 'usage': {'prompt_tokens': 20, 'completion_tokens': 15, 'total_tokens': 35}})
            self.wfile.write(b'data: [DONE]\n\n')
            self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError):
            pass

    def frame(self, value):
        self.wfile.write(b'data: ' + json.dumps(value, ensure_ascii=False).encode() + b'\n\n')
        self.wfile.flush()


if __name__ == '__main__':
    ThreadingHTTPServer(('0.0.0.0', 80), Handler).serve_forever()
