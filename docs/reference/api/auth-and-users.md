# 认证与用户 API

## 公开与认证接口

| 接口 | 说明 |
|---|---|
| `GET /api/public/health` | 服务健康检查 |
| `GET /api/auth/captcha` | 获取登录验证码 |
| `POST /api/auth/register` | 注册并返回登录信息 |
| `POST /api/auth/login` | 登录并返回 JWT |
| `GET /api/auth/me` | 获取当前登录用户 |
| `PUT /api/auth/profile` | 修改昵称、头像等资料 |
| `PUT /api/auth/password` | 校验旧密码后修改密码 |

## 登录请求示例

```json
{
  "username": "testuser",
  "password": "test123",
  "captchaId": "captcha-id",
  "captchaCode": "ABCD"
}
```

验证码字段是否必填取决于当前认证策略和 DTO 定义。客户端应从验证码接口获取本次挑战，不得复用过期验证码。

## 认证响应

成功登录返回 Token、有效期和用户摘要。后续请求使用：

```text
Authorization: Bearer <token>
```

用户角色至少区分 `USER` 和 `ADMIN`。页面隐藏不能代替后端权限校验。

## 用户管理

管理员用户接口集中列在[管理与治理 API](admin-governance.md#用户管理)。
