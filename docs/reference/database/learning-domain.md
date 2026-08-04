# 学习内容域数据

## 用户与课程

| 表 | 首次迁移 | 职责 | 关键约束 |
|---|---|---|---|
| `user` | V1，V20 扩展 | 用户名、验证邮箱、角色、状态、认证版本和 AI 日配额 | 用户名和非空邮箱唯一；密码只保存 BCrypt 哈希 |
| `email_verification` | V20 | 注册邮箱验证码、验证票据与尝试状态 | 验证码和票据只保存 HMAC；票据只能消费一次 |
| `password_reset_token` | V20 | 一次性密码重置令牌 | 只保存令牌 HMAC；过期或已使用令牌不可重复消费 |
| `course` | V1 | 课程元数据和排序 | 删除前检查题目与知识点引用 |
| `knowledge_point` | V1 | 树形知识点 | `parent_id` 表达同课程内父子关系 |
| `user_course` | V21 | 用户加入个人课程库的关系 | 用户与课程组合唯一；加入课程不表示学习或掌握 |
| `course_learning_event` | V22 | 跨 AI 教学与试卷入口的课程学习事实 | `(user_id, course_id, idempotency_key)` 唯一；事件追加且带版本、来源和发生时间 |
| `tutor_content` | V24 | 已审查、版本化的 Tutor 教学内容和检查定义 | `(content_key, content_version)` 唯一；正确选项不返回客户端 |
| `tutor_session` | V24 | 用户课程 Tutor 会话及首次检查结果 | `session_key` 唯一；会话归属用户、课程与知识点 |

V21 为课程和知识点增加可空的 `content_key` 与 `content_source`。`content_key` 用于在
AiStu、Web 后端和后续内容导入之间保持稳定引用；存量平台内容可以继续使用空键。
`content_source` 记录内容来源，不代表审核结论或权威等级。V23 为需要分批迁入的原子
知识增加可空的 `content_version` 和 `content_review_status`；后者只保存迁入时的审查
事实，不能替代 Web 端的发布或权限状态。`ods-array-size-capacity`、
`ods-arraystack-insertion`、`ods-arraystack-removal`、`ods-arraystack-resize`、
`ods-arraystack-amortized-resize`、`ods-arraystack-performance` 与
`ods-fastarraystack-block-copy`、`ods-arrayqueue-representation`、`ods-arrayqueue-enqueue` 与
`ods-arrayqueue-dequeue`、`ods-arrayqueue-resize` 均绑定父目录“栈、队列和数组”，
版本为 1、迁入时状态为 `REVIEWED`。

`course_learning_event` 只记录已加入个人课程库后产生的课程内事实；普通题库练习不会
被自动解释为课程进度。首版映射练习、复习、考试逐题作答与结构化 AI 变式题的首次判分，
不保存用户原始答案或把事件直接折算为掌握度。

V25 为 `ods-arraystack-insertion` 的 `tutor_content.lesson_json` 增加已审查的
`ARRAY_STACK_INSERTION` v1 参数定义。它只包含容量、初始槽位和插入参数；课件动画状态由
前端固定渲染器从这些数据推导，不在数据库中存放脚本或用户运行态。

V26 在同一已审查内容中增加前置与后续路径提示。它们只能在 Tutor 首次服务端判分后返回：
答错提示“元素数量与数组容量”，答对提示“ArrayStack 的操作复杂度”。两者均不创建独立
`tutor_content` 或可访问路由，避免把尚未迁入、尚未单独审查的知识误表示为已开放教学。

V27 迁入 ArrayStack 按位删除的独立 Tutor 内容与理解检查。理解检查的正确选项和正误解释
均保存在已审查的 `check_json` 中；服务端只在判分结果中返回对应解释，不能将某一内容的
固定解释复用于其他内容。

V28 迁入“元素数量与数组容量”的独立 Tutor 内容与理解检查。它以 `n`、`length(a)` 和
`0 ≤ n ≤ length(a)` 为审查范围，不把空闲槽位解释为逻辑元素；插入与删除内容中的同名
前置提示同时更新为可从课程目录进入的已迁入内容。

V29 迁入“ArrayStack 的容量调整”的独立 Tutor 内容、理解检查和 `ARRAY_STACK_RESIZE` v1
受限课件定义。课件只保存旧容量和有效元素；固定客户端渲染器推导 `max(1, 2n)` 的新容量与
复制状态，不存储脚本、动态组件或用户运行态。内容只陈述单次 `resize` 的 `O(n)` 成本，摊还
分析在后续 V30 作为独立知识迁入。

V30 迁入“ArrayStack 调整容量的摊还成本”的独立 Tutor 内容和理解检查。该内容只陈述从空
结构开始的 `m` 次 `add/remove` 中全部 `resize` 总成本为 `O(m)`，并明确单次触发仍可能为
`O(n)`；它不创建可执行课件，也不将结论扩展为尚未迁入的完整操作复杂度教学。

