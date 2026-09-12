# 常见问题排查

## 后端无法连接 MySQL

1. 日常开发先执行 `python3 scripts/dev.py status`，确认 Docker MySQL 健康且已映射到
   `127.0.0.1:13306`（或配置的 `DEV_MYSQL_PORT`），无需启动本机 MySQL。
2. 通过 `dev.py backend` 启动本地后端；其数据库地址由开发入口生成，基础 Docker 后端则使用
   `mysql:3306`。映射端口改变后按[本地开发](local-development.md#1-配置环境变量)应用配置。
3. 检查数据库名 `learn_platform` 和账号配置。已有卷的数据库密码不会随 `.env` 自动更新，
   不要通过删除数据卷解决密码不匹配。
4. 查看本地后端终端或对应容器的连接与 Flyway 错误，不输出凭证，不手工跳过迁移。

## 页面改动没有出现

- 先确认实际地址：本地学习端通常为 `5173`，管理端为 `5174/admin/`；完整 Docker 页面通常为 `80`。
- 确认开发服务器属于当前工作目录。Vue/CSS 保存后应热更新，后端代码变化需要重启本地后端。
- `.env` 中前端变量变化需要重启相应 Vite 进程；配置生效步骤见[本地开发](local-development.md#1-配置环境变量)。
- 如果打开的是 Docker 镜像提供的页面，本地热更新不会改变该页面。需要查看日常改动时打开开发地址，
  需要验证更新后的 Docker 版本时按 [Docker 开发](docker-development.md)操作。

## Docker 服务无法变为健康

```bash
docker compose ps
docker compose logs backend
docker compose logs mysql
```

优先解决最先失败的依赖。仅日常开发的基础服务出错时检查 `dev.py status` 和对应容器日志；
本地 Java 错误看后端终端，不运行 `app-up` 代替诊断。需要更新完整 Docker 应用时使用：

```bash
python3 scripts/docker-lifecycle.py app-up
```

该入口会重新构建当前源码，并在替换后核对容器镜像、健康状态和 HTTP 响应。仅查看运行环境
是否仍对应当前工作区时，执行 `python3 scripts/docker-lifecycle.py app-status`。

不要通过删除数据卷绕过迁移问题，除非明确接受丢失本地数据。

## 前端请求出现重复 `/api/api`

前端 API 模块应传相对于统一请求实例的路径，例如 `/review/stats`；基础 `/api` 由请求实例、Vite 或 Nginx 统一添加。不要在同一链路重复拼接。

## 登录后仍返回 401

- 检查请求头是否为 `Authorization: Bearer <token>`。
- 确认 Token 未过期且由当前 `JWT_SECRET` 签发。
- 清理旧站点 localStorage 后重新登录。
- 后端应用新的 JWT Secret 后，旧 Token 失效；这与是否重建 Docker 镜像无关。

## 普通用户访问管理端返回 403

这是预期行为。前端路由守卫只改善体验，`/api/admin/**` 仍由后端要求 `ADMIN`。

## AI 功能不可用

1. 核对 `AI_ENABLED=true`。
2. 确认 `AI_API_KEY` 只在本机配置且上游地址兼容。
3. 检查用户日配额和管理端调用日志。
4. 查看超时、上游错误和模型名称。
5. 核心刷题、考试和复习应继续可用，不要为了 AI 降级修改业务规则。

## SSE 一次性返回

确认请求使用项目规定的流式路径，并检查 Nginx 是否对该路径设置：

```nginx
proxy_buffering off;
proxy_cache off;
```

本地 Vite 与 Docker Nginx 的表现需要分别验证。

## Testcontainers 找不到 Docker

- 确认 Docker daemon 正常运行。
- 检查当前用户能否访问 Docker socket。
- 使用[测试策略](../development/testing.md)中的真实 MySQL命令。
- 不把“0 tests”或跳过集成分组当成通过。

## 文档检查失败

```bash
python3 scripts/check-docs.py
```

检查器会报告坏链接、旧路径、标题层级、无效 JSON、未记录 Controller 端点、未记录数据库表和 Skill 配置问题。
