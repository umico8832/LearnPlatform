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
| `GET /api/admin/courses` | 分页查询课程，包含禁用课程 |
| `GET /api/admin/courses/{id}` | 查询课程详情，包含禁用课程 |
| `POST /api/admin/courses` | 创建课程 |
| `PUT /api/admin/courses/{id}` | 修改课程 |
| `DELETE /api/admin/courses/{id}` | 删除没有下游引用的课程 |
| `POST /api/admin/knowledge-points` | 创建知识点 |
| `PUT /api/admin/knowledge-points/{id}` | 修改知识点 |
| `DELETE /api/admin/knowledge-points/{id}` | 删除没有子节点或下游引用的知识点 |

学习端课程列表和详情只暴露启用课程；管理端使用独立查询接口维护禁用课程。课程或知识点存在题目、
学习状态、测评、Tutor 内容等下游引用时拒绝删除。

## 知识快照导入

| 接口 | 说明 |
|---|---|
| `GET /api/admin/knowledge` | 分页查看知识版本，可按 `courseKey` 和 `reviewStatus` 精确筛选 |
| `GET /api/admin/knowledge/{bundleId}` | 查看导入信息、内容指纹和平台审核记录 |
| `GET /api/admin/knowledge/{bundleId}/chunks` | 分页查看该版本的片段原文、来源元数据和哈希，可按 `conceptId` 精确筛选 |
| `GET /api/admin/knowledge/{bundleId}/indexes` | 分页查看该版本索引的模型、维度、状态、已处理片段数和租约时间 |
| `POST /api/admin/knowledge/import` | 导入服务端 `KNOWLEDGE_SNAPSHOT_PATH` 指定的知识版本，无请求体 |
| `POST /api/admin/knowledge/{bundleId}/review` | 提交 `decision=REVIEWED` 或 `WITHDRAWN` 与必填 `note`（最多 1000 字符） |
| `POST /api/admin/knowledge/{bundleId}/index` | 为已审核版本显式构建配置指定的向量索引；每个 Embedding 批次消耗操作者的 AI 配额 |
| `POST /api/admin/knowledge/{bundleId}/withdraw` | 撤回版本并尝试清理向量索引；可重复调用以完成外部清理 |

查询在 HTTP 角色门禁之外复核数据库中的管理员身份，允许管理员查看待审和撤回版本。
分页参数为 `pageNum`（默认 1、至少 1）和 `pageSize`（默认 20、1–50），返回 `records/total/current/size`；
版本与索引按 ID 倒序，片段按 ID 正序；筛选在数据库分页前执行，越界页返回空列表。
缺失版本返回业务码 `1004`，非法分页或筛选返回 `1001`。索引查询不返回执行令牌或向量服务地址。
管理端 `/admin/knowledge` 提供版本查询、原文核验、人工批准和撤回，原文与来源元数据均以文本显示。
页面的撤回审核停止该版本检索；需要删除向量时使用下述显式撤回清理接口。

导入仅管理员可执行，返回版本 ID、课程内容键、版本号、清单哈希、片段数量、审核状态与是否已导入，
不返回本地路径或内容正文。同课程同版本同清单重复导入返回原记录；清单变化拒绝覆盖，须创建新版本。
清单或内容不合法时整包拒绝，片段落库失败回滚整个版本。导入始终进入 `PENDING`，
来源文件的 `reviewed` 也不能替代平台审核。审核只允许 `PENDING → REVIEWED`，撤回后不恢复原版本，
需重新导入新版本。审核记录保留操作者、时间和说明；应在实际核对内容与来源使用许可后再批准。
索引状态返回 ID、版本 ID、配置索引键、状态与完成片段数。构建中的版本不能并发领取，过期租约可重试。
撤回接口即使报告向量清理失败，版本也已不可检索；运行中租约到期或原向量端点恢复后重试可完成清理。

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

## AI 变式题审查

| 接口 | 说明 |
|---|---|
| `GET /api/admin/ai-variant-reviews` | 按 `PENDING`、`APPROVED` 或 `REJECTED` 分页读取结构化变式题审查队列 |
| `POST /api/admin/ai-variant-reviews/{variantId}` | 提交 `APPROVE` 或 `REJECT`；驳回必须填写说明 |

批准操作只接受母题仍为当前课程公开已发布题目的结构化变式题，并将其物化为正式题目。正式题从首次版本
快照起保留 `AI_GENERATED`、`AI_VARIANT:{variantId}` 和母题 ID；待审或驳回项不会进入阶段测评。

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

## AI 调用生命周期

AI 完成日志列表及成功/失败统计排除 `RUNNING` 预登记记录，用户日配额仍计入这些记录。
日志响应额外提供 callId、runId、callKind、outcome、requestedModel、finishReason 和 responseId；
旧数据允许为空。字段语义见[AI 与治理数据](../database/ai-and-governance.md)。
