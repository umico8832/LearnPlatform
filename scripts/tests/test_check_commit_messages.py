from __future__ import annotations

import contextlib
import importlib.util
import io
import subprocess
import sys
import unittest
from pathlib import Path

SCRIPT_PATH = Path(__file__).resolve().parents[1] / "check-commit-messages.py"
SPEC = importlib.util.spec_from_file_location("check_commit_messages", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("无法加载 Commit Message 检查脚本")
checker = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = checker
SPEC.loader.exec_module(checker)


class CommitMessageValidationTest(unittest.TestCase):
    def assert_valid(self, subject: str, body: str = "") -> None:
        self.assertEqual([], checker.validate_commit(subject, body))

    def assert_invalid(self, subject: str, expected: str, body: str = "") -> None:
        failures = checker.validate_commit(subject, body)
        self.assertTrue(
            any(expected in failure for failure in failures),
            f"未发现预期错误 {expected!r}，实际错误：{failures}",
        )

    def test_accepts_supported_types_and_single_kebab_case_scope(self) -> None:
        self.assert_valid("feat(course-store): 建立个人课程库")
        self.assert_valid("build(deps): 升级 Vite 构建依赖")
        self.assert_valid("revert(auth): 撤销认证页主题调整")

    def test_accepts_exact_subject_length_limit(self) -> None:
        self.assert_valid("feat: " + "修" * 66)

    def test_accepts_structured_body(self) -> None:
        self.assert_valid(
            "ci(git): 固定提交正文排版校验",
            "- 禁止正文中的 Round 行和连续自然段\n"
            "- 固定验证区块的空行与项目符号结构\n\n"
            "验证：\n\n"
            "- python3 -m unittest scripts/tests/test_check_commit_messages.py\n"
            "- python3 scripts/check-docs.py",
        )

    def test_accepts_explicit_breaking_change(self) -> None:
        self.assert_valid(
            "feat(learning)!: 统一课程学习状态契约",
            "- 统一课程学习事件接口\n\n"
            "BREAKING CHANGE: 客户端必须改用新的学习事件接口。",
        )

    def test_rejects_unknown_type(self) -> None:
        self.assert_invalid("release: 发布新版本", "主题格式")

    def test_rejects_multiple_or_uppercase_scopes(self) -> None:
        self.assert_invalid("feat(backend,frontend): 建立投稿流程", "主题格式")
        self.assert_invalid("feat(Auth): 建立登录流程", "主题格式")

    def test_rejects_subject_without_chinese(self) -> None:
        self.assert_invalid("fix(auth): refresh token", "必须包含中文")

    def test_rejects_subject_over_length_limit(self) -> None:
        self.assert_invalid("feat: " + "修" * 67, "超过 72")

    def test_rejects_forbidden_terminal_punctuation(self) -> None:
        self.assert_invalid("docs: 更新开发文档。", "不能以句号")

    def test_rejects_unpaired_breaking_change_marker(self) -> None:
        self.assert_invalid(
            "feat(api)!: 调整课程接口",
            "必须提供 BREAKING CHANGE",
        )
        self.assert_invalid(
            "feat(api): 调整课程接口",
            "必须在主题中使用 !",
            "BREAKING-CHANGE: 旧客户端不能继续使用。",
        )

    def test_rejects_round_line_in_body(self) -> None:
        self.assert_invalid(
            "docs(agent): 收紧提交正文规则",
            "Round 只属于 changelog",
            "Round 244：收紧提交正文规则\n- 固定正文排版",
        )

    def test_rejects_plain_paragraph_in_body(self) -> None:
        self.assert_invalid(
            "ci(git): 收紧正文校验",
            "不得出现独立自然段",
            "这里是一段连续自然段。\n\n验证：\n\n- python3 -m unittest",
        )

    def test_rejects_section_without_blank_lines(self) -> None:
        self.assert_invalid(
            "ci(git): 收紧正文校验",
            "区块前必须保留一个空行",
            "- 固定正文格式\n验证：\n- python3 -m unittest",
        )
        self.assert_invalid(
            "ci(git): 收紧正文校验",
            "区块后必须保留一个空行",
            "- 固定正文格式\n\n验证：\n- python3 -m unittest",
        )


class CommitRangeCheckTest(unittest.TestCase):
    """覆盖 run_check 对普通 push 与明确 force push 的范围校验行为。"""

    UNREACHABLE = "f" * 40
    HEAD = "b" * 40
    BASE = "a" * 40
    VALID = checker.CommitRecord(
        "b" * 40,
        "ci(git): 校验提交范围",
        "- 校验当前提交范围\n\n验证：\n\n- python3 -m unittest",
    )

    def run_check(self, base: str, head: str, forced: bool = False) -> tuple[int, str, str]:
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = checker.run_check(base, head, forced=forced)
        return code, out.getvalue(), err.getvalue()

    def test_normal_push_with_reachable_base_passes(self) -> None:
        original = checker.read_commits
        checker.read_commits = lambda revision: [self.VALID]
        try:
            code, _, _ = self.run_check(self.BASE, self.HEAD, forced=False)
        finally:
            checker.read_commits = original
        self.assertEqual(0, code)

    def test_normal_push_with_unreachable_base_fails(self) -> None:
        original = checker.read_commits
        checker.read_commits = lambda revision: (_ for _ in ()).throw(
            subprocess.CalledProcessError(128, ["git", "log"])
        )
        try:
            code, _, err = self.run_check(self.UNREACHABLE, self.HEAD, forced=False)
        finally:
            checker.read_commits = original
        self.assertEqual(2, code)
        self.assertIn("读取提交记录失败", err)

    def test_forced_push_with_unreachable_base_validates_head(self) -> None:
        original_read = checker.read_commits
        original_head = checker.read_head_commit
        checker.read_commits = lambda revision: (_ for _ in ()).throw(
            subprocess.CalledProcessError(128, ["git", "log"])
        )
        checker.read_head_commit = lambda head: [self.VALID]
        try:
            code, _, err = self.run_check(self.UNREACHABLE, self.HEAD, forced=True)
        finally:
            checker.read_commits = original_read
            checker.read_head_commit = original_head
        self.assertEqual(0, code)
        self.assertIn("force push", err)

    def test_forced_push_with_reachable_base_still_checks_range(self) -> None:
        original = checker.read_commits
        checker.read_commits = lambda revision: [self.VALID]
        try:
            code, _, _ = self.run_check(self.BASE, self.HEAD, forced=True)
        finally:
            checker.read_commits = original
        self.assertEqual(0, code)

    def test_forced_push_with_invalid_head_message_fails(self) -> None:
        original_read = checker.read_commits
        original_head = checker.read_head_commit
        checker.read_commits = lambda revision: (_ for _ in ()).throw(
            subprocess.CalledProcessError(128, ["git", "log"])
        )
        checker.read_head_commit = lambda head: [
            checker.CommitRecord("a" * 40, "release: 非法主题", "")
        ]
        try:
            code, _, err = self.run_check(self.UNREACHABLE, self.HEAD, forced=True)
        finally:
            checker.read_commits = original_read
            checker.read_head_commit = original_head
        self.assertEqual(1, code)
        self.assertIn("不符合项目规范", err)


if __name__ == "__main__":
    unittest.main()
