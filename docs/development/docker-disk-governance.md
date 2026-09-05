# Docker 磁盘增长治理

本文档定义 Docker 磁盘占用的定位顺序、磁盘预算和安全回收边界。
目标是让开发流程不长期积累几十 GB 的无价值镜像与构建缓存，同时保证持久化数据和
其他项目资源不被误删。

## 1. 范围与原则

只治理 Docker 磁盘增长和由它引发的开发流程问题，不扩展产品功能，也不为了“绝对干净”
引入守护进程、定时任务或额外服务。

构建习惯见[Docker 开发](../getting-started/docker-development.md)，验证范围见[测试策略](testing.md)。
磁盘回收只处理可再生成的缓存与悬空镜像；环境停止和 E2E 清理按第 4 节单独核对范围。

## 2. 占用定位顺序

Docker 占用异常增长时，先定位来源，再决定处理方式。不要无脑执行全局 prune。

| 顺序 | 来源 | 查看方式 |
|---|---|---|
| 1 | images | `docker system df` 的 Images 行；悬空镜像用 `docker images --filter dangling=true` |
| 2 | build cache | `docker system df` 的 Build Cache 行；明细用 `docker builder du` |
| 3 | containers | `docker ps -a --size` 与 `docker system df` 的 Containers 行 |
| 4 | volumes | `docker system df -v` 的 Local Volumes 段；`docker volume ls` |

一键诊断：

```bash
python3 scripts/docker-disk.py report
```

该命令按 images → build cache → containers → volumes 输出占用，并区分本项目与其他
项目的卷和容器，提示哪些资源不得自动清理。

## 3. 磁盘预算与回收策略

### Build Cache

- 预算：默认保留 4GB（`scripts/docker-disk.py` 的 `DEFAULT_KEEP_STORAGE`），可临时
  用 `--keep-storage` 或环境变量 `DOCKER_BUILD_CACHE_KEEP` 调整。
- 回收方式：`docker builder prune --max-used-space 4g -f`（脚本会自动适配旧版
  `--keep-storage` 参数）。
- 保留 4GB 的意图：前端 `npm ci` 与后端 Maven 依赖的最近构建层可命中缓存，避免每次
  开发都重新下载依赖；超出预算的部分视为可安全丢弃的陈旧缓存。

### 悬空镜像（dangling）

- 悬空镜像指重建后遗留的无 tag 旧镜像，无容器引用、无持久价值，可安全回收。
- 回收方式：`docker image prune --filter dangling=true -f`。

### 带 tag 镜像

- 不设自动预算、不自动回收。删除带 tag 的未使用镜像（`docker image prune -a`）需要
  用户明确授权，因为它可能包含其他项目或有意保留的镜像。

## 4. 安全回收 vs 需授权清理

| 操作 | 分类 | 说明 |
|---|---|---|
| `scripts/docker-disk.py reclaim` | 允许自动执行 | 仅回收构建缓存超预算部分和悬空镜像 |
| `docker compose down`（本项目） | 允许 | 停止本项目容器并移除其网络，保留数据卷 |
| `docker compose ... down -v --rmi local`（E2E 项目） | 允许 | 清理 E2E 自己的容器、网络、卷与构建镜像 |
| `docker compose down -v`（开发项目） | 需确认 | 会删除开发数据库等数据卷，仅用于明确的全量重置 |
| `docker image prune -a` / `docker container prune` / `docker volume prune` | 需授权 | 全局且可能波及持久化数据或其他项目 |
| `docker system prune`（尤其是 `--volumes`） | 需授权 | 扩大范围的破坏性全局清理 |

缓存回收不授权删除数据卷、容器、带 tag 镜像或网络；表中项目停止和 E2E 清理仅适用于明确的项目范围。
跨项目资源清理必须获得用户明确授权。

E2E 的启动、重建与结束清理由[测试策略](testing.md#8-浏览器-e2e-环境)统一维护。
`down` 不清理构建缓存；仅在超过预算时使用 `scripts/docker-disk.py reclaim`，不要求每次测试后执行。

## 7. 持久化数据安全

- 开发环境持久化数据保存在 `learnplatform_*` 数据卷中；E2E 数据保存在
  `learnplatform-e2e_*` 数据卷中。两者互不影响。
- 不得为了清理磁盘误删数据库或其他持久化 volume；不得默认执行扩大范围的破坏性 prune。
- 其他项目（不以 `learnplatform` 前缀开头）的卷、容器、镜像和构建缓存属于共享环境，
  需要全局 Docker 清理时必须由用户明确授权。
- 日志与监控保留策略只按实际占用诊断调整，不根据历史结论推断当前磁盘来源。

## 8. 相关文档

- 测试分层与 E2E 执行：[测试策略](testing.md)
- 开发循环与验证边界：[AI Agent 开发工作流](workflow.md)
- 启动与停止：[Docker 开发](../getting-started/docker-development.md)
