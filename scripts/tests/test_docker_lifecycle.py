from __future__ import annotations

import importlib.util
import sys
import unittest
from unittest.mock import Mock, patch

from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "docker-lifecycle.py"
SPEC = importlib.util.spec_from_file_location("docker_lifecycle", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("无法加载 Docker 生命周期脚本")
lifecycle = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = lifecycle
SPEC.loader.exec_module(lifecycle)


class ImageIdentityTest(unittest.TestCase):
    def test_requires_revision_state_and_source_fingerprint(self) -> None:
        metadata = lifecycle.SourceMetadata("abc123", "dirty", "fingerprint")
        matching = lifecycle.ImageInfo(
            "sha256:image",
            {
                "org.opencontainers.image.revision": "abc123",
                "com.learnplatform.worktree-state": "dirty",
                "com.learnplatform.source-fingerprint": "fingerprint",
            },
        )
        stale = lifecycle.ImageInfo(
            "sha256:image",
            {
                "org.opencontainers.image.revision": "abc123",
                "com.learnplatform.worktree-state": "dirty",
                "com.learnplatform.source-fingerprint": "old",
            },
        )

        self.assertTrue(lifecycle.image_matches_source(matching, metadata))
        self.assertFalse(lifecycle.image_matches_source(stale, metadata))
        self.assertFalse(lifecycle.image_matches_source(None, metadata))


class AppLifecycleTest(unittest.TestCase):
    def metadata(self) -> dict[str, lifecycle.SourceMetadata]:
        return {
            "backend": lifecycle.SourceMetadata("rev", "clean", "backend-hash"),
            "frontend": lifecycle.SourceMetadata("rev", "clean", "frontend-hash"),
        }

    @patch.object(lifecycle, "reclaim")
    @patch.object(lifecycle, "remove_rollback_tags")
    @patch.object(lifecycle, "image_matches_source", return_value=True)
    @patch.object(lifecycle, "inspect_image", return_value=Mock())
    @patch.object(lifecycle, "verify_app")
    @patch.object(lifecycle, "start_app_stack")
    @patch.object(lifecycle, "pin_rollback_images", return_value={"backend": "rollback"})
    @patch.object(lifecycle, "image_id", side_effect=["new-backend", "new-frontend"])
    @patch.object(lifecycle, "run_command")
    @patch.object(
        lifecycle,
        "inspect_container",
        side_effect=[
            lifecycle.ContainerInfo("old-backend", "healthy"),
            lifecycle.ContainerInfo("old-frontend", "healthy"),
        ],
    )
    @patch.object(lifecycle, "lifecycle_environment")
    @patch.object(lifecycle, "require_docker")
    def test_success_reclaims_only_after_verification(
        self,
        require_docker: Mock,
        lifecycle_environment: Mock,
        inspect_container: Mock,
        run_command: Mock,
        image_id: Mock,
        pin_rollback_images: Mock,
        start_app_stack: Mock,
        verify_app: Mock,
        inspect_image: Mock,
        image_matches_source: Mock,
        remove_rollback_tags: Mock,
        reclaim: Mock,
    ) -> None:
        metadata = self.metadata()
        lifecycle_environment.return_value = ({}, metadata)

        self.assertEqual(0, lifecycle.app_up())

        start_app_stack.assert_called_once()
        verify_app.assert_called_once_with({}, {"backend": "new-backend", "frontend": "new-frontend"})
        remove_rollback_tags.assert_called_once_with({"backend": "rollback"})
        reclaim.assert_called_once_with()

    @patch.object(lifecycle, "reclaim")
    @patch.object(lifecycle, "remove_rollback_tags")
    @patch.object(lifecycle, "rollback_app")
    @patch.object(lifecycle, "verify_app", side_effect=lifecycle.LifecycleError("unhealthy"))
    @patch.object(lifecycle, "start_app_stack")
    @patch.object(lifecycle, "pin_rollback_images", return_value={"backend": "rollback"})
    @patch.object(lifecycle, "image_id", side_effect=["new-backend", "new-frontend"])
    @patch.object(lifecycle, "run_command")
    @patch.object(
        lifecycle,
        "inspect_container",
        side_effect=[
            lifecycle.ContainerInfo("old-backend", "healthy"),
            lifecycle.ContainerInfo("old-frontend", "healthy"),
        ],
    )
    @patch.object(lifecycle, "lifecycle_environment")
    @patch.object(lifecycle, "require_docker")
    def test_failed_verification_rolls_back_without_reclaiming(
        self,
        require_docker: Mock,
        lifecycle_environment: Mock,
        inspect_container: Mock,
        run_command: Mock,
        image_id: Mock,
        pin_rollback_images: Mock,
        start_app_stack: Mock,
        verify_app: Mock,
        rollback_app: Mock,
        remove_rollback_tags: Mock,
        reclaim: Mock,
    ) -> None:
        lifecycle_environment.return_value = ({}, self.metadata())

        with self.assertRaisesRegex(lifecycle.LifecycleError, "unhealthy"):
            lifecycle.app_up()

        rollback_app.assert_called_once()
        remove_rollback_tags.assert_not_called()
        reclaim.assert_not_called()

    @patch.object(lifecycle, "compose_container_id", return_value="container-id")
    @patch.object(lifecycle, "run_command")
    def test_unrecoverable_historical_image_does_not_create_false_rollback_reference(
        self, run_command: Mock, compose_container_id: Mock
    ) -> None:
        run_command.return_value.returncode = 1

        tags = lifecycle.pin_rollback_images({"backend": "missing-image"})

        self.assertEqual({}, tags)
        self.assertEqual(2, run_command.call_count)


class E2ELifecycleTest(unittest.TestCase):
    def patches(self):
        return (
            patch.object(lifecycle, "require_docker"),
            patch.object(lifecycle, "lifecycle_environment", return_value=({}, {})),
            patch.object(lifecycle, "available_port", return_value=18000),
            patch.object(lifecycle, "start_e2e"),
            patch.object(lifecycle, "run_playwright", return_value=0),
            patch.object(lifecycle, "show_e2e_failure_logs"),
            patch.object(lifecycle, "stop_e2e", return_value=0),
            patch.object(lifecycle, "reclaim"),
        )

    def test_success_always_cleans_the_isolated_environment(self) -> None:
        patchers = self.patches()
        mocks = [patcher.start() for patcher in patchers]
        self.addCleanup(lambda: [patcher.stop() for patcher in reversed(patchers)])

        self.assertEqual(0, lifecycle.e2e(["--grep", "登录"]))

        start_e2e = mocks[3]
        run_playwright = mocks[4]
        stop_e2e = mocks[6]
        reclaim = mocks[7]
        start_e2e.assert_called_once()
        run_playwright.assert_called_once()
        self.assertEqual(["--grep", "登录"], run_playwright.call_args.args[1])
        stop_e2e.assert_called_once()
        reclaim.assert_called_once_with()

    def test_start_failure_still_cleans_the_isolated_environment(self) -> None:
        patchers = self.patches()
        mocks = [patcher.start() for patcher in patchers]
        self.addCleanup(lambda: [patcher.stop() for patcher in reversed(patchers)])
        mocks[3].side_effect = lifecycle.LifecycleError("start failed")

        with self.assertRaisesRegex(lifecycle.LifecycleError, "start failed"):
            lifecycle.e2e([])

        mocks[6].assert_called_once()
        mocks[7].assert_called_once_with()

    @patch.object(lifecycle, "run_command")
    def test_cleanup_targets_only_e2e_compose_and_exact_application_images(
        self, run_command: Mock
    ) -> None:
        run_command.return_value.returncode = 0

        self.assertEqual(0, lifecycle.stop_e2e({}))

        commands = [mock_call.args[0] for mock_call in run_command.call_args_list]
        self.assertEqual(
            lifecycle.compose_command("down", "-v", e2e=True),
            commands[0],
        )
        self.assertEqual(
            [
                ("docker", "image", "rm", "learnplatform-e2e-backend:latest"),
                ("docker", "image", "rm", "learnplatform-e2e-frontend:latest"),
            ],
            commands[1:],
        )


class LifecycleCliTest(unittest.TestCase):
    def test_e2e_forwards_documented_grep_arguments(self) -> None:
        with patch.object(sys, "argv", [str(SCRIPT_PATH), "e2e", "--grep", "用户可完成考试"]), \
                patch.object(lifecycle, "e2e", return_value=0) as e2e:
            self.assertEqual(0, lifecycle.main())

        e2e.assert_called_once_with(["--grep", "用户可完成考试"])

    def test_e2e_forwards_arguments_after_separator(self) -> None:
        with patch.object(sys, "argv", [str(SCRIPT_PATH), "e2e", "--", "--grep", "用户可完成考试"]), \
                patch.object(lifecycle, "e2e", return_value=0) as e2e:
            self.assertEqual(0, lifecycle.main())

        e2e.assert_called_once_with(["--grep", "用户可完成考试"])

    def test_e2e_forwards_a_spec_filename(self) -> None:
        with patch.object(sys, "argv", [str(SCRIPT_PATH), "e2e", "e2e/knowledge-review.spec.ts"]), \
                patch.object(lifecycle, "e2e", return_value=0) as e2e:
            self.assertEqual(0, lifecycle.main())

        e2e.assert_called_once_with(["e2e/knowledge-review.spec.ts"])

    def test_e2e_help_does_not_start_the_lifecycle(self) -> None:
        for option in ("-h", "--help"):
            with self.subTest(option=option), patch.object(sys, "argv", [str(SCRIPT_PATH), "e2e", option]), \
                    patch.object(lifecycle, "e2e") as e2e:
                with self.assertRaises(SystemExit) as error:
                    lifecycle.main()

                self.assertEqual(0, error.exception.code)
                e2e.assert_not_called()

    def test_app_commands_still_reject_unknown_arguments(self) -> None:
        for command in ("app-status", "app-up"):
            with self.subTest(command=command), patch.object(
                    sys, "argv", [str(SCRIPT_PATH), command, "--unexpected"]):
                with self.assertRaises(SystemExit) as error:
                    lifecycle.main()

                self.assertEqual(2, error.exception.code)


if __name__ == "__main__":
    unittest.main()
