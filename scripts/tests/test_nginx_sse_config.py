import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
NGINX_CONFIG = ROOT / "frontend/nginx.conf"


class NginxSseConfigTest(unittest.TestCase):

    def test_all_backend_sse_routes_use_unbuffered_long_lived_proxy(self):
        config = NGINX_CONFIG.read_text(encoding="utf-8")
        match = re.search(
            r"location\s+~\s+\^/api/\((.*?)\)\$\s*\{(.*?)\n\s*\}",
            config,
            re.DOTALL,
        )

        self.assertIsNotNone(match, "缺少统一的 SSE 反向代理 location")
        route_pattern, directives = match.groups()
        for fragment in (
            "explanation/stream",
            "variant/stream",
            "review-suggestion/stream",
            "asset/stream",
            "statistics/ai-advice/stream",
            "review/ai-suggestion/stream",
            "exam/learning-sessions/",
        ):
            self.assertIn(fragment, route_pattern)
        self.assertIn("proxy_buffering off;", directives)
        self.assertIn("proxy_cache off;", directives)
        self.assertIn("proxy_read_timeout 300s;", directives)


if __name__ == "__main__":
    unittest.main()
