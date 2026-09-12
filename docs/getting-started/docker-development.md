# Docker 开发

## 环境要求

- Docker 20.10+
- Docker Compose 2.0+

## 完整 Docker 更新与验收

日常修改代码使用[本地开发](local-development.md)入口。完整 Docker 联调前，先在本地
前后端终端按 `Ctrl+C`；`app-up` 会在构建前拒绝非本项目容器占用的应用端口。

```bash
test -f .env || cp .env.example .env
python3 scripts/docker-lifecycle.py app-up
```

`app-up` 始终让 BuildKit 检查当前前后端构建上下文；没有源码变化时复用缓存，有变化时构建
新镜像。脚本保留旧应用镜像到新容器通过健康与 HTTP 检查，成功后安全回收悬空旧镜像及
超过 4GB 预算的构建缓存。检查运行容器与当前源码是否一致：

```bash
python3 scripts/docker-lifecycle.py app-status
```

默认访问地址：

- 前端：`http://localhost`
- 后端：`http://localhost:8080`
- Knife4j：`http://localhost:8080/doc.html`

如 80 或 8080 端口被占用，在 `.env` 中配置：

```env
FRONTEND_HOST_PORT=18000
BACKEND_HOST_PORT=18080
```

本地前后端连接 Docker 数据库仍属于[本地开发](local-development.md)，页面验收复用对应
开发服务，环境选择遵循[工作流](../development/workflow.md#运行环境与服务复用)。
只有需要把当前源码应用到完整 Docker 环境并验证时才使用 `app-up`。已经验证过且源码、配置
均未改变的 Docker 环境可以直接恢复运行；不能把旧镜像的检查结果当作新源码的验证证据。
`app-status` 核对源码与镜像身份，不核对 `.env` 是否已经应用。

## 日常开关与移除

临时停用完整 Docker 环境：

```bash
docker compose stop
```

恢复已有容器（代码和配置没有改变）：

```bash
docker compose start
```

`stop/start` 保留原容器，不重新构建镜像，也不读取新的容器环境配置。后台启动后关闭终端或
按 `Ctrl+C` 不会停止容器；`docker compose logs -f` 中的 `Ctrl+C` 只结束日志查看。
混合开发模式的本地进程与基础服务启停见[本地开发](local-development.md#6-停止与-docker-联调)。

需要移除本项目容器与网络时：

```bash
docker compose down
```

`down` 保留数据卷和镜像，但下次需要重新创建容器，不能直接 `start`。
配置和镜像均未变化时可用 `docker compose up -d` 重新创建；需要更新源码时使用 `app-up`。
`down -v` 会删除数据库等持久化数据卷，仅在需要
完全重置开发数据时使用并需确认。浏览器 E2E 使用隔离的 `docker-compose.e2e.yml`，
执行方式和安全边界见[测试策略](../development/testing.md)。磁盘占用诊断与安全回收见
[Docker 磁盘增长治理](../development/docker-disk-governance.md)。
