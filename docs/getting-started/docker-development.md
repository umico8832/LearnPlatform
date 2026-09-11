# Docker 开发

## 环境要求

- Docker 20.10+
- Docker Compose 2.0+

## 启动

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

日常编码和单元测试优先使用本机工具链（[本地开发](local-development.md)），不要求每次保存
源码都重建镜像。准备通过 Docker 做浏览器检查、联调或演示时必须使用 `app-up`，不能把
`docker compose up -d` 复用的旧镜像当作当前工作区验证。单纯重启已经核对过的同一镜像时
才直接使用 `docker compose up -d`。

## 停止

```bash
docker compose down
```

`down` 保留数据卷，适合日常停止。`down -v` 会删除数据库等持久化数据卷，仅在需要
完全重置开发数据时使用并需确认。浏览器 E2E 使用隔离的 `docker-compose.e2e.yml`，
执行方式和安全边界见[测试策略](../development/testing.md)。磁盘占用诊断与安全回收见
[Docker 磁盘增长治理](../development/docker-disk-governance.md)。
