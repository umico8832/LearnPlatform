# 配置说明

项目通过根目录 `.env` 向本地脚本和 Docker Compose 提供配置。仓库只保存 `.env.example`；真实密码、Token 和 API Key 不得提交。

## 初始化

```bash
cp .env.example .env
```

至少修改：

- `DB_PASSWORD`
- `JWT_SECRET`
- `GRAFANA_ADMIN_PASSWORD`（启用监控时）

只有启用 AI 时才填写 `AI_API_KEY`。

## 数据库与应用

| 变量 | 用途 | 示例默认值 |
|---|---|---|
| `DB_URL` | 本地 Spring 数据库连接 | `jdbc:mysql://localhost:3306/learn_platform...` |
| `DB_USERNAME` | 数据库用户 | `root` |
| `DB_PASSWORD` | 数据库密码 | 必须修改 |
| `JWT_SECRET` | JWT 签名密钥 | 至少 256 bit |
| `JWT_EXPIRATION` | Token 有效期，秒 | `604800` |
| `SERVER_PORT` | 容器内后端端口 | `8080` |
| `BACKEND_HOST_PORT` | 后端宿主端口 | `8080` |
| `FRONTEND_HOST_PORT` | 前端宿主端口 | `80` |
| `SPRING_PROFILES_ACTIVE` | Spring Profile | 本地 `dev`，Compose 默认覆盖为 `docker` |

## Redis

`REDIS_HOST`、`REDIS_PORT` 和 `REDIS_PASSWORD` 用于后端连接。Compose 将 Redis 宿主端口限制到 `127.0.0.1`，容器间仍通过内部网络访问。

## AI

| 变量 | 用途 |
|---|---|
| `AI_ENABLED` | 是否启用 AI |
| `AI_API_BASE_URL` | OpenAI 兼容 API 地址 |
| `AI_API_KEY` | 上游 Key，只保存在本机 |
| `AI_MODEL` | 上游模型名称 |
| `AI_TIMEOUT` | 后端请求超时，毫秒 |
| `AI_MAX_TOKENS` | 最大输出 Token |
| `AI_DAILY_QUOTA` | 默认用户日配额，`0` 表示不限 |
| `AI_ALERT_WEBHOOK_ENABLED` | 是否启用提醒 webhook |
| `AI_ALERT_WEBHOOK_URL` | 提醒地址 |
| `AI_ALERT_WEBHOOK_TIMEOUT` | webhook 超时 |

模型价格由后端配置决定。未配置价格或上游未返回 usage 时，成本保持未知。

## 前端

- `VITE_API_BASE_URL`：通常为 `/api`，本地由 Vite 代理，容器由 Nginx 代理。
- `VITE_AI_TIMEOUT`：同步 AI 请求超时；SSE 流式接口不使用 Axios 超时。

Vite 变量在构建时固化，修改后需要重新启动开发服务或重新构建镜像。

## 监控

Prometheus、Grafana 和 Loki 的宿主端口分别由 `PROMETHEUS_HOST_PORT`、`GRAFANA_HOST_PORT` 和 `LOKI_HOST_PORT` 控制。

## 安全规则

- 不读取、展示或提交真实 `.env`。
- 不把真实 Key 写入测试、截图、日志或 Markdown。
- `.env.example` 只使用不可用的示例值。
- 公开部署前更换全部默认密码和演示账号。
