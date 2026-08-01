# 认证与用户 API

## 公开接口

| 接口 | 说明 |
|---|---|
| `GET /api/public/health` | 服务健康检查 |
| `POST /api/auth/login` | 用户名或邮箱登录，需 Turnstile |
| `POST /api/auth/email/register-code` | 发送注册邮箱验证码，需 Turnstile |
| `POST /api/auth/email/verify-register-code` | 验证邮箱验证码并签发一次性注册票据 |
| `POST /api/auth/register` | 消费注册票据并创建用户 |
| `POST /api/auth/password/forgot` | 发送密码重置邮件，需 Turnstile |
| `GET /api/auth/password/reset/validate` | 验证一次性重置令牌 |
| `POST /api/auth/password/reset` | 消费重置令牌并设置新密码 |

登录请求使用统一账号字段：

```json
{
  "account": "testuser-or-email@example.com",
  "password": "password",
  "turnstileToken": "cloudflare-token"
}
```

账号不存在和密码错误使用相同错误语义，避免通过登录接口枚举账号。Turnstile Token 必须由后端向 Cloudflare 二次校验，前端成功回调不能代替服务端验证。

注册采用三阶段契约：

1. 发送邮箱验证码；
2. 验证验证码并获得五分钟有效的一次性 `verificationTicket`；
3. 注册请求提交用户名、邮箱、密码、可选昵称和该 Ticket。

服务端在同一事务中消费 Ticket 并创建用户，不能仅依赖前端记录邮箱已验证。

忘记密码接口无论邮箱是否存在均返回中性成功响应。邮件链接携带高熵一次性令牌，数据库只保存令牌 HMAC；成功重置后令牌作废、用户认证版本递增，旧 JWT 随即失效。

## 认证接口

| 接口 | 说明 |
|---|---|
| `GET /api/auth/me` | 获取当前登录用户 |
| `PUT /api/auth/profile` | 修改昵称等资料 |
| `PUT /api/auth/password` | 校验旧密码后修改密码，并使旧 JWT 失效 |

成功登录返回 Token、有效期和用户摘要。后续请求使用：

```text
Authorization: Bearer <token>
```

历史用户允许暂时没有邮箱并继续使用用户名登录；新注册用户必须绑定并验证唯一邮箱。用户角色至少区分 `USER` 和 `ADMIN`，页面隐藏不能代替后端权限校验。

## 外部配置

- `VITE_TURNSTILE_SITE_KEY` 只用于前端控件，可公开。
- `TURNSTILE_SECRET_KEY` 只由后端读取。
- SMTP 密码、认证令牌 HMAC Secret 和 JWT Secret 不进入仓库或日志。
- 本地 Docker 使用 Mailpit 接收开发邮件；生产环境通过 SMTP 环境变量切换真实邮件服务。
