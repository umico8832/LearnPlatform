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
| `GET /api/my-courses/{courseId}/tutor-memory` | 读取本人在该课程保存的学习目标和讲解偏好 |
| `PUT /api/my-courses/{courseId}/tutor-memory` | 携带当前版本显式保存或纠正课程记忆 |
| `DELETE /api/my-courses/{courseId}/tutor-memory` | 必填查询参数 `revision`；清除当前目标和偏好 |
| `GET /api/my-courses/{courseId}/knowledge-point-facts` | 分页查询当前用户的知识点作答、错题和到期复习事实 |
| `POST /api/my-courses/{courseId}/start-learning` | 不指定知识点，按当前统一课程状态选择下一学习目标 |
| `POST /api/my-courses/{courseId}/stage-assessments` | 创建或恢复当前用户在课程中的进行中阶段测评 |
| `GET /api/my-courses/{courseId}/stage-assessments` | 分页查询当前用户在课程中的已完成测评摘要 |
| `GET /api/my-courses/stage-assessments/{assessmentId}` | 查询本人已完成测评的逐题复盘 |
| `POST /api/my-courses/stage-assessments/{assessmentId}/submit` | 完整提交本人阶段测评并由服务端判分 |
| `POST /api/my-courses/{courseId}/tutor-sessions` | 以已审查的课程知识点开始 Tutor 会话（必填查询参数 `knowledgePointId`） |
| `GET /api/my-courses/{courseId}/tutor-sessions/{sessionKey}` | 恢复本人在该课程中的 Tutor 会话 |
| `POST /api/my-courses/{courseId}/tutor-sessions/{sessionKey}/check` | 提交该会话的理解检查 `{ "optionId": "..." }` |
| `GET /api/my-courses/{courseId}/tutor-notes` | 分页读取本人已保存的会话复盘（查询参数 `page`，从 1 开始） |
| `GET /api/my-courses/{courseId}/tutor-sessions/{sessionKey}/note` | 读取本人该 Tutor 会话的复盘及可用的检查来源 |
| `PUT /api/my-courses/{courseId}/tutor-sessions/{sessionKey}/note` | 以 `{ "revision": 1, "note": "..." }` 显式保存或纠正复盘 |
| `DELETE /api/my-courses/{courseId}/tutor-sessions/{sessionKey}/note` | 必填查询参数 `revision`；删除本人该会话的复盘 |
| `POST /api/my-courses/{courseId}/tutor-sessions/{sessionKey}/agent-runs` | 以 `{ "message": "..." }` 创建 Tutor Agent 运行并提问 |
| `POST /api/my-courses/{courseId}/tutor-sessions/{sessionKey}/agent-runs/{runKey}/messages` | 恢复运行并继续提问 |
| `GET /api/my-courses/{courseId}/tutor-sessions/{sessionKey}/agent-runs/{runKey}` | 读取本人运行状态与可见消息 |
| `GET /api/my-courses/{courseId}/tutor-sessions/{sessionKey}/agent-runs/latest` | 找回本人该会话最近创建的运行；尚无运行时返回成功及空 data |
| `GET /api/my-courses/{courseId}/tutor-sessions/{sessionKey}/agent-runs/{runKey}/messages/{sequence}/practice` | 显式打开已保存推荐的变式练习，恢复本人首次结果 |
| `POST /api/my-courses/{courseId}/tutor-sessions/{sessionKey}/agent-runs/{runKey}/messages/{sequence}/practice/answer` | 提交 `{ "userAnswer": "B", "answerTime": 12 }`；返回首次判分，耗时秒数可省略 |
| `GET /api/my-courses/{courseId}/tutor-sessions/{sessionKey}/agent-runs/{runKey}/messages/{sequence}/plan` | 恢复已保存安排及真实确认状态，重新核对目标可用性 |
| `POST /api/my-courses/{courseId}/tutor-sessions/{sessionKey}/agent-runs/{runKey}/messages/{sequence}/plan/confirm` | 无请求体；显式确认该消息的安排，重试返回首次确认时间 |

