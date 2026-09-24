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

### 全局页面预览

学习端开发服务启动后，打开 [全局页面预览](http://localhost:5173/dev/pages)。页面目录从学习端和管理端
路由生成，支持按名称、路径或路由名查找，包含隐藏入口与占位页面；生产构建不注册此工具。
侧边栏分组采用手风琴展开，每次最多展开一组；搜索时优先展开当前页面所在的匹配分组，否则展开首个匹配分组。
清空搜索后恢复原有展开状态。

- 点击目录中的页面立即预览，可切换 1280、1440、1920 像素桌面宽度、刷新或在新标签打开；保留原认证预览的
  768 × 900 平板与 390 × 844 手机尺寸。
- 课程、考试记录与试卷学习页面需填写当前账号有权访问的真实 ID，按回车加载；输入过程中不发起页面加载。
  也可通过“从业务入口进入”建立所需上下文。
  练习答题页需要先选题，AI 教学页进入时可能创建会话，考试页会计时；预览中的业务操作会实际生效。
- 管理端默认连接 `http://localhost:5174/admin/`，需单独启动管理端并登录；修改地址后按回车应用。
- 认证页面在同一工具中通过“预览状态”切换登录、注册步骤、邮件发送、链接验证与结果等 14 种状态，
  也可选择“真实流程”调试实际行为。新标签链接跟随所选状态；独立认证预览页已移除。
- 业务页面保持真实登录、权限与数据要求；认证状态仅在开发环境启用，不授予业务权限。

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
