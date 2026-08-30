from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "check-repository-layout.py"
SPEC = importlib.util.spec_from_file_location("check_repository_layout", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("无法加载仓库结构检查脚本")
checker = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = checker
SPEC.loader.exec_module(checker)


class RepositoryLayoutValidationTest(unittest.TestCase):
    def test_accepts_normal_project_paths(self) -> None:
        self.assertEqual(
            [],
            checker.validate_paths(
                [
                    "backend/src/main/java/com/learnplatform/service/CourseService.java",
                    "backend/src/main/resources/db/migration/V91__snapshot.sql",
                    "frontend/src/views/course/CourseOverviewView.vue",
                ]
            ),
        )

    def test_rejects_cross_platform_invalid_characters(self) -> None:
        errors = checker.validate_paths(
            ["backend/src/test/java/com/learnplatform/controller/CourseControllerTest.java</path"]
        )
        self.assertEqual(1, len(errors))
        self.assertIn("forbidden character", errors[0])

    def test_rejects_generated_directories_and_system_files(self) -> None:
        errors = checker.validate_paths(
            [
                "backend/target/classes/Application.class",
                "frontend/node_modules/vue/index.js",
                "scripts/.DS_Store",
            ]
        )
        self.assertEqual(3, len(errors))
        self.assertTrue(any("generated directory 'target'" in error for error in errors))
        self.assertTrue(any("generated directory 'node_modules'" in error for error in errors))
        self.assertTrue(any("system file" in error for error in errors))

    def test_rejects_components_ending_with_space_or_dot(self) -> None:
        errors = checker.validate_paths(["docs/report. /index.md", "docs/draft./notes.md"])
        self.assertEqual(2, len(errors))
        self.assertTrue(all("must not end with space or dot" in error for error in errors))

    def test_rejects_admin_pages_in_learner_view_directory(self) -> None:
        errors = checker.validate_paths(["frontend/src/views/admin/AdminDashboard.vue"])
        self.assertEqual(1, len(errors))
        self.assertIn("frontend/src/admin/views/", errors[0])


if __name__ == "__main__":
    unittest.main()