课程和知识点的写接口位于[管理与治理 API](admin-governance.md#课程与知识点管理)。
个人课程库关系以服务端认证用户为准，客户端不能指定或查询其他用户的 `userId`。
添加课程只建立学习入口，不表示已经开始学习或掌握课程内容。

### 课程概览

课程总览及知识点事实仅对当前用户已加入的课程开放。查询直接聚合现有作答、测评快照、错题、
复习计划和学习事件，不保存另一份进度。`answeredCount` 与 `correctCount` 是加入课程后已判定的
非空作答次数和其中正确次数；`dueReviewCount` 与 `unresolvedWrongCount` 是当前待办题数。
`lastLearningTime` 为作答、人工批阅或课程学习事件的最近时间，可包含 AI 辅导，不等于最近判分时间。
不会返回或推断“掌握度”、趋势或个性化结论。`tutorProgress` 只列出当前课程中已审查、已迁入的 Tutor 内容，并以
`NOT_STARTED`、`IN_PROGRESS`、`COMPLETED` 表示是否存在会话或首次正确理解检查；未迁入的目录节点
不会被列为未完成。`recommendedTargets` 依次给出当前课程中已审查且尚未完成理解检查的 Tutor 内容、
到期复习、未掌握错题和课程默认目录。Tutor 目标携带当前课程内的
`knowledgePointId`，客户端只能跳转到既有 Tutor 页面；一次正确理解检查后，该内容不再作为
未完成教学推荐。

`start-learning` 不接收 `knowledgePointId`、`questionId` 或客户端排序结果，而是在请求发生时复用课程总览的
统一排序并返回首个目标。响应目标类型为 `TUTOR`、`DUE_REVIEW`、`WRONG_QUESTION` 或
`COURSE_SEQUENCE`，只携带对应的可空 `knowledgePointId` 或 `questionId`。选择目标本身不生成掌握证据；
实际 Tutor 理解检查或题目判分发生后才写入课程学习事实。

### 作答计数与知识点归属

| 来源 | 一次作答的依据 | 计入条件 |
|---|---|---|
| 练习、复习 | 同一 `practice_record.id` 只计一次 | 非空且已有正误判定；复习计划更新或自评质量本身不算作答 |
| Tutor 理解检查 | 每个 `tutor_session.id` 的首次检查 | 已保存检查答案、判定与判分时间；打开会话和展示讲解不计入 |
| 结构化变式训练 | 每个 `ai_variant_training.id` 的首次判分 | 训练已完成且存在非空答案、正误及判分时间；旧 Markdown 的确认完成不计入 |
| 试卷学习 | 每个 `exam_learning_answer.id` 的独立尝试 | 非空且已有正误判定；无判定的主观题自评不计入 |
| 考试 | 每个 `exam_answer.id` | 考试已交卷、非空且 `AUTO_GRADED` 或 `REVIEWED`；未答题、待批阅不计入；重复批阅读取当前结果，不新增次数 |
| 阶段测评 | 每个已完成测评的逐题快照 ID | 已完成测评、非空且有正误判定；恢复会话或重复提交不新增次数 |

以上时间下限为个人课程库关系的加入时间：练习/复习和试卷学习使用作答创建时间，考试使用
作答创建时间（批阅可晚于该时间），Tutor、变式和测评使用首次判分时间。加入前的历史作答
不会回填。当前错题和复习待办不受该下限约束，与已有课程入口一致。作答数与事件数不相加，
因此事件重放、辅助事件、同一复习记录的多种投影均不会改变作答数；Tutor 和变式训练分别
按自身来源表计数，不依赖二者旧 `AI_TUTOR` 幂等键的数值空间。

知识点接口参数为 `pageNum`（默认 1、至少 1）、`pageSize`（默认 10、1–50），返回常规
`Page` 的 `records/total/current/size`；越界页返回空列表。每行返回知识点 ID、名称、
`available`、`tutorAvailable` 和四项计数。当前目录节点即使没有事实也显示零值。

- 练习、复习、试卷学习、考试与变式训练按当前题目在本课程中的直接知识点关联归属；不向父目录
  递归累计。非测评题目须仍存在且为公共题或当前用户所有的私有题，其他用户的内容不进入统计。
- Tutor 检查直接归属会话保存的知识点 ID。阶段测评只使用逐题知识点快照的 ID，后续改题目关联
  不移动测评事实；快照缺失、空列表或无有效 ID 时归入“未关联知识点”，不从当前关联猜测补齐。
- 同一次作答关联多个知识点时，每个点各计一次；快照中的重复 ID 只计一次。各知识点行不能相加
  得出课程总数。同一知识点跨入口合并；仍在课程目录时使用当前名称，否则保留最近测评快照名称
  （无名称时使用知识点 ID），标记 `available=false`，不提供已失效的知识点深链。
- 测评快照受本人测评所有权隔离，原题或知识点删除后仍保留历史事实；历史测评详情和最近测评摘要
  继续使用各轮原始名称。错题与到期复习是当前题目待办，使用当前可访问题目及当前关联，
  未解决表示未删除且 `mastery_level != 2`；到期表示未删除且 `next_review_date` 不晚于服务端当天。
- 未关联题目的作答和待办进入 `knowledgePointId=null` 的独立行；无此类事实时不制造该行。
  `tutorAvailable` 只表示当前目录中存在已审查教学内容，不能视作已学习或掌握。

课程页联动已有知识点复习、错题和已审查 Tutor 入口；未关联或历史知识点可返回课程题库。
总计、待办首题和 Tutor 状态在数据库聚合，知识点列表在数据库分页，不把整门课程的作答或
事件明细传给前端，也不逐知识点发起查询。

### 阶段测评

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
只返回既有结果，不重复写学习事实。完成后可以开启新一轮，活动会话约束与回写事务见[测评存储](../database/learning-domain.md#阶段测评存储与事务)。
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

### Tutor 会话

Tutor 仅可打开已加入课程、属于该课程且审查状态为 `REVIEWED` 的内容；公开 `check` 只包含
既有公开题目标识 `id`、`prompt` 与 `options[].id/text`，不会返回正确选项或作答前的解释。
理解检查提交同时绑定用户、路径课程与会话，拒绝已退出课程、撤回内容及无效选项；由服务端首次判分并写入
可追加课程学习事件，重复提交返回既有结果。会话的 `checkAnswer` 与 `checkResult` 在作答前为空，
作答后分别返回首次选择与服务端判分反馈，页面刷新后恢复同一结果，不创建第二次作答。
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

会话响应的 `agentAvailable` 只在 AI 与模型工具能力都显式开启时为 `true`；否则学习页不展示追问入口，
原有教学和理解检查不受影响。Agent 每个问题必须先通过服务端工具读取该会话的已审查教学内容，可按需读取
启动会话时固化的学习证据；工具不接受用户、课程或知识点 ID，课程工具不返回正确选项和对应解释。
所有工具读取均复用会话权限及内容审查校验。

Agent 可以调用无参数的 `present_tutor_check`。未作答时，由服务端为成功的 ASSISTANT 消息附上
`actions: [{ "type": "CHECK" }]`；其他消息返回空数组。动作和回答一起保存，刷新对话仍可恢复。
页面的“开始理解检查”只将焦点移到本节表单，用户自己选择并提交；展示动作不写学习事件，模型也没有作答工具。
`read_tutor_check_result` 同样只接受空对象，返回 `UNANSWERED` 或 `ANSWERED` 及真实 `result`，
不采信用户聊天中的判分声明。已作答后再次请求检查只返回结果，不提供新的作答动作。
用户可显式请求 Tutor 根据作答继续指导，该操作才触发新的模型调用；刷新和判分本身不自动调用模型。

用户请求提示时，Agent 调用无参数 `request_tutor_hint`。未作答时工具由服务端根据当前运行完整已保存历史
分配下一级：第 1 级提供概念方向，第 2 级引导推理步骤，第 3 级使用课内相似情境。提示正文由模型依据公开
课节生成，不能选择或排除检查选项；真实提示质量仍需在线人工验收。
成功回复保存 `actions: [{ "type": "HINT", "level": 1 }]`（级别范围 1–3），表示该回复提供了提示，
不表示用户阅读、掌握或作答。每轮最多一个 HINT，可与 CHECK、PRACTICE、PLAN 共存；同轮重复调用不升级，失败不消费级别。
已达第 3 级返回 `LIMIT_REACHED`，已作答返回 `ANSWERED`，两者均不产生新的 HINT。
页面从保存的动作恢复进度，逐次点击才请求提示；新运行重新计算其提示进度。模型和客户端均不能指定级别。
生成提示期间用户在另一请求中完成作答时，完成写回前会再次校验，拒绝保存已过时的提示回复。

用户请求变式练习时，`recommend_tutor_practice` 只从本节知识点关联、管理员已批准的正式单选题中选取，
母题与发布题都必须公开、启用、未删除且同属本课程及当前知识点。按变式 ID 确定性选取首题，排除本人本会话
已经完成的题；没有候选返回 `UNAVAILABLE`，不临时生成或跳过审核。成功回复保存
`{ "type": "PRACTICE", "questionId": 51 }`，与 CHECK / HINT 各至多一个；推荐不写练习或学习事实。

用户点击“开始变式练习”才调用上述 GET。题目由服务端根据本人原会话、运行及 ASSISTANT 消息定位，
请求体不能指定题号。响应为 `{ "question": { "id", "content", "questionType", "options": [{ "label", "content" }] },
"result": null }`；作答前不返回正确标记、答案或解析。POST 验证真实选项，复用正式练习判分，返回
`result` 的 `recordId/questionId/userAnswer/correct/correctAnswer/analysis/score`；重复提交及刷新恢复首次题面、选项和结果；后续改题不重写本次作答快照。
推荐、读取与提交都重新核对成员关系、课节审核以及母题和发布题的可用性；权限或审核撤回后拒绝访问。

前端提交响应丢失时查询已保存结果，同步失败则阻止再次提交并提供同步入口；切换会话后丢弃迟到响应。
只有用户再次点击指导才调用模型。无参数 `read_tutor_practice_result` 使用服务端绑定的 run ID，读取当前运行
最新 PRACTICE 消息的首次结果（后面的普通聊天不影响选择）；尚无作答返回 `UNANSWERED`，不会回退到旧题结果。
已答仅向模型提供真实正误及解析，不采信聊天自述。页面只为最新推荐提供该指导入口。

用户点击“建议学习安排”时，Agent 可调用无参数 `propose_tutor_plan`，复用当前本人课程概览的建议目标：
未完成的已审查 Tutor、到期复习、错题、课程目录。过滤无有效 ID 的目标，同一道题去重后按原顺序取最多三步。
Tutor 目标要求知识点和教学内容均已审查，知识点撤回审核后也不再作为可确认的教学目标。
没有候选返回 `UNAVAILABLE`；有候选返回 `AVAILABLE` 和服务端 PLAN 动作，在本轮成功后随消息保存。
动作结构为 `{ "type": "PLAN", "steps": [{ "type": "TUTOR", "title": "...", "reason": "...", "knowledgePointId": 31 }] }`。
每步类型为 `TUTOR/COURSE_SEQUENCE/DUE_REVIEW/WRONG_QUESTION`；前两者只带 `knowledgePointId`，后两者只带 `questionId`，
模型不能指定目标、改写步骤或提供导航 URL。每种动作每轮至多一个，同轮相同计划去重、冲突计划拒绝保存。

计划 GET/confirm 返回 `{ "steps": [...], "confirmed": false, "confirmedAt": null, "available": true }`。
GET 保留提出时的标题、原因和目标，按目标类型及 ID 重新核对当前建议；确认前目标失效时拒绝首次确认。
用户点击“确认这份安排”才写确认记录，模型工具无确认权限；确认不写作答、错题、复习、完成或掌握事实，也不自动调用模型。
首次及并发重试返回同一持久化确认时间。已确认目标后来失效时仍返回历史安排与原确认时间，但 `available=false`，页面禁用旧入口。
页面刷新恢复服务端状态；确认响应丢失后先查询，查询也失败则要求显式同步；初始加载失败可重试。
确认成功后，每一步仍需用户点击才导航。原会话、运行、消息归属及当前会话权限在每次访问中复核。

无参数 `read_tutor_plan_state` 使用服务端绑定的 run ID，读取当前运行最新 PLAN 消息，跳过后来的普通聊天；
返回 `NONE`，或 `PROPOSED/CONFIRMED` 及上述 `plan`。聊天自称“已确认”不改变状态；确认也不代表学习完成。

运行状态为 `RUNNING`、`WAITING_USER` 或 `FAILED`。成功回答后停在 `WAITING_USER`，下一条消息领取同一运行；
同一运行并发提问会被拒绝，失败运行可以重试。每次执行最多保留 10 分钟的领取租约；
创建、继续、读取历史及成功写回均复核课程成员关系和当前内容审核状态；退出课程或撤回内容后拒绝继续访问。
进程中断后需等待租约到期；到期时读取状态显示为 `FAILED`，用户可通过继续提问接口重试，历史成功消息保留。
迟到的旧请求不能覆盖新执行的状态或追加回答；重试仍按新的实际调用消耗配额。
执行过程在模型和工具边界检查租约及中断，发现失效即停止后续调用。已经发出的同步外部请求仍受原超时控制，
不能保证立即终止；没有新增用户取消接口，已产生的模型用量仍保留在调用审计中。
学习页恢复期间暂停发送，网络失败保留对话标识并提供重试；运行中可手动刷新状态，
切换 Tutor 会话后忽略旧请求的迟到响应。
本地缺少运行标识或首次提问响应丢失时，通过 `latest` 查询该用户该会话最近创建的运行，
按运行 ID 倒序取一条，不跨用户、课程或会话查询；本地已有运行标识时仍优先恢复指定对话。
查询不发起模型调用、不自动重试提问；尚无运行返回成功和空结果，归属不匹配返回不存在。
响应只返回已成功保存的 USER / ASSISTANT 消息；内部工具消息
不作为对话正文公开。一次提问中的每次云模型调用分别消耗配额并写调用审计，但共享同一 Agent `runId`。

显式启用课程知识检索后，Agent 可调用 `search_course_knowledge`，只接受 `query`，不能指定其他用户、
课程或版本。检索范围来自当前会话和课程成员关系，仅返回人工已审核且索引就绪的知识片段。
查询 Embedding 同样计入当前用户配额并共享 Agent `runId`；未审核或未就绪时返回空资料。
成功回答末尾由服务端追加本轮实际检索的资料名称、版本和片段标识，随回答持久化；这不是模型生成的来源声明。
检索工具默认关闭，配置和审核入口见[知识快照配置](../../getting-started/configuration.md#知识快照)。

### 课程学习记忆

记忆接口只接受认证用户本人已加入且仍启用、未删除的课程，不接受客户端指定用户。
GET 返回 `{ "revision": 0, "explanationStyle": null, "goal": null }` 表示尚未保存；空字段始终返回 null。
PUT 请求同样包含三个字段：必填非负 `revision`，可空 `explanationStyle`（`STEP_BY_STEP`、`CONCISE`、
`EXAMPLES`），可空 `goal`（最多 500 字符，去除首尾空白）。至少填写一项；清空全部使用 DELETE。
保存和删除均返回最新快照；首次保存版本为 1，后续修改递增。版本过期返回业务错误 `1005`，要求重新读取后编辑。
删除清空目标和偏好，只保留归属及版本以拒绝旧页面覆盖；未保存时删除版本 0 返回原空快照。
请求结果不确定时，页面保留草稿并暂停写入，用户显式重新读取服务端内容后才可继续修改。

记忆跨本课程的 Tutor 会话使用，不从聊天自动提取，也不能由模型工具修改；它不写作答、学习事件或掌握度。
每次新提问开始时重新读取一次，作为独立的用户数据消息提供给模型，与真实学习证据区分。
纠正或删除影响后续开始的提问；已在运行的提问仍使用开始时的快照。删除记忆不会清除历史对话、学习记录，
也不会撤回已发送给模型的请求。系统策略要求模型不从旧对话恢复已删除设置；真实模型遵循情况仍需在线评测。

### Tutor 会话复盘

会话复盘只接受认证用户本人已加入且仍启用、未删除的课程；会话键还必须属于该用户和路径课程。`PUT` 请求的
`revision` 必须非负，`note` 为去除首尾空白后的非空文本，最多 500 字符。首次保存版本为 1；后续纠正和删除
递增版本。版本过期返回业务错误 `1005`，客户端必须先重新读取，不能用旧页面覆盖纠正或已删除的文字。

`GET /tutor-notes?page=1` 固定每页 5 条，返回 `{ "records", "total", "current", "size": 5 }`；列表排除
内容已删除的墓碑。单会话 GET 在尚无复盘时返回 `revision: 0`、`note: null`，删除后保留递增版本且
`note: null`，使旧版本不能复活。删除不会清理 Tutor 对话、理解检查或其他学习记录。

每条响应还带有 `source`：已审查课节和知识点仍可用时，才返回会话键关联的 `knowledgePointId`、课节标题、
会话开始时间，以及该会话当前真实理解检查的 `UNANSWERED`、`CORRECT` 或 `INCORRECT` 和作答时间。它不返回
选项或答案，也不复制判分为复盘快照。课节或知识点撤回后，仍可读取和删除本人文字，但不能再纠正；`source.available`
为 `false`，其余来源元数据均为 `null`。

每个新的 Agent 提问开始时，运行时只读取本课程最近 5 条仍可用、未删除的复盘。`note` 始终是用户自述，
`source` 才是服务端关联的本次理解检查事实；两者不能互相推断正确、完成或掌握。复盘当前只关联理解检查，
不包含变式练习或测评证据，也不是自动生成的摘要或完整学习历史。纠正、删除或撤回仅影响之后开始的提问，
不清理历史对话或撤回正在进行请求已经取得的快照。

### 受限课件

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

作答前的题目、练习和试卷响应只提供可展示的选择题或判断题选项，并移除正确性标记；填空题与
简答题的 `options` 返回空数组，避免泄露存放在选项正文中的参考答案。管理员编辑仍可读取完整
选项，提交后的答案和解析按各业务的判分、复盘规则返回。

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
