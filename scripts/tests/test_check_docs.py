from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "check-docs.py"
SPEC = importlib.util.spec_from_file_location("check_docs", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("无法加载文档检查脚本")
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)


class StatusOwnershipTest(unittest.TestCase):
    def check_content(self, content: str, relative: str = "docs/project/status.md") -> list[str]:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
            errors: list[str] = []
            with patch.object(checker, "ROOT", root), patch.object(checker, "DOCS_ROOT", root / "docs"):
                checker.check_document_ownership([path], errors)
            return errors

    def test_accepts_current_baseline_with_history_links(self) -> None:
        content = """# 项目状态

## 最新验证基线

| 范围 | 最近验证 | 适用范围与缺口 |
|---|---|---|
| 后端 | 2026-09-05，607 个测试通过 | [Round 311](changelog/2026-09.md#round-311)；之后未改后端 |
| E2E | 2026-08-17，13 条通过 | [第 249 轮](changelog/2026-08.md#round-249)；后续改动未重验 |
"""
        self.assertEqual([], self.check_content(content))

    def test_rejects_appended_round_results(self) -> None:
        for label in ("Round 313", "round 313", "第 313 轮", "第三百一十三轮"):
            with self.subTest(label=label):
                content = f"# 项目状态\n\n## 最新验证基线\n\n导入逻辑拆分（{label}）：\n\n- 11 个测试通过。\n"
                errors = self.check_content(content)
                self.assertTrue(any("round" in error for error in errors), errors)

    def test_round_link_must_point_to_history(self) -> None:
        for target in ("../../product/roadmap.md", "https://example.com/changelog/", "status.md"):
            with self.subTest(target=target):
                errors = self.check_content(f"# 项目状态\n\n[Round 313]({target})\n")
                self.assertTrue(any("round" in error for error in errors), errors)

    def test_rejects_growth_even_without_round_labels(self) -> None:
        at_limit = "# 项目状态\n" + "- 已记录验证。\n" * 149
        self.assertEqual([], self.check_content(at_limit))
        errors = self.check_content(at_limit + "- 又一次验证。\n")
        self.assertTrue(any("150 lines" in error for error in errors), errors)

    def test_rejects_compressing_history_into_long_lines(self) -> None:
        at_limit = "# 项目状态\n".ljust(10000, "文")
        self.assertEqual([], self.check_content(at_limit))
        errors = self.check_content(at_limit + "文")
        self.assertTrue(any("10000 characters" in error for error in errors), errors)

    def test_history_can_keep_long_round_records(self) -> None:
        content = "# 历史\n\n## Round 313\n" + "- 历史证据。\n" * 1600
        for path in ("docs/project/changelog/2026-09.md", "docs/project/audits/2026-09-05.md"):
            with self.subTest(path=path):
                self.assertEqual([], self.check_content(content, path))

    def test_other_documents_still_reject_round_records(self) -> None:
        errors = self.check_content("# 架构\n\nRound 313 已完成。\n", "docs/architecture/overview.md")
        self.assertTrue(any("round" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
