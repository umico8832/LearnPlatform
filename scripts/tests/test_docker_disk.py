from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path

SCRIPT_PATH = Path(__file__).resolve().parents[1] / "docker-disk.py"
SPEC = importlib.util.spec_from_file_location("docker_disk", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("无法加载 Docker 磁盘治理脚本")
disk = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = disk
SPEC.loader.exec_module(disk)


class BuilderPruneFlagTest(unittest.TestCase):
    def test_buildx_uses_max_used_space(self) -> None:
        help_text = "Usage: docker buildx prune\n  --max-used-space bytes"
        self.assertEqual("--max-used-space", disk.builder_prune_flag(help_text))

    def test_legacy_uses_keep_storage(self) -> None:
        self.assertEqual("--keep-storage", disk.builder_prune_flag("--keep-storage 10g"))

    def test_unknown_defaults_to_buildx(self) -> None:
        self.assertEqual("--max-used-space", disk.builder_prune_flag(""))


class VolumeOwnershipTest(unittest.TestCase):
    def test_project_volumes(self) -> None:
        self.assertEqual("project", disk.classify_volume("learnplatform_mysql-data"))
        self.assertEqual("project", disk.classify_volume("learnplatform-e2e_mysql-data"))
        self.assertTrue(disk.is_project_managed("learnplatform-e2e_mysql-data"))

    def test_foreign_volumes(self) -> None:
        self.assertEqual("foreign", disk.classify_volume("lifepilot_mysql-data"))
        self.assertEqual("foreign", disk.classify_volume("docker_postgres_data"))
        self.assertEqual("foreign", disk.classify_volume("vscode"))
        self.assertFalse(disk.is_project_managed("gapi-postgres"))


if __name__ == "__main__":
    unittest.main()
