# API 参考

本目录记录 LearnPlatform 的稳定接口约定和业务域入口。运行时字段定义以 Spring Controller、DTO 和 Knife4j/OpenAPI 为准；本文档负责解释认证、权限、业务边界和前后端共同依赖的契约。

## 在线接口定义

- 本地 Knife4j：`http://localhost:8080/doc.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`
- 基础路径：`/api`
- 管理端路径：`/api/admin`

## 业务域

| 文档 | 覆盖范围 | 后端入口 |
|---|---|---|
| [通用约定](conventions.md) | 响应、认证、分页、错误和 SSE | 全部 Controller |
| [认证与用户](auth-and-users.md) | 注册、登录、验证码、资料、用户管理 | `AuthController`、`CaptchaController`、`AdminUserController` |
| [学习内容](learning-content.md) | 课程、知识点、题目、收藏、评论、投稿、搜索 | 内容相关 Controller |
| [练习与复习](practice-and-review.md) | 练习、错题、间隔重复、统计诊断 | 学习闭环 Controller |
| [考试](exams.md) | 试卷、考试、判分、智能组卷 | `ExamController`、`AdminExamController` |
| [AI 学习](ai-learning.md) | AI 解析、资产、变式训练和结构化判分 | `AiController` |
| [管理与治理](admin-governance.md) | 内容治理、投稿审核、AI 用量、运营统计 | 管理端 Controller |

## 维护规则

1. 接口方法、路径或权限变化时，同一提交更新对应领域文档。
2. 字段级定义优先通过 DTO 注解进入 OpenAPI，不在 Markdown 中复制所有 Java 字段。
3. Markdown 只保留前端实现、业务判断或安全边界真正需要的示例。
4. `scripts/check-docs.py` 会从 Controller 提取映射，防止新增接口没有进入文档。
