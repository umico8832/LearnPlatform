# Docker 开发

## 环境要求

- Docker 20.10+
- Docker Compose 2.0+

## 启动

```bash
cp .env.example .env
docker compose up -d
docker compose ps
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

日常开发优先使用本机工具链（[本地开发](local-development.md)），不要为验证普通代码
改动反复重建镜像。只有需要容器化运行，或改了 Dockerfile、`.dockerignore`、Compose 或
Nginx 配置时才重建对应服务：

```bash
docker compose build backend
docker compose up -d backend
```

启动与更新默认使用 `docker compose up -d`（不加 `--build`）。

## 停止

```bash
docker compose down
```

`down` 保留数据卷，适合日常停止。`down -v` 会删除数据库等持久化数据卷，仅在需要
完全重置开发数据时使用并需确认。浏览器 E2E 使用隔离的 `docker-compose.e2e.yml`，
执行方式和安全边界见[测试策略](../development/testing.md)。磁盘占用诊断与安全回收见
[Docker 磁盘增长治理](../development/docker-disk-governance.md)。
