# 管理与治理 API

本页覆盖管理员角色接口。所有 `/api/admin/**` 路径必须由后端执行角色校验。

## 用户管理

| 接口 | 说明 |
|---|---|
| `GET /api/admin/users` | 查询用户 |
| `POST /api/admin/users` | 创建用户 |
| `PUT /api/admin/users/{id}/role` | 修改角色 |
| `PUT /api/admin/users/{id}/status` | 修改状态 |
| `PUT /api/admin/users/{id}/ai-daily-quota` | 调整 AI 日配额 |
| `GET /api/admin/users/{id}/ai-daily-quota/audits` | 查询配额审计 |
| `PUT /api/admin/users/{id}/reset-password` | 重置密码 |
| `DELETE /api/admin/users/{id}` | 删除允许删除的用户 |
| `GET /api/admin/users/stats` | 用户统计 |

## 课程与知识点管理

| 接口 | 说明 |
|---|---|
| `POST /api/admin/courses` | 创建课程 |
| `PUT /api/admin/courses/{id}` | 修改课程 |
| `DELETE /api/admin/courses/{id}` | 删除课程 |
| `POST /api/admin/knowledge-points` | 创建知识点 |
| `PUT /api/admin/knowledge-points/{id}` | 修改知识点 |
| `DELETE /api/admin/knowledge-points/{id}` | 删除知识点 |

## 正式题目治理

| 接口 | 说明 |
|---|---|
| `GET /api/admin/questions` | 查询管理端题目 |
| `GET /api/admin/questions/duplicates` | 查询疑似重复题 |
| `GET /api/admin/questions/{id}` | 获取完整题目详情 |
| `POST /api/admin/questions` | 创建正式题目 |
| `PUT /api/admin/questions/{id}` | 修改允许修改的题目 |
| `DELETE /api/admin/questions/{id}` | 删除允许删除的题目 |
| `GET /api/admin/questions/{id}/versions` | 查询版本历史 |
| `GET /api/admin/questions/source-stats` | 来源统计 |
| `GET /api/admin/questions/source-types` | 来源类型 |
| `GET /api/admin/questions/correction-reports` | 查询纠错报告 |
| `POST /api/admin/questions/correction-reports/{reportId}/process` | 处理纠错报告 |
| `GET /api/admin/questions/review-overdue` | 查询复审逾期题目 |
| `GET /api/admin/questions/{id}/review-records` | 查询复审记录 |
| `GET /api/admin/questions/{id}/review-suggestion` | 获取复审建议 |
| `POST /api/admin/questions/{id}/re-review` | 提交正式复审 |
| `GET /api/admin/questions/export` | 导出题目 |
| `GET /api/admin/questions/template` | 下载表格模板 |
| `POST /api/admin/questions/import` | 导入表格题目 |
| `POST /api/admin/questions/import-markdown` | 导入 Markdown 题目 |
| `GET /api/admin/questions/template-markdown` | 下载 Markdown 模板 |

## 投稿审核

| 接口 | 说明 |
|---|---|
| `GET /api/admin/submission` | 查询投稿 |
| `GET /api/admin/submission/{id}` | 获取投稿详情 |
| `POST /api/admin/submission/{id}/review` | 审核或拒绝 |
| `POST /api/admin/submission/{id}/quality-check` | AI 质量检查 |
| `POST /api/admin/submission/{id}/kp-tagging` | AI 知识点标注 |
| `POST /api/admin/submission/{id}/apply-kp` | 应用知识点 |
| `POST /api/admin/submission/{id}/difficulty-assessment` | AI 难度评估 |
| `POST /api/admin/submission/{id}/generate-review-comment` | 生成审核意见 |
| `POST /api/admin/submission/{id}/import` | 显式入库为正式题 |
| `GET /api/admin/submission/stats` | 投稿统计 |

AI 结果只作为审核辅助，不能绕过管理员确认自动发布。

## AI 运营

| 接口 | 说明 |
|---|---|
| `GET /api/admin/ai-logs` | 查询 AI 调用日志 |
| `GET /api/admin/ai-logs/stats` | 查询调用日志统计 |
| `GET /api/admin/ai-usage/overview` | AI 用量总览 |
| `GET /api/admin/ai-usage/report` | 周期用量报告 |
| `GET /api/admin/ai-usage/learning-effect` | 观察性学习效果 |
| `GET /api/admin/ai-usage/alerts` | 查询运营提醒 |
| `POST /api/admin/ai-usage/alerts/{id}/acknowledge` | 确认提醒 |

学习效果接口在样本量和去重学习者不足时返回 `INSUFFICIENT_DATA`，不得据此表达因果结论。

## 管理总览

| 接口 | 说明 |
|---|---|
| `GET /api/admin/statistics/overview` | 管理工作台总览 |
