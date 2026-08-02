from __future__ import annotations

import importlib.util
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

    def test_accepts_explicit_breaking_change(self) -> None:
        self.assert_valid(
            "feat(learning)!: 统一课程学习状态契约",
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


if __name__ == "__main__":
    unittest.main()
