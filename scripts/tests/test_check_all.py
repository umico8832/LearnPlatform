from __future__ import annotations

import contextlib
import importlib.util
import io
import os
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "check-all.py"
SPEC = importlib.util.spec_from_file_location("check_all", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("无法加载完整检查脚本")
checker = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = checker
SPEC.loader.exec_module(checker)
HOOK = SCRIPT_PATH.parent / "hooks/pre-push"


class JobContractTest(unittest.TestCase):
    @patch.object(checker.shutil, "which", return_value="/tools/mvn")
    def test_jobs_preserve_ci_commands_and_order(self, which: Mock) -> None:
        steps = {job: checker.job_steps(job, {}) for job in checker.JOBS}
        self.assertEqual(["tools", "commits", "layout", "structure", "documentation"],
                         [step.name for step in steps["docs"]])
        self.assertEqual(("origin/main", "HEAD"), steps["docs"][1].command[-2:])
        self.assertEqual(("/tools/mvn", "clean", "verify", "-B"), steps["backend"][0].command)
        self.assertEqual(("/tools/mvn", "test", "-B", "-DexcludedGroups=", "-Dgroups=integration"),
                         steps["backend-integration"][0].command)
        self.assertEqual([
            ("npm", "ci"), ("npm", "audit", "--audit-level=high"), ("npm", "run", "coverage"),
            ("npm", "run", "lint"), ("npm", "run", "build"),
        ], [step.command for step in steps["frontend"]])
        self.assertEqual([
            ("docker", "build", "-t", "learnplatform-backend", "./backend"),
            ("docker", "build", "-t", "learnplatform-frontend", "./frontend"),
        ], [step.command for step in steps["docker"]])
        self.assertEqual([("npm", "ci"), ("npm", "run", "test:e2e")],
                         [step.command for step in steps["e2e"]])
        for job in ("backend", "backend-integration", "frontend", "e2e"):
            self.assertTrue(all(step.directory == ("backend" if job.startswith("backend") else "frontend")
                                for step in steps[job]))

    @patch.object(checker.shutil, "which", return_value=None)
    def test_maven_wrapper_fallback(self, which: Mock) -> None:
        self.assertEqual(("sh", "./mvnw"), checker.maven())

    def test_event_range_and_dependabot_exception(self) -> None:
        env = {"BASE_SHA": "0" * 40, "HEAD_SHA": "event-head", "FORCED_PUSH": "true"}
        self.assertEqual(("0" * 40, "event-head"), checker.job_steps("docs", env)[1].command[-2:])
        self.assertNotIn("commits", [step.name for step in checker.job_steps("docs", env, skip_commits=True)])
        self.assertEqual(4, len(checker.job_steps("docs", env, skip_commits=True)))

    def test_workflow_only_delegates_checks_and_preserves_preparation(self) -> None:
        workflow = (SCRIPT_PATH.parents[1] / ".github/workflows/ci.yml").read_text()
        sections = dict(re.findall(r"^  ([a-z0-9-]+):\n(.*?)(?=^  [a-z0-9-]+:|\Z)", workflow.split("jobs:\n", 1)[1], re.M | re.S))
        self.assertEqual(set(checker.JOBS), set(sections))
        for job, section in sections.items():
            self.assertIn(f"python3 scripts/check-all.py {job}", section)
            for command in re.findall(r"^\s+run: (.+)$", section, re.M):
                self.assertTrue(command == ">-" or command.startswith("python3 scripts/check-all.py ")
                                or command == "npx playwright install --with-deps chromium", command)
        e2e = sections["e2e"]
        self.assertLess(e2e.index("--step dependencies"), e2e.index("playwright install"))
        self.assertLess(e2e.index("playwright install"), e2e.index("--step tests"))
        for expression in ("github.event.pull_request.base.sha || github.event.before", "github.sha",
                           "github.event.forced || 'false'", "dependabot[bot]", "--skip-commit-messages"):
            self.assertIn(expression, sections["docs"])


class RunnerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.output = contextlib.ExitStack()
        self.addCleanup(self.output.close)
        self.output.enter_context(contextlib.redirect_stdout(io.StringIO()))
        self.output.enter_context(contextlib.redirect_stderr(io.StringIO()))

    @patch.object(checker, "run_step")
    def test_partial_jobs_and_step_do_not_touch_record(self, run: Mock) -> None:
        with patch.object(checker, "record_path") as record, patch.object(checker, "git") as git:
            checker.run_checks(["frontend"], {})
            self.assertEqual(["dependencies", "audit", "coverage", "lint", "build"],
                             [call.args[1].name for call in run.call_args_list])
            run.reset_mock()
            checker.run_checks(["e2e"], {}, step_name="tests")
            self.assertEqual(("npm", "run", "test:e2e"), run.call_args.args[1].command)
            record.assert_not_called()
            git.assert_not_called()

    @patch.object(checker, "run_step", side_effect=[None, checker.CheckError("frontend/audit")])
    def test_stops_on_first_failure(self, run: Mock) -> None:
        with self.assertRaisesRegex(checker.CheckError, "frontend/audit"):
            checker.run_checks(["frontend", "docker"], {})
        self.assertEqual(["dependencies", "audit"], [call.args[1].name for call in run.call_args_list])

    def test_step_reports_failed_job_and_preserves_environment(self) -> None:
        step = checker.Step("audit", ("npm", "audit"), "frontend")
        env = {"FORCED_PUSH": "true", "PATH": "/tools"}
        with patch.object(checker.subprocess, "run", return_value=Mock(returncode=7)) as run:
            with self.assertRaisesRegex(checker.CheckError, r"frontend/audit.*7"):
                checker.run_step("frontend", step, env)
            self.assertEqual({**env, "CI": "true"}, run.call_args.kwargs["env"])
            self.assertEqual(checker.ROOT / "frontend", run.call_args.kwargs["cwd"])
        with patch.object(checker.subprocess, "run", side_effect=FileNotFoundError("npm")):
            with self.assertRaisesRegex(checker.CheckError, "frontend/audit"):
                checker.run_step("frontend", step, env)

    def test_e2e_uses_only_isolated_fixture_settings_without_loading_dotenv(self) -> None:
        with patch.object(checker.subprocess, "run", return_value=Mock(returncode=0)) as run:
            checker.run_step("e2e", checker.Step("tests", ("npm", "run", "test:e2e"), "frontend"),
                             {"DB_PASSWORD": "external-value", "JWT_SECRET": "external-value", "FORCED_PUSH": "true", "CI": "false"})
            env = run.call_args.kwargs["env"]
            self.assertEqual("root123", env["DB_PASSWORD"])
            self.assertEqual("ci-e2e-jwt-secret-key-for-testing-only", env["JWT_SECRET"])
            self.assertEqual("1", env["COMPOSE_DISABLE_ENV_FILE"])
            self.assertEqual("true", env["CI"])
            self.assertEqual("true", env["FORCED_PUSH"])

    def test_unknown_jobs_duplicate_jobs_and_invalid_step_are_rejected(self) -> None:
        for args in (["unknown"], ["docs", "docs"], ["--step", "tests"], ["docs", "frontend", "--step", "build"]):
            with self.subTest(args=args), self.assertRaises(SystemExit) as error:
                checker.main(args)
            self.assertEqual(2, error.exception.code)
        self.assertEqual(1, checker.main(["frontend", "--step", "missing"]))
        with self.assertRaises(checker.CheckError):
            checker.job_steps("unknown", {})

    @patch.object(checker, "run_checks")
    def test_cli_defaults_and_selected_jobs(self, run: Mock) -> None:
        self.assertEqual(0, checker.main([]))
        self.assertEqual(list(checker.JOBS), run.call_args.args[0])
        self.assertEqual(0, checker.main(["backend-integration", "frontend"]))
        self.assertEqual(["backend-integration", "frontend"], run.call_args.args[0])
        run.side_effect = checker.CheckError("backend/verify")
        self.assertEqual(1, checker.main(["backend"]))
        run.side_effect = KeyboardInterrupt()
        self.assertEqual(130, checker.main(["backend"]))

    def test_full_gate_only_records_unchanged_clean_head_and_invalidates_old_record(self) -> None:
        for clean, heads, succeeds, expected in (
            ([True, True], ["new", "new"], True, "new\n"),
            ([False], ["new"], True, None),
            ([True, False], ["new"], True, None),
            ([True, True], ["new", "changed"], True, None),
            ([True], ["new"], False, None),
        ):
            with self.subTest(clean=clean, heads=heads, succeeds=succeeds), tempfile.TemporaryDirectory() as directory:
                record = Path(directory) / checker.RECORD_NAME
                record.write_text("old\n")
                def check_step(*args: object) -> None:
                    self.assertFalse(record.exists(), "旧记录应在执行检查前失效")
                    if not succeeds:
                        raise checker.CheckError("docs/tools")
                with patch.object(checker, "record_path", return_value=record), \
                     patch.object(checker, "git", side_effect=heads), \
                     patch.object(checker, "clean_worktree", side_effect=clean), \
                     patch.object(checker, "run_step", side_effect=check_step):
                    if succeeds:
                        checker.run_checks(checker.JOBS, {})
                    else:
                        with self.assertRaises(checker.CheckError):
                            checker.run_checks(checker.JOBS, {})
                self.assertEqual(expected, record.read_text() if record.exists() else None)
                self.assertEqual([record] if expected else [], list(Path(directory).iterdir()))

    @patch.object(checker, "run_step")
    @patch.object(checker, "record_path")
    def test_skipped_commit_validation_cannot_record_a_full_pass(self, record: Mock, run: Mock) -> None:
        checker.run_checks(checker.JOBS, {}, skip_commits=True)
        record.assert_not_called()


class GitAndHookTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name) / "repository with spaces"
        self.root.mkdir()
        self.git("init", "-q")
        self.git("-c", "user.name=Test", "-c", "user.email=test@example.invalid",
                 "commit", "--allow-empty", "-qm", "test: 初始化测试仓库")
        self.head = self.git("rev-parse", "HEAD")
        self.record = self.root / ".git" / checker.RECORD_NAME
        self.zero = "0" * 40

    def git(self, *args: str) -> str:
        return subprocess.run(("git", *args), cwd=self.root, check=True, text=True,
                              capture_output=True).stdout.strip()

    def push_line(self, oid: str, ref: str = "refs/heads/main") -> str:
        return f"{ref} {oid} {ref} {self.zero}\n"

    def hook(self, lines: str, *, cwd: Path | None = None, autonomous: str = "0") -> subprocess.CompletedProcess[str]:
        env = {**os.environ, "CODEX_AUTONOMOUS": autonomous}
        return subprocess.run(("sh", str(HOOK)), input=lines, cwd=cwd or self.root,
                              env=env, text=True, capture_output=True)

    def test_missing_stale_and_mixed_refs_reject_but_deletions_and_no_updates_pass(self) -> None:
        self.assertNotEqual(0, self.hook(self.push_line(self.head)).returncode)
        self.record.write_text("stale\n")
        rejection = self.hook(self.push_line(self.head))
        self.assertIn("python3 scripts/check-all.py", rejection.stderr)
        self.assertNotEqual(0, rejection.returncode)
        checker.write_record(self.record, self.head)
        self.assertEqual(0, self.hook(self.push_line(self.head)).returncode)
        self.assertEqual(0, self.hook(self.push_line(self.head, "refs/tags/lightweight")).returncode)
        self.assertNotEqual(0, self.hook(self.push_line(self.head) + self.push_line("a" * 40, "refs/heads/other")).returncode)
        self.record.unlink()
        self.assertEqual(0, self.hook(self.push_line(self.zero)).returncode)
        self.assertEqual(0, self.hook(self.push_line("0" * 64)).returncode)
        self.assertEqual(0, self.hook("").returncode)

    def test_autonomous_block_is_preserved_even_for_deletions(self) -> None:
        checker.write_record(self.record, self.head)
        for lines in (self.push_line(self.head), self.push_line(self.zero), ""):
            result = self.hook(lines, autonomous="1")
            self.assertEqual(1, result.returncode)
            self.assertIn("CODEX_AUTONOMOUS=1", result.stderr)

    def test_worktrees_share_record_and_untracked_or_staged_changes_are_dirty(self) -> None:
        worktree = Path(self.temporary.name) / "linked worktree"
        self.git("worktree", "add", "--detach", "-q", str(worktree), "HEAD")
        with patch.object(checker, "ROOT", worktree):
            self.assertEqual(self.record.resolve(), checker.record_path())
            self.assertTrue(checker.clean_worktree())
            (worktree / "file.txt").write_text("untracked")
            self.assertFalse(checker.clean_worktree())
            subprocess.run(("git", "add", "file.txt"), cwd=worktree, check=True)
            self.assertFalse(checker.clean_worktree())
            checker.write_record(checker.record_path(), self.head)
        self.assertEqual(0, self.hook(self.push_line(self.head), cwd=worktree).returncode)


if __name__ == "__main__":
    unittest.main()
