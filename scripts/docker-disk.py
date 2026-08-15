#!/usr/bin/env python3
"""Docker 磁盘占用诊断与安全回收工具。

本工具只做两类安全回收，绝不触碰持久化数据：

1. BuildKit 构建缓存超出预算的部分（默认保留 4GB，可用 `--keep-storage` 或环境变量
   `DOCKER_BUILD_CACHE_KEEP` 调整）；
2. 悬空镜像（dangling：无 tag 且未被任何容器引用，通常是重建后遗留的旧镜像）。

以下操作始终不做，需要用户明确授权或按正常生命周期处理：

- 删除容器、数据卷或网络；
- 删除带 tag 的镜像（等价于 `docker image prune -a`）；
- 全局 `docker system prune`（尤其是 `--volumes`）；
- 清理其他项目（不以 `learnplatform` 前缀开头的卷或容器）。

诊断必须先按 images → build cache → containers → volumes 定位，再决定处理方式；
详细规则见 `docs/development/docker-disk-governance.md`。

用法：
  python3 scripts/docker-disk.py report
  python3 scripts/docker-disk.py reclaim [--keep-storage 4g] [--dry-run]
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys

DEFAULT_KEEP_STORAGE = "4g"

# 本项目 Compose 管理的资源都以项目名前缀开头（默认项目名 learnplatform，
# E2E 项目名 learnplatform-e2e）；其余前缀视为其他项目或手工资源，只报告不清理。
PROJECT_PREFIX = "learnplatform"


def run(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(["docker", *args], capture_output=True, text=True)


def require_docker() -> None:
    proc = run("version", "--format", "{{.Server.Version}}")
    if proc.returncode != 0:
        print("错误：无法连接 Docker，请先启动 Docker 后重试。", file=sys.stderr)
        if proc.stderr.strip():
            print(proc.stderr.strip(), file=sys.stderr)
        sys.exit(1)


def is_project_managed(name: str) -> bool:
    """判断资源名是否属于本项目 Compose 管理。"""
    return name.startswith(PROJECT_PREFIX)


def classify_volume(name: str) -> str:
    return "project" if is_project_managed(name) else "foreign"


def builder_prune_flag(help_text: str) -> str:
    """根据当前 builder 的帮助文本选择缓存预算参数名。

    BuildKit（Docker Desktop / 现代 Docker 默认）使用 `--max-used-space`；
    旧版经典 builder 使用 `--keep-storage`。
    """
    if "--max-used-space" in help_text:
        return "--max-used-space"
    if "--keep-storage" in help_text:
        return "--keep-storage"
    return "--max-used-space"


def cmd_report() -> int:
    require_docker()

    df = run(
        "system",
        "df",
        "--format",
        "{{.Type}}\t{{.TotalCount}}\t{{.Active}}\t{{.Size}}\t{{.Reclaimable}}",
    )
    rows: dict[str, list[str]] = {}
    if df.returncode == 0:
        for line in df.stdout.splitlines():
            parts = line.split("\t")
            if len(parts) == 5:
                rows[parts[0]] = parts
    else:
        print(df.stderr.strip(), file=sys.stderr)
        return df.returncode

    def cell(kind: str, index: int) -> str:
        row = rows.get(kind)
        return row[index] if row else "-"

    print("== Docker 磁盘占用（按 images → build cache → containers → volumes 定位）==\n")
    print(
        f"images       数量 {cell('Images', 1)}  活动 {cell('Images', 2)}  "
        f"大小 {cell('Images', 3)}  可回收 {cell('Images', 4)}"
    )
    print(
        f"build cache  数量 {cell('Build Cache', 1)}  活动 {cell('Build Cache', 2)}  "
        f"大小 {cell('Build Cache', 3)}  可回收 {cell('Build Cache', 4)}"
    )
    print(
        f"containers   数量 {cell('Containers', 1)}  活动 {cell('Containers', 2)}  "
        f"大小 {cell('Containers', 3)}  可回收 {cell('Containers', 4)}"
    )
    print(
        f"volumes      数量 {cell('Local Volumes', 1)}  活动 {cell('Local Volumes', 2)}  "
        f"大小 {cell('Local Volumes', 3)}  可回收 {cell('Local Volumes', 4)}"
    )

    dangling = run("images", "--filter", "dangling=true", "--format", "{{.ID}}\t{{.Size}}")
    dangling_lines = [line for line in dangling.stdout.splitlines() if line.strip()]
    print(f"\n悬空镜像（可安全回收）: {len(dangling_lines)} 个")
    for line in dangling_lines[:20]:
        print(f"  {line}")
    if len(dangling_lines) > 20:
        print(f"  ... 共 {len(dangling_lines)} 个")

    vols = run("volume", "ls", "--format", "{{.Name}}")
    vol_names = [line for line in vols.stdout.splitlines() if line.strip()]
    project_vols = [n for n in vol_names if is_project_managed(n)]
    foreign_vols = [n for n in vol_names if not is_project_managed(n)]
    print(f"\n数据卷：本项目 {len(project_vols)} 个，其他项目/手工 {len(foreign_vols)} 个")
    if foreign_vols:
        print("  其他项目/手工卷（不得自动清理）: " + ", ".join(foreign_vols))

    cons = run("ps", "-a", "--format", "{{.Names}}")
    con_names = [line for line in cons.stdout.splitlines() if line.strip()]
    project_cons = [n for n in con_names if is_project_managed(n)]
    foreign_cons = [n for n in con_names if not is_project_managed(n)]
    print(f"\n容器：本项目 {len(project_cons)} 个，其他项目 {len(foreign_cons)} 个")
    if foreign_cons:
        print("  其他项目容器（不得自动清理）: " + ", ".join(foreign_cons))

    return 0


def cmd_reclaim(args: argparse.Namespace) -> int:
    require_docker()
    keep = args.keep_storage or os.environ.get("DOCKER_BUILD_CACHE_KEEP", DEFAULT_KEEP_STORAGE)
    dry = args.dry_run

    print(f"构建缓存保留预算：{keep}")
    if dry:
        print("[dry-run] 只打印将执行的命令，不实际清理。\n")

    flag = builder_prune_flag(run("builder", "prune", "--help").stdout)

    build_cmd = ("builder", "prune", flag, keep, "-f")
    print("1) 回收超出预算的 BuildKit 构建缓存：")
    print("   docker " + " ".join(build_cmd))
    if not dry:
        proc = run(*build_cmd)
        print("   " + (proc.stdout.strip() or proc.stderr.strip()))

    image_cmd = ("image", "prune", "--filter", "dangling=true", "-f")
    print("2) 回收悬空镜像：")
    print("   docker " + " ".join(image_cmd))
    if not dry:
        proc = run(*image_cmd)
        print("   " + (proc.stdout.strip() or proc.stderr.strip()))

    print("\n未执行（需要用户授权或按正常生命周期处理）：")
    print("- 容器、数据卷、网络清理")
    print("- 带 tag 的镜像清理（docker image prune -a）")
    print("- 全局 docker system prune（尤其是 --volumes）")
    print("- 其他项目（非 learnplatform 前缀）的资源")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Docker 磁盘诊断与安全回收")
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("report", help="诊断镜像 / 缓存 / 容器 / 卷占用与归属")

    reclaim = sub.add_parser("reclaim", help="安全回收：缓存超预算部分 + 悬空镜像")
    reclaim.add_argument(
        "--keep-storage",
        default=None,
        help=f"构建缓存保留预算，默认 {DEFAULT_KEEP_STORAGE}（也可用 DOCKER_BUILD_CACHE_KEEP）",
    )
    reclaim.add_argument("--dry-run", action="store_true", help="只打印将执行的命令")

    args = parser.parse_args()
    if args.command == "report":
        return cmd_report()
    return cmd_reclaim(args)


if __name__ == "__main__":
    sys.exit(main())
