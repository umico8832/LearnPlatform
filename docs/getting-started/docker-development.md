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

修改后端代码后如本机存在旧镜像，应重建后端：

```bash
docker compose build backend
docker compose up -d backend
```

## 停止

```bash
docker compose down
```

变量含义见[配置说明](configuration.md)，启动异常见[常见问题排查](troubleshooting.md)。浏览器 E2E 使用隔离的 `docker-compose.e2e.yml`，执行方式和安全边界见[测试策略](../development/testing.md)。
