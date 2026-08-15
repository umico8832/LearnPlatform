# 学习内容 API

## 课程与知识点

| 接口 | 说明 |
|---|---|
| `GET /api/courses` | 分页或筛选课程 |
| `GET /api/courses/list` | 获取课程简表 |
| `GET /api/courses/{id}` | 获取课程详情 |
| `GET /api/knowledge-points/tree/{courseId}` | 获取课程知识点树 |
| `POST /api/my-courses/{courseId}` | 将已开放课程幂等加入当前用户的个人课程库 |
| `GET /api/my-courses` | 查询当前用户的个人课程库 |
| `GET /api/my-courses/{courseId}/overview` | 查询已加入课程的学习概况与下一步候选目标 |
| `POST /api/my-courses/{courseId}/start-learning` | 不指定知识点，按当前统一课程状态选择下一学习目标 |
| `POST /api/my-courses/{courseId}/stage-assessments` | 创建或恢复当前用户在课程中的进行中阶段测评 |
| `GET /api/my-courses/{courseId}/stage-assessments` | 分页查询当前用户在课程中的已完成测评摘要 |
| `GET /api/my-courses/stage-assessments/{assessmentId}` | 查询本人已完成测评的逐题复盘 |
| `POST /api/my-courses/stage-assessments/{assessmentId}/submit` | 完整提交本人阶段测评并由服务端判分 |
| `POST /api/my-courses/{courseId}/tutor-sessions` | 以已审查的课程知识点开始 Tutor 会话（必填查询参数 `knowledgePointId`） |
| `POST /api/my-courses/{courseId}/tutor-sessions/{sessionKey}/check` | 提交该会话的理解检查 `{ "optionId": "..." }` |

