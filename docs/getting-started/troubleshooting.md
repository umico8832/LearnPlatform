# 常见问题排查

## 后端无法连接 MySQL

1. 确认 MySQL 8 正在运行。
2. 检查数据库名为 `learn_platform`。
3. 核对 `DB_URL`、`DB_USERNAME` 和 `DB_PASSWORD`。
4. 检查 URL 是否包含时区和 `allowPublicKeyRetrieval=true`。
5. 查看后端启动日志中的 Flyway 版本，不要手工跳过迁移。

## Docker 服务无法变为健康

```bash
docker compose ps
docker compose logs backend
docker compose logs mysql
```

优先解决最先失败的依赖。修改环境变量或镜像内容后使用：

```bash
docker compose up -d --build --force-recreate
```

不要通过删除数据卷绕过迁移问题，除非明确接受丢失本地数据。

## 前端请求出现重复 `/api/api`

前端 API 模块应传相对于统一请求实例的路径，例如 `/review/stats`；基础 `/api` 由请求实例、Vite 或 Nginx 统一添加。不要在同一链路重复拼接。

## 登录后仍返回 401

- 检查请求头是否为 `Authorization: Bearer <token>`。
- 确认 Token 未过期且由当前 `JWT_SECRET` 签发。
- 清理旧站点 localStorage 后重新登录。
- Docker 重建后如果 JWT Secret 变化，旧 Token 必然失效。

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

检查器会报告坏链接、旧路径、标题层级、无效 JSON、未记录 Controller 端点、未记录数据库表和 Skill 配置问题。历史 changelog 中的旧文件名是明确例外。