V31 迁入“ArrayStack 的操作复杂度”的独立 Tutor 内容和理解检查。它只汇总 `get/set` 的
`O(1)` 最坏时间、忽略 `resize` 的按位 `add/remove` 成本 `O(1+n-i)`，以及尾端 push/pop 的
`O(1)` 摊还时间；单次尾端更新仍可能因 resize 耗时 `O(n)`，不将摊还界写成最坏界。

V32 迁入“FastArrayStack 的批量复制优化”的独立 Tutor 内容和理解检查。它只陈述批量复制
可以替代连续区间的显式循环并降低常数开销；由于处理元素数不变，插入、删除和 `resize` 的
渐近时间复杂度不变，且不承诺固定加速倍数。

V33 迁入“ArrayQueue 的循环数组表示”的独立 Tutor 内容、理解检查与
`ARRAY_QUEUE_REPRESENTATION` v1 受限课件定义。配置只包含容量、队首物理下标和逻辑元素序列；
固定客户端渲染器据此回放 `a[(j+k) mod capacity]` 的回绕映射，不存储脚本、动态组件或用户运行态。
内容不延伸至入队、出队、扩缩容或其复杂度结论。

V34 迁入“ArrayQueue 的入队”的独立 Tutor 内容、理解检查与 `ARRAY_QUEUE_ENQUEUE` v1 受限课件
定义。配置只包含容量、队首物理下标、既有逻辑元素和新元素；固定客户端渲染器据此回放容量充足时
`a[(j+n) mod capacity]` 的队尾写入及 `n` 增加，不存储脚本、动态组件或用户运行态。内容不延伸至
满队扩容、出队或其复杂度结论。

V35 迁入“ArrayQueue 的出队”的独立 Tutor 内容、理解检查与 `ARRAY_QUEUE_DEQUEUE` v1 受限课件
定义。配置只包含容量、队首物理下标和非空逻辑元素；固定客户端渲染器据此回放读出 `a[j]`、
`j = (j+1) mod capacity` 与 `n` 减一，不存储脚本、动态组件或用户运行态。内容不延伸至空队列、
缩容或其复杂度结论。

V36 迁入“ArrayQueue 调整容量时的线性化复制”的独立 Tutor 内容、理解检查与
`ARRAY_QUEUE_RESIZE` v1 受限课件定义。配置只包含旧数组容量、队首物理下标和跨界的非空逻辑元素；
固定客户端渲染器据此回放 `b[k] = a[(j+k) mod oldCapacity]` 的 FIFO 复制与 `j = 0`，不存储脚本、
动态组件或用户运行态。内容不陈述扩缩容触发条件、特定容量策略的合理性或摊还复杂度。

## 题目

| 表 | 首次迁移 | 职责 | 关键约束 |
|---|---|---|---|
| `question` | V1，V8 扩展 | 题干、题型、答案、解析、来源和复审状态 | 正式题目受试卷引用和发布状态保护 |
| `question_option` | V1 | 选择题选项及正确标记 | 用户端 DTO 不得暴露正确标记 |
| `question_knowledge_point` | V1 | 题目与知识点多对多关联 | 题目和知识点组合不能重复 |
| `question_version` | V16 | 正式题目修改前后的版本快照 | 版本只追加，不覆盖历史 |
| `question_review_record` | V8 | 正式题目定期复审记录 | 记录审查人、结论和时间 |
| `question_correction_report` | V15 | 用户纠错及管理员处理状态 | 状态变化需要记录处理人和结果 |

## 收藏与评论

| 表 | 首次迁移 | 职责 | 关键约束 |
|---|---|---|---|
| `user_favorite_question` | V1 | 用户收藏题目 | 用户与题目组合唯一 |
| `question_comment` | V4 | 题目评论 | 删除和可见性受作者或管理员权限控制 |
| `comment_like` | V4 | 评论点赞 | 用户与评论组合唯一 |

## 投稿

| 表 | 首次迁移 | 职责 | 关键约束 |
|---|---|---|---|
| `question_submission` | V7 | 用户投稿、AI 辅助结果和审核状态 | 投稿与正式题目分离；入库必须显式确认 |

投稿通过审核后，由业务事务创建正式 `question`、选项和知识点关系；原投稿继续保留来源和审核记录。

## 删除策略

- 用户历史行为引用的题目通常不能物理删除。
- 课程或知识点删除前必须检查下游关系。
- 评论、收藏等用户关系按业务需要使用物理删除或逻辑状态，具体以 Mapper 与迁移为准。
- 文档不应把 UI 上的“删除按钮”直接解释成数据库物理删除。
