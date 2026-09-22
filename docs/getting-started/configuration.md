# 配置说明

项目通过根目录 `.env` 向本地脚本和 Docker Compose 提供配置。仓库只保存 `.env.example`；真实密码、Token 和 API Key 不得提交。

日常开发使用 [dev.py 入口](local-development.md)，由 Compose 解析 `.env` 并覆盖本地连接地址。
完整 Docker 环境仍读取基础 Compose；不要在终端长期导出旧配置，以免覆盖 `.env` 的新值。

## 初始化

```bash
test -f .env || cp .env.example .env
```

至少修改：

- `DB_PASSWORD`
- `JWT_SECRET`
- `AUTH_TOKEN_SECRET`（与 JWT 独立的随机密钥）
- `GRAFANA_ADMIN_PASSWORD`（启用监控时）

只有启用 AI 时才填写 `AI_API_KEY`。

登录需要配置 `VITE_TURNSTILE_SITE_KEY`、`TURNSTILE_ENABLED=true` 和 `TURNSTILE_SECRET_KEY`；
前者是公开站点密钥，后者只给后端。开发入口保留真实验证，不自动注入测试密钥。
已有 MySQL 卷的密码必须在数据库内同步修改，单改 `.env` 不会修改已有账号密码。

## 日常开发覆盖

`docker-compose.dev.yml` 仅由 `dev.py` 加载，固定本地后端端口 8080、`dev` Profile、前端地址
`http://localhost:5173` 和 Mailpit 发信配置；数据库与 Redis 连接对应 Docker 映射端口。
数据库密码、JWT、Turnstile 与 AI 参数仍来自根 `.env`。

| 可选变量 | 默认值 | 用途 |
|---|---|---|
| `DEV_MYSQL_PORT` | `13306` | Docker MySQL 映射到本机 |
| `DEV_SMTP_PORT` | `11025` | Docker Mailpit 发信端口 |
| `REDIS_HOST_PORT` | `6379` | 本地后端连接 Redis 的映射端口 |
| `MAILPIT_HOST_PORT` | `8025` | 浏览器查看开发邮件 |

完整 Docker 环境应使用 `SMTP_HOST=mailpit`、`SMTP_PORT=1025`（真实 SMTP 按服务商填写），
`FRONTEND_URL` 设置为实际 Docker 页面地址，`SPRING_PROFILES_ACTIVE=docker`。
本地开发入口会覆盖这些地址，因此无需切换时反复编辑。已有 `.env` 不会被入口重写。

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
| `SPRING_PROFILES_ACTIVE` | Spring Profile | 开发入口固定 `dev`；基础 Compose 未设置时默认 `docker` |
| `PRIVATE_EXAM_SOURCE_STORAGE_LIMIT_BYTES` | 每位用户的私有试卷原文件累计配额 | `104857600`（100MB） |

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
| `AI_TEMPERATURE` | 生成温度，默认 0.7 |
| `AI_TOOLS_SUPPORTED` | 当前云模型是否支持工具调用，默认 false |
| `AI_STRUCTURED_OUTPUT_SUPPORTED` | 当前云模型是否支持原生 JSON Schema，默认 false |
| `AI_STREAM_INCLUDE_USAGE` | 请求流式最终用量，默认 true；不兼容端点可关闭 |
| `AI_EMBEDDING_ENABLED` | 独立启用云 Embedding，默认 false |
| `AI_EMBEDDING_API_BASE_URL`、`AI_EMBEDDING_API_KEY` | 独立配置 Embedding 端点与凭据，不回退到聊天配置 |
| `AI_EMBEDDING_MODEL` | Embedding 模型名称，默认空；启用前必须配置 |
| `AI_EMBEDDING_DIMENSIONS` | 可选向量维度；留空使用模型原生维度，配置时必须为正整数 |
| `AI_EMBEDDING_TIMEOUT_SECONDS` | Embedding 请求超时，默认 30 秒 |
| `AI_DAILY_QUOTA` | 默认用户日配额，`0` 表示不限 |
| `AI_ALERT_WEBHOOK_ENABLED` | 是否启用提醒 webhook |
| `AI_ALERT_WEBHOOK_URL` | 提醒地址 |
| `AI_ALERT_WEBHOOK_TIMEOUT` | webhook 超时 |

模型价格由后端配置决定。未配置价格或上游未返回 usage 时，成本保持未知。
Tutor Agent 追问入口要求 `AI_ENABLED=true` 且 `AI_TOOLS_SUPPORTED=true`；每次问题可能包含多次
受治理的模型调用，因此每次调用分别计入日配额。只启用普通文本生成不会开放 Agent 工具循环。

Embedding 批次同样通过用户日配额与逐调用审计；价格复用 `ai.model-prices` 中对应模型的
`input-per-million`，不要求输出价格。缺少 usage 或输入价格时不估算成本。

## 知识快照

`KNOWLEDGE_SNAPSHOT_PATH` 指向服务端可读的版本目录，例如仓库内 `content/knowledge/cs408/v1`
的绝对路径，默认空。管理员显式调用[导入接口](../reference/api/admin-governance.md#知识快照导入)
才读取并导入，不在启动时自动写入数据库。容器部署需单独挂载该目录；客户端不能传入文件路径。
快照须通过清单哈希、课程、知识点引用与计数校验，导入后仍为待审核，不会自动触发云调用。

| 变量 | 用途 |
|---|---|
| `KNOWLEDGE_VECTOR_ENABLED` | 启用向量服务配置，默认 false |
| `KNOWLEDGE_VECTOR_URL`、`KNOWLEDGE_VECTOR_API_KEY` | Qdrant 端点与可选凭据，默认空 |
| `KNOWLEDGE_VECTOR_COLLECTION` | 专用集合名，默认空；构建时校验 Cosine 与维度 |
| `KNOWLEDGE_VECTOR_TIMEOUT_SECONDS` | 向量请求超时，默认 20 秒，允许 1–60 秒 |
| `KNOWLEDGE_EMBEDDING_BATCH_SIZE` | 每批片段数，默认 16，允许 1–32；索引拒绝超过 6000 字符的片段 |
| `KNOWLEDGE_RETRIEVAL_ENABLED` | 向 Tutor 注册课程知识检索工具，默认 false |
| `KNOWLEDGE_TOP_K` | 单次返回片段上限，默认 4，允许 1–10 |

索引与检索要求显式配置正整数 `AI_EMBEDDING_DIMENSIONS`，不能混用不同模型或端点产生的向量。
字符限制只是批次大小保护，不估算实际 token；云端模型的输入限制仍适用。
启用检索前应完成真实模型评测和知识审核。未审核或尚未就绪的版本返回空资料，不自动发布或构建索引。
普通数据导入、应用启动和工具关闭时不会因此发起 Embedding 调用。

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