课程和知识点的写接口位于[管理与治理 API](admin-governance.md#课程与知识点管理)。
个人课程库关系以服务端认证用户为准，客户端不能指定或查询其他用户的 `userId`。
添加课程只建立学习入口，不表示已经开始学习或掌握课程内容。

课程总览仅对当前用户已加入的课程开放。它从已保存的课程学习事件、错题与到期复习计划聚合
`answeredCount`、`correctCount`、`dueReviewCount`、`unresolvedWrongCount` 和最近学习时间；
不会返回或推断“掌握度”。`tutorProgress` 只列出当前课程中已审查、已迁入的 Tutor 内容，并以
`NOT_STARTED`、`IN_PROGRESS`、`COMPLETED` 表示是否存在会话或首次正确理解检查；未迁入的目录节点
不会被列为未完成。`recommendedTargets` 依次给出当前课程中已审查且尚未完成理解检查的 Tutor 内容、
到期复习、未掌握错题和课程默认目录。Tutor 目标携带当前课程内的
`knowledgePointId`，客户端只能跳转到既有 Tutor 页面；一次正确理解检查后，该内容不再作为
未完成教学推荐。

`start-learning` 不接收 `knowledgePointId`、`questionId` 或客户端排序结果，而是在请求发生时复用课程总览的
统一排序并返回首个目标。响应目标类型为 `TUTOR`、`DUE_REVIEW`、`WRONG_QUESTION` 或
`COURSE_SEQUENCE`，只携带对应的可空 `knowledgePointId` 或 `questionId`。选择目标本身不生成掌握证据；
实际 Tutor 理解检查或题目判分发生后才写入课程学习事实。

阶段测评仅对已加入课程开放。创建请求可传 `questionCount`（1–20，默认 5）和可空的
`knowledgePointId`：不传时从整门课程选题；传入时服务端独立校验该知识点属于当前课程且审查状态为
`REVIEWED`，并只从该知识点关联的当前用户可访问已发布单选、多选和判断题中选题，题量不足时返回
实际题数，没有候选时明确失败，不会扩大到其他课程或知识点。同一用户与课程最多保留
一个进行中会话并在重复创建时恢复。服务端只选择当前用户可访问的已发布单选、多选和判断题，优先顺序为
未掌握错题、到期复习、近期错误课程事件和题目 ID；存在上述事实时返回
`LEARNING_STATE_PRIORITY`，否则返回 `COURSE_SEQUENCE_FALLBACK`，后者不能宣称为 AI 个性化。
已由管理员审查通过并发布到当前课程的 AI 生成客观题属于同一候选集合；待审、驳回、母题失效或其他
课程的生成题不属于候选，组卷过程不会为凑题调用模型。题目在创建时固化题干、选项、答案、解析、
`sourceType` 和可空母题 ID；进行中响应不返回正确答案与解析，但会把 AI 生成来源明确展示给学习者。

提交必须包含全部且不重复的测评题答案。服务端按快照答案统一判分，完成后返回逐题正误、参考答案与解析，
并将每题结果写入错题、间隔复习计划和来源为 `STAGE_ASSESSMENT` 的课程学习事件；重复提交已完成测评
只返回既有结果，不重复写学习事实。完成会释放活动会话键，后续可以开启新一轮。
历史接口只返回当前用户在已加入课程中的已完成记录，并按完成时间倒序分页；摘要只含题数、正确数、
选题策略和时间。逐题详情继续以会话所有者校验，并使用创建时快照复盘。课程总览的
`latestStageAssessment` 只表示最近一次完成事实，不生成掌握度或趋势结论。
当以知识点范围创建时，进行中会话、完成详情、最近摘要和历史摘要还会返回
`targetKnowledgePointId` 与 `targetKnowledgePointName`：名称随会话创建固化，不随知识点改名或
课程内容调整变化；未限定范围时两者为空，前端展示为课程整体。
逐题详情中每题还返回 `knowledgePoints`（知识点 ID 与名称列表），在创建测评时从题目关联固化，
后续修改题目知识点关联不重写历史。完成详情还返回 `knowledgePointSummary`，按知识点汇总本轮题数与
正确数，只统计已固化的逐题快照。课程总览的 `latestStageAssessment` 同样返回该知识点事实摘要，
不打开复盘即可看到最近一轮各知识点题数与正误数。前端只在知识点属于当前课程已审查 Tutor 内容时提供进入
AI 教学的入口；错题提供进入错题复习的深链。这些标注与统计只呈现题目归属和作答正误事实，
不从单次或多次测评结果推断掌握度、趋势或个性化结论。
测评详情、最近摘要和历史摘要还返回 `sourceComposition`，分别统计 `officialExamCount`、`manualCount`、
`userPrivateCount` 和 `aiGeneratedCount`。统计只读取创建测评时固化的来源类别，不随正式题后续来源或
试卷关联变化；这些数量只解释题目出处，不表达难度、质量、推荐效果或掌握度。

Tutor 仅可打开已加入课程、属于该课程且审查状态为 `REVIEWED` 的内容；响应不会返回正确选项。
理解检查由服务端首次判分并写入可追加课程学习事件，重复提交返回既有结果。
启动 Tutor 会话时，响应的 `learningContext` 会返回目标知识点及其同课程祖先目录范围内的服务端证据
聚合快照：`paperAnswerCount`、`paperIncorrectCount`、`paperAiAssistanceCount`、
`unresolvedWrongCount`、`dueReviewCount`、`reviewAnswerCount` 和 `latestEvidenceAt`。快照随会话保存，
只包含计数与时间，不返回原始答案、正确答案或 AI 输出，也不能被解释为掌握度；没有相关证据时前端不
制造进度卡片。
当已审查教学内容显式声明路径时，理解检查响应会额外给出 `guidanceType`、
`guidanceTitle`、`guidanceDescription` 和可空的 `guidanceKnowledgePointId`：答错时为前置补充
（`PREREQUISITE`），答对时为后续目标（`NEXT_TARGET`）。前三项只表达路径建议；服务端仅在目标
属于当前课程且知识点状态为 `REVIEWED` 时返回可导航 ID，客户端不能用内容中的任意标识绕过课程
与审查边界。

已审查 Tutor 内容在 `lesson.visualization` 中使用受限的
`ARRAY_STACK_INSERTION` v1 Schema：`capacity`（1–12）、`initialElements`（非空字符串数组，
长度小于 capacity）、`insertIndex` 与 `insertValue`。客户端仅对通过该 Schema 校验的固定类型
渲染器进行回放。`ARRAY_STACK_RESIZE` v1 Schema 使用 `previousCapacity`（1–12）和与其长度
相等的非空 `initialElements`，固定渲染器据此展示 `max(1, 2n)` 的新数组和按序复制。
`ARRAY_QUEUE_REPRESENTATION` v1 Schema 使用 `capacity`（2–12）、`headIndex`（合法物理下标）和
长度小于 capacity 的非空 `elements`；固定渲染器只展示 `a[(j+k) mod capacity]` 映射与 FIFO
逻辑顺序。`ARRAY_QUEUE_ENQUEUE` v1 Schema 在相同循环数组参数外增加非空 `enqueueValue`，并要求
`elements.length < capacity`；固定渲染器只回放 `a[(j+n) mod capacity]` 的写入与 `n` 增加。
`ARRAY_QUEUE_DEQUEUE` v1 Schema 使用同一循环数组参数，但只要求非空 `elements`；固定渲染器只回放
读出 `a[j]`、`j = (j+1) mod capacity` 与 `n` 减一。`ARRAY_QUEUE_RESIZE` v1 Schema 使用
`previousCapacity`（2–12）、`headIndex` 和非空 `elements`，且逻辑元素必须跨越旧数组末端；固定渲染器
只回放 `b[k] = a[(j+k) mod oldCapacity]` 的 FIFO 复制与 `j = 0`，不解释触发条件或摊还复杂度。
`ARRAY_DEQUE_REPRESENTATION` v1 Schema 使用 `capacity`（2–12）、`headIndex`、非空 `elements` 与
范围内的 `accessIndex`；固定渲染器只回放逻辑下标 `i` 到 `a[(j+i) mod capacity]` 的映射和 `get(i)`
访问，不实现插入、删除、搬移或脚本化行为。
`ARRAY_DEQUE_FRONT_SHIFT_INSERT` v1 Schema 使用 `capacity`（3–12）、`headIndex`、至少两个且未满的
`elements`、靠近逻辑前端的 `insertIndex` 与非空 `insertValue`；固定渲染器只回放 `j` 左移回绕、前缀
搬移和写入，不实现尾端分支、删除、resize 或通用操作脚本。
`DUAL_ARRAY_DEQUE_REPRESENTATION` v1 Schema 使用长度至多 6 的 `front`、`back` 字符串数组和范围内的
`accessIndex`，且两栈合计至少一个元素；固定渲染器只回放 `reverse(front)` 后接 `back` 的逻辑顺序与
前缀反向/后缀偏移下标映射。`DUAL_ARRAY_DEQUE_BALANCE` v1 Schema 使用同样受限的两栈数组，要求总元素
数至少为 2 且一侧严格超过另一侧三倍；固定渲染器只回放按逻辑顺序重建为近似等大的两栈，不接受操作、
脚本或用户运行态。
`ROOTISH_ARRAY_STACK_LAYOUT` v1 Schema 使用 1–5 个数组块，且第 b 个块恰有 b+1 个非空字符串元素；
固定渲染器只回放递增块容量、逻辑顺序与总容量公式，不接受下标公式、脚本、动态组件或用户运行态。
`SEQUENTIAL_LIST_STORAGE` v1 Schema 使用受限的首地址、元素宽度、1–8 个非空逻辑元素及范围内访问下标；
固定渲染器只回放连续地址和 `LOC(ai)=LOC(a1)+(i-1)l` 的直接寻址，不接受地址表达式执行、脚本或用户运行态。
`LINKED_LIST_REVERSAL` v1 Schema 只接受 2–6 个非空字符串元素；固定渲染器逐步回放 `prev`、`cur`、保存的
`next` 和已逆置前缀，不接受用户输入的指针、脚本、动态组件或可执行配置。
`FACTORIAL_CALL_STACK` v1 Schema 只接受 2–6 的整数 `startValue`；固定渲染器回放阶乘活动记录的压栈、
基例与逐层返回，不接受函数体、表达式、脚本、动态组件或用户运行态。
所有类型都拒绝把课件 JSON 解释为脚本、动态组件或通用可执行配置。

## 题库与纠错

| 接口 | 说明 |
|---|---|
| `GET /api/questions` | 按课程、知识点、题型等条件查询题目 |
| `GET /api/questions/{id}` | 获取用户可见的题目详情 |
| `POST /api/questions/{id}/correction-reports` | 提交题目纠错 |
| `GET /api/questions/correction-reports/my` | 查询我的纠错记录 |

用户题目详情不能泄露正确选项和标准解析。判分结果只能通过练习或考试提交接口获得。

## 收藏

| 接口 | 说明 |
|---|---|
| `POST /api/favorites/{questionId}` | 收藏题目 |
| `DELETE /api/favorites/{questionId}` | 取消收藏 |
| `GET /api/favorites/{questionId}/status` | 查询收藏状态 |
| `GET /api/favorites` | 查询收藏列表 |
| `GET /api/favorites/ids` | 查询收藏题目 ID 集合 |

## 评论

| 接口 | 说明 |
|---|---|
| `GET /api/comments/question/{questionId}` | 查询题目评论 |
| `POST /api/comments` | 发布评论 |
| `DELETE /api/comments/{commentId}` | 删除本人评论或执行管理删除 |
| `POST /api/comments/{commentId}/like` | 切换点赞状态 |
| `GET /api/comments/count/{questionId}` | 查询评论数量 |

## 用户投稿

| 接口 | 说明 |
|---|---|
| `POST /api/submission` | 提交题目投稿 |
| `GET /api/submission/my` | 查询我的投稿 |
| `GET /api/submission/{id}` | 查询本人可见的投稿详情 |

投稿不会自动成为正式题目，必须经过管理员审核和显式入库。

## 全局搜索

| 接口 | 说明 |
|---|---|
| `GET /api/search` | 搜索课程、知识点和题目 |
| `GET /api/search/suggestions` | 获取建议、历史和热门词 |
| `DELETE /api/search/history` | 清空当前用户搜索历史 |
| `DELETE /api/search/history/item` | 删除单条搜索历史 |
