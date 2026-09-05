from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "check-code-structure.py"
SPEC = importlib.util.spec_from_file_location("check_code_structure", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("无法加载代码结构检查脚本")
checker = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = checker
SPEC.loader.exec_module(checker)


class CodeStructureValidationTest(unittest.TestCase):
    def test_accepts_service_at_limits(self) -> None:
        fields = "\n".join(f"    private final Dependency{i} dependency{i};" for i in range(8))
        content = f"@Service\nclass ExampleService {{\n{fields}\n}}"
        content += "\n" * (checker.SERVICE_MAX_LINES - checker.count_lines(content))

        self.assertEqual([], checker.validate_service(Path("ExampleService.java"), content))

    def test_rejects_oversized_service_and_dependency_fan_in(self) -> None:
        fields = "\n".join(f"    private final Dependency{i} dependency{i};" for i in range(9))
        content = f"@Service\nclass ExampleService {{\n{fields}\n}}"
        content += "\n// filler" * checker.SERVICE_MAX_LINES

        errors = checker.validate_service(Path("ExampleService.java"), content)

        self.assertEqual(2, len(errors))
        self.assertTrue(any("lines" in error for error in errors))
        self.assertTrue(any("direct dependencies" in error for error in errors))

    def test_ignores_static_constants_and_non_service_classes(self) -> None:
        service = "@Service\nclass ExampleService {\n    private static final int LIMIT = 1;\n}"
        helper = "class Helper {\n" + "\n".join("    private final Object value;" for _ in range(12)) + "\n}"

        self.assertEqual([], checker.validate_service(Path("ExampleService.java"), service))
        self.assertEqual([], checker.validate_service(Path("Helper.java"), helper))

    def test_rejects_vue_file_above_limit(self) -> None:
        accepted = "\n".join("<div />" for _ in range(checker.VUE_MAX_LINES))
        rejected = accepted + "\n<div />"

        self.assertEqual([], checker.validate_vue(Path("Accepted.vue"), accepted))
        self.assertEqual(1, len(checker.validate_vue(Path("Rejected.vue"), rejected)))

    def test_rejects_dense_vue_script_below_total_file_limit(self) -> None:
        script = "\n".join("const value = 1" for _ in range(checker.VUE_SCRIPT_MAX_LINES + 1))
        content = f"<template><div /></template>\n<script setup>\n{script}\n</script>"

        errors = checker.validate_vue(Path("DenseScript.vue"), content)

        self.assertEqual(1, len(errors))
        self.assertIn("script block", errors[0])


if __name__ == "__main__":
    unittest.main()
