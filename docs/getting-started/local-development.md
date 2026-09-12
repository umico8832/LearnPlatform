# 本地开发

## 环境要求

- JDK 21
- Maven 3.8+（本机已有 `backend/mvnw` 时也可使用 Wrapper）
- Node.js 22.12+（推荐 Node 22 LTS）、Python 3.10+
- 已启动的 Docker Desktop / Docker Compose

日常开发由 Docker 运行 MySQL、Redis、Mailpit，本机运行前后端。无需启动本机 MySQL。
所有下列 `python3 scripts/...` 命令都在项目根目录执行；后端与前端各使用一个终端。

## 1. 配置环境变量

```bash
test -f .env || cp .env.example .env
```

按[配置说明](configuration.md)在本机填写 `.env`。新入口交给 Compose 解析配置，不需要先
`source` 环境文件；如果旧终端已导出同名变量，它们会优先于 `.env`，请使用新终端。
不得读取、输出或提交真实密钥。

修改 `.env` 后，按实际影响应用配置：

| 修改内容 | 本地开发操作 |
|---|---|
| 前端 `VITE_*` 配置 | 停止并重新运行对应的 `dev.py frontend` 或 `dev.py admin` |
| 后端 JWT、Turnstile、AI 等运行参数 | 停止并重新运行 `dev.py backend` |
| MySQL、Redis、Mailpit 的映射端口或容器配置 | 先停止本地后端，执行 `dev.py infra-up`，再启动后端 |

开发入口固定覆盖本地服务地址、Profile 和 Mailpit 发信设置，具体覆盖项见[配置说明](configuration.md#日常开发覆盖)。
已有数据库账号密码需要在数据库内同步修改，不能仅修改 `.env`。以上操作不需要运行 `app-up`。

## 2. 启动基础服务

```bash
python3 scripts/dev.py infra-up
```

只启动 MySQL、Redis、Mailpit，并等待就绪，不构建应用镜像。复用现有 `learnplatform` 项目
和数据卷，MySQL 使用 `127.0.0.1:13306`，Redis 默认 `127.0.0.1:6379`，SMTP 使用
`127.0.0.1:11025`；邮件在 [Mailpit](http://localhost:8025) 查看，不会送达真实邮箱。
端口可通过 `DEV_MYSQL_PORT`、`REDIS_HOST_PORT`、`DEV_SMTP_PORT`、`MAILPIT_HOST_PORT` 配置。

如果完整 Docker 后端仍在运行，先执行 `docker compose stop frontend backend`。
开发入口不会自动结束占用端口的进程。首次启动后端时，Flyway 自动应用现有迁移；本地运行
与完整 Docker 联调共享数据库，开发数据变化和迁移会保留，自动化测试继续使用隔离环境。

## 3. 启动后端

```bash
python3 scripts/dev.py backend
```

后端默认地址：

- API：`http://localhost:8080`
- Knife4j：`http://localhost:8080/doc.html`

入口先构建并安装 `ai-core` 及父 POM，再运行 `app` 的 Spring Boot 插件；使用本机 Maven，未安装时尝试已有的 Maven Wrapper。日志保留在当前终端，使用
`Ctrl+C` 停止；修改 Java 代码后再次执行同一命令。默认没有后端自动热重载。

## 4. 启动前端

```bash
npm --prefix frontend ci
python3 scripts/dev.py frontend
```

学习端默认地址：`http://localhost:5173`。需要独立调试已迁移的管理端页面时，另开终端执行：

```bash
python3 scripts/dev.py admin
```

管理端入口：`http://localhost:5174/admin/`。Docker 环境由同一 Nginx 在 `/admin/` 提供该构建。

`npm ci` 仅首次安装或依赖锁文件改变后需要执行。修改 Vue/CSS 后保存，浏览器自动热更新。
两个开发前端的 `/api` 都代理到本地 `8080`。入口只传递允许公开的三个 `VITE_*` 配置，
不会将后端密钥交给前端进程；Turnstile 未配置时会提示，登录仍要求真实验证。

## 5. 基础验证

开发中优先运行本次改动直接涉及的测试，页面验收使用当前本地前后端；下列是按影响范围选择的
模块级命令，不要求每次保存文件全部执行。缺少本机 Maven 时，在 `backend` 目录用 `./mvnw` 替代 `mvn`。

```bash
cd backend
mvn test

cd ../frontend
npm test
npm run build
```

完整质量门禁和 E2E 方式见[测试策略](../development/testing.md)，启动异常见[常见问题排查](troubleshooting.md)。

## 6. 停止与 Docker 联调

前后端分别在各自终端按 `Ctrl+C`，基础服务可以继续运行。需要关闭基础服务时：

```bash
python3 scripts/dev.py infra-stop
```

该命令只停止三个基础服务，保留容器和数据卷。查看基础服务（不含本地进程）状态：

```bash
python3 scripts/dev.py status
```

需要完整 Docker 联调时，先停止本地前后端，再按 [Docker 开发](docker-development.md)运行
`app-up`。它在构建前检查前后端的宿主端口；配置中的端口已被其他进程占用时会直接提示。
Docker 联调使用基础 Compose，本地地址覆盖只由 `dev.py` 加载，不需要来回修改 `.env`。
