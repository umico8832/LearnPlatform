# 学习内容域数据

## 用户与课程

| 表 | 职责 | 关键约束 |
|---|---|---|
| `user` | 用户名、验证邮箱、角色、状态、认证版本和 AI 日配额 | 用户名和非空邮箱唯一；密码只保存 BCrypt 哈希 |
| `email_verification` | 注册邮箱验证码、验证票据与尝试状态 | 验证码和票据只保存 HMAC；票据只能消费一次 |
| `password_reset_token` | 一次性密码重置令牌 | 只保存令牌 HMAC；过期或已使用令牌不可重复消费 |
| `course` | 课程元数据和排序 | 删除前检查题目与知识点引用 |
| `knowledge_point` | 树形知识点 | `parent_id` 表达同课程内父子关系 |
| `user_course` | 用户加入个人课程库的关系 | 用户与课程组合唯一；加入课程不表示学习或掌握 |
| `course_learning_event` | 跨 AI 教学与试卷入口的课程学习事实 | `(user_id, course_id, idempotency_key)` 唯一；事件追加且带版本、来源和发生时间 |
| `tutor_content` | 已审查、版本化的 Tutor 教学内容和检查定义 | `(content_key, content_version)` 唯一；正确选项不返回客户端 |
| `tutor_session` | 用户课程 Tutor 会话、学习证据聚合快照及首次检查结果 | `session_key` 唯一；会话归属用户、课程与知识点 |
| `course_stage_assessment` | 用户课程阶段测评会话、选题策略、知识点范围与汇总结果 | `(user_id, course_id, active_session_key)` 限制一个进行中会话；完成时活动键清空 |
| `course_stage_assessment_question` | 测评题目、答案、解析、来源与知识点快照及用户作答 | 会话内原题和排序均唯一；提交前不通过 API 暴露答案快照 |

### 内容标识与教学存储

课程和知识点的可空 `content_key` 提供跨端稳定引用，存量内容可以继续为空；`content_source` 记录来源，
不代表审核结论。`content_version` 与 `content_review_status` 保存迁入时的版本和审查事实，
不能替代 Web 发布或访问权限。

`tutor_content.check_json` 保存检查题的私有正确选项与对应解释；`lesson_json` 保存教学内容，
其中的 `visualization` 只保存已注册课件的受限场景参数，不保存脚本、动态组件或用户运行态。
路径用稳定 `contentKey` 引用内容，访问边界与参数
定义见[课程与 Tutor API](../api/learning-content.md#tutor-会话)及[受限课件](../api/learning-content.md#受限课件)。
教学批次与算法范围见既有[迁移历史](../../project/changelog/2026-09.md#aistu-资源迁移历史快照)，
数据库文档不重复维护课程内容清单。

### 课程事实与 Tutor 快照

`course_learning_event` 以用户、课程和来源幂等键唯一标识一条可追加事实，保留事件版本、入口与业务时间。
只有已加入课程库后的相关作答才投影为课程事件，不保存原始答案，也不存储由事件推断的掌握度。
加入课程、推荐目标与前端显示进度均不直接创建作答事实。

`tutor_session.learning_context_json` 在会话开始时固化目标知识点及同课程祖先目录关联的试卷学习、
试卷 AI、错题与复习计数及最近时间。它不保存原始答案、正确答案或 AI 输出，不替代原始业务记录。
聚合快照只服务于该次教学上下文；接口字段和 Tutor 进度语义见[课程概览](../api/learning-content.md#课程概览)
与[Tutor 会话](../api/learning-content.md#tutor-会话)。

### 阶段测评存储与事务

- `course_stage_assessment` 按用户和课程隔离。创建事务锁定个人课程关系，通过 `active_session_key`
  唯一约束保护单个进行中会话；完成时显式置 `NULL`，释放下一次创建所需的活动键。
- `course_stage_assessment_question` 在创建时固化题干、选项、私有答案、解析、来源类型与可空母题 ID。
  判分与复盘读取快照，避免原题后续变更重写既有结果。
- `source_category_snapshot` 保存 `OFFICIAL_EXAM`、`MANUAL`、`USER_PRIVATE` 或 `AI_GENERATED`。
  AI、私有来源优先保持自身类别；官方类别取决于创建时是否被已发布且来源核验的官方试卷引用。
  历史统计不重新读取正式题来源；存量分类回填规则以相应迁移为准。
- 会话可空的 `target_knowledge_point_id` 与 `target_knowledge_point_name_snapshot` 保存限定范围，
  未限定时为空；逐题 `knowledge_points_json` 固化关联知识点 ID 和名称，改名或重新关联不重写历史。
- 完成事务保存逐题结果、释放活动键并投影到错题、复习计划与课程事件；重复完成不重复写入。
  已完成记录按 `(user_id, course_id, complete_time)` 提供本人历史与最近摘要。

创建范围、选题顺序、公开响应与提交条件由[阶段测评 API](../api/learning-content.md#阶段测评)维护。
AI 生成题的批准发布行为见[管理 API](../api/admin-governance.md#ai-变式题审查)；正式题从创建和首个
版本快照起即保留 `AI_GENERATED` 及母题来源，不能由测评写入绕过内容发布。

## 题目

| 表 | 职责 | 关键约束 |
|---|---|---|
| `question` | 题干、题型、答案、解析、来源、可空母题和复审状态 | 正式题目受试卷引用和发布状态保护 |
| `question_option` | 选择题选项及正确标记 | 用户端 DTO 不得暴露正确标记 |
| `question_knowledge_point` | 题目与知识点多对多关联 | 题目和知识点组合不能重复 |
| `question_version` | 正式题目修改前后的版本快照 | 版本只追加，不覆盖历史 |
| `question_review_record` | 正式题目定期复审记录 | 记录审查人、结论和时间 |
| `question_correction_report` | 用户纠错及管理员处理状态 | 状态变化需要记录处理人和结果 |

## 收藏与评论

| 表 | 职责 | 关键约束 |
|---|---|---|
| `user_favorite_question` | 用户收藏题目 | 用户与题目组合唯一 |
| `question_comment` | 题目评论 | 删除和可见性受作者或管理员权限控制 |
| `comment_like` | 评论点赞 | 用户与评论组合唯一 |

## 投稿

| 表 | 职责 | 关键约束 |
|---|---|---|
| `question_submission` | 用户投稿、AI 辅助结果和审核状态 | 投稿与正式题目分离；入库必须显式确认 |

投稿通过审核后，由业务事务创建正式 `question`、选项和知识点关系；原投稿继续保留来源和审核记录。

## 删除策略

- 用户历史行为引用的题目通常不能物理删除。
- 课程或知识点删除前必须检查下游关系。
- 评论、收藏等用户关系按业务需要使用物理删除或逻辑状态，具体以 Mapper 与迁移为准。
- 文档不应把 UI 上的“删除按钮”直接解释成数据库物理删除。
