# 题目导师 API 契约（M3）

> 本页说明 M3 已实现的接口与数据契约。全部响应使用[统一外壳](conventions.md#统一响应)。题目和尝试的访问、内容版本、答案隔离及 `1004` 不暴露存在性的规则沿用[题目理解 API](question-understanding.md)。预置题与 M5 内容的当前审核状态见[项目状态](../../project/status.md)；待审核内容仅可由管理员预览。

## 内容与可用性

- 导师运行时只选择已存储、与本次尝试锁定的理解内容版本一致、人工审核为 `REVIEWED` 的内容，分级提示和下一步建议不调用云模型或在请求中生成文案。新生成的提示、场景描述和变式草稿必须以 `AI_GENERATED/PENDING` 入库；人工审核后才能公开。审核撤回即停止公开，不回退到旧版。
- 三级提示分别给概念方向、推理步骤、课内相似案例；均不判断或排除选项，也不泄露答案。每级文本有独立来源 `source: {kind, reviewStatus}`，`kind` 为 `CURATED` 或 `AI_GENERATED`；公开的 `reviewStatus` 恒为 `REVIEWED`。待审核内容只在管理员预览中可见，必须标为“AI 生成·待审核”，不能标成已审核或来源已核对。
- 可视化返回 `{title, spec, source}`，其中 `spec` 使用 `frontend/src/viz/engine/types.ts` 的 `SceneSpec`，包括 `scene`、`op`、场景参数以及可选 `pauseAt`；后端不执行场景。审核时须用正式模拟器验证场景、参数和检查点。`source` 描述可视化文案与描述本身，和题目来源 `source.verification` 分开。
- 正解响应保留 M2 的 `correctAnswer`、`explanation`、`viz`，增加 `explanationSource: {kind, reviewStatus}`；变式响应保留 M2 结构，其 `source` 继续描述变式的生成与审核。错项误区已有 `misconception.source`；新增的 `viz.source` 表示所用场景描述的来源与审核状态。导师建议的理由是服务端规则文案，返回 `reasonSource: {kind: "CURATED", reviewStatus: "REVIEWED"}`。
- 若本次内容版本缺任一级已审核提示，导师提示链整体不可用：`next` 不建议提示，正解保持 M2 的直接开放路径。这样待审核草稿不会让现有题目卡住。提示链可用后，错答者须依次取得三级提示才能开放正解；答对者可直接开放正解。

## 接口

| 方法与路径 | 请求 | 成功 `data` | 约束 |
|---|---|---|---|
| `GET /api/understanding/attempts/{attemptId}/tutor/next` | 无 | `{action, reason, reasonSource, state}` | 纯读取；按本次持久事实给唯一建议，不预占提示级别 |
| `POST /api/understanding/attempts/{attemptId}/tutor/hints/next` | `{requestId}`，小写规范 UUID | `{level, text, source, state}` | 服务端分配下一级；同一 `requestId` 的并发或重试返回同一提示，不跳级 |
| `POST /api/understanding/attempts/{attemptId}/tutor/viz-views` | `{context}` | `{context, viewed: true, state}` | `context` 为 `MISCONCEPTION` 或 `SOLUTION`；仅接受该尝试实际可见且存在的场景，重复记录幂等 |
| `POST /api/understanding/attempts/{attemptId}/solution/reveal` | 无请求体 | M2 字段及 `explanationSource`、可视化 `source` | 错答且已审核提示链可用时，三级提示均取得后才开放；其余沿用 M2 幂等语义 |
| `POST /api/understanding/variants/{variantId}/attempts` | M2 的 `answer`、可选 `answerTime`、`originAttemptId` | M2 字段；`assistance` 为 `UNAIDED` 或 `AIDED`；含 `review` | 服务端按原尝试提交前已用提示判定并持久化，客户端不得传入或覆盖 |

`state` 为 `{nextHintLevel, hintsUsed, misconceptionVizViewed, solutionVizViewed, solutionRevealed, variantAttempted}`。`nextHintLevel` 仅在完整已审核提示链可用、原题答错、正解尚未开放且仍有下一等级时为 `1`、`2` 或 `3`，其他情况为 `null`。`hintsUsed` 是已领取的最高等级，取值 `0..3`。两项可视化状态只表明客户端报告“已开始播放”，不宣称学习者看完或理解。`variantAttempted` 只表明本次原尝试已有变式提交。

`action` 是以下判别联合之一：

| `action.type` | 附加字段 | 返回条件与顺序 |
|---|---|---|
| `HINT` | `level` | 错答、提示链可用且尚有未领取的等级时优先；`level=state.nextHintLevel` |
| `PLAY_VIZ` | `context`、`viz` | 没有下一等级提示时，优先返回尚未记录播放且当前可见的错项场景；正解开放后可返回正解场景 |
| `REVEAL_SOLUTION` | 无 | 尚未开放正解、提示门禁已满足或提示链不可用时 |
| `TRY_VARIANT` | `variantId` | 已开放正解、有已批准且仍有效的变式、尚未提交变式时 |
| `DONE` | 无 | 无可继续的上述动作时 |

具体优先级：未开放正解时 `HINT → PLAY_VIZ(MISCONCEPTION) → REVEAL_SOLUTION`；已开放正解时 `PLAY_VIZ(SOLUTION) → TRY_VARIANT → DONE`。错项无场景时跳过播放；相同的正解与错项场景只建议播放一次。答对后直接按已开放正解的后续顺序处理。`reason` 用简短规则文案说明所建议动作，不能暗示尚未开放的答案。`next` 不从客户端传入选项、已看动画或提示等级；仅从本次作答及持久记录计算。

### 幂等、并发与错误

- 每次调用重新核对所有权、题目和课程访问、内容版本及审核状态。不可访问或内容撤回统一 `1004`；题目答案仍只在原题提交后显式开放的正解或已提交的变式结果中出现。
- 提示推进在同一原尝试上串行化。服务端记录三行以内的提示领取事实，唯一约束 `(attempt_id, level)`。为消除网络重试与用户下一次点击的歧义，`hints/next` 请求使用必填 `requestId`（UUID），同一 `requestId` 的重试返回同一结果；不同 `requestId` 才能推进一层。若两个不同请求同时到达，各按锁序最多推进一层，不超过 3；前端一次点击只生成一个 `requestId` 并在重试时复用。`requestId` 与结果一同持久化，唯一约束 `(attempt_id, request_id)`。
- `viz-views` 只记第一次开始播放时间，唯一约束 `(attempt_id, context)`；相同请求无附加学习事实。对不存在或尚未开放的场景返回 `1004`，非法 `context` 或 `requestId` 返回 `1001`。提示链可用但正解门禁未满足时，`reveal` 返回 `1005` 且不记开放事实。
- 变式提交的唯一约束与答案冲突规则沿用 M2。`AIDED` 表示本次原尝试在变式提交前领取过任一级提示；否则为 `UNAIDED`。原题误区、正解或动画的查看不单独改变这一标记。M4 只把 `UNAIDED` 的首次作答作为独立评分候选，不从请求或客户端状态推断。辅助记录与变式提交必须在同一原尝试的锁下取一致快照。

## 与既有契约的衔接

- 复用 `TutorAgentHintPolicy` 的三级上限与各级教学边界；其当前 `nextLevel(history)` 基于 Tutor 对话消息，题目理解没有该对话历史，A2 以数据库领取事实实现同一分级策略，不伪造对话消息。导师 `next` 使用确定性规则，不接入 Tutor 模型运行时。
- `GET /questions/{questionId}` 和原题提交仍兼容 M2。正解、变式响应只增字段；可视化增加 `source`。**行为变化**是错答且完整已审核提示链可用时，旧客户端直接调用 `reveal` 会收到 `1005`；M3 前端须先按提示链推进。变式 `assistance` 从恒定 `UNAIDED` 扩为 `UNAIDED | AIDED`，现有前端类型须同步。
- 新提交的尝试在创建时锁定提示链是否已审核；升级前已有尝试保持 M2 正解开放规则，避免因新内容加入而改变已开始的流程。已提交变式的 `assistance` 保持原持久值，不能事后重算。

## 管理员无副作用预览（B5）

`POST /api/understanding/preview/questions/{questionId}`，登录管理员调用。前端在 `/questions/:id` 切换预览后，
用本接口替代所有正式理解/导师接口，并显示“预览·含待审核内容”。预览进度仅保存在页面内存，退出或刷新即清空。

请求为 JSON 对象，首次传 `{}`；后续每次携带当前全部进度：

| 字段 | 类型与默认值 | 用法 |
|---|---|---|
| `answer` | 可选选项标签 | 原题预览作答，规则与正式 `answer` 相同；未传表示只读题干 |
| `hintLevel` | 整数 `0..3`，默认 `0` | 展示前 n 级提示；前端每次点下一提示将其加一。管理员可回退或重置预览 |
| `misconceptionVizViewed` / `solutionVizViewed` | 布尔，默认 `false` | 本地是否已播放相应场景，影响下一步建议 |
| `revealSolution` | 布尔，默认 `false` | 请求正解；错答且完整提示链可用时须 `hintLevel=3` |
| `variantAnswer` | 可选变式选项标签 | 正解已开放后预览变式判分；不生成正式变式尝试 |

不接受 `attemptId`、`originAttemptId`、`assistance` 或其他字段；每次从当前最新内容重新计算。
`answer` 缺省时不能携带非默认进度。预览允许启用的 `DRAFT` 题（仅此管理员入口放行），并选取最新 `PENDING/REVIEWED` 理解版本，撤回版本不可预览，
并保留正式题目、课程、知识点关联、指纹与来源有效性检查；管理员不能借此查看他人私有题。
提示只选择该版本的 `PENDING/REVIEWED` 行；不足三级时沿用正式无提示通路，`hintLevel` 必须为 `0`。
变式取首个有效 `PENDING/APPROVED` 单选变式，拒绝/撤回的内容不可预览。

成功 `data`：

- `preview: true`、`question`（与正式题干结构一致）。
- `attempt: null | {correct, answer, misconception, viz}`，未作答为 `null`；没有正式 `attemptId`。
- `hints: {level, text, source}[]`，按级别顺序，仅返回请求展示的等级。
- `solution: null | Solution`、`variant: null | Variant`，未请求正解时均为 `null`；无有效变式也为 `null`。
- `variantResult: null | {correct, correctAnswer, explanation, explanationSource, viz, assistance}`，
  未提交变式为 `null`；没有正式 `variantAttemptId`，`assistance` 依 `hintLevel` 投影为 `UNAIDED/AIDED`。
- `next: null | {action, reason, reasonSource, state}`，未作答为 `null`；其余复用正式导师建议规则。
  `TRY_VARIANT.variantId` 仅供显示，预览变式仍通过本接口提交。

误区、正解与其动画的 `source.reviewStatus` 继承理解版本；提示使用提示行自身状态；
变式 `PENDING` 映射为 `source.verification=PENDING` 和内容 `reviewStatus=PENDING`，`APPROVED` 映射为 `REVIEWED`。
题目来源 `question.source` 保持原有来源核验语义，规则理由仍是 `CURATED/REVIEWED`，不把待审核内容标成已审核。

错误：非管理员（包括题目不存在时）统一 `1004`；无权限、无有效内容或已撤回为 `1004`；
字段、类型、选项或进度组合无效为 `1001`；提示门禁未满足时请求正解为 `1005`。
本接口幂等计算，无预览会话或数据库写入；不记录提示、动画、作答、学习事件、错题、排期、FSRS、经验、课程事实，
也不读取或改变管理员的正式尝试。正式接口继续只接受正式 ID 和已审核内容。

## 作答后复习日期（B6）

原题与变式正式作答响应增加 `review: {nextReviewDate, counted}`，前端直接读取日期和本次是否计分；
空值、幂等重试和辅助变式语义见[题目理解 API](question-understanding.md#作答后的当前复习排期b6)。
预览响应中的 `attempt` / `variantResult` 没有 `review`。

## 题目追问（Phase 27，D1 契约）

追问独立于确定性三级提示，复用正式理解尝试、内容审核与所有权校验，不接入管理员预览。

| 方法与路径 | 请求 | 返回 |
|---|---|---|
| `GET /api/understanding/attempts/{attemptId}/ask` | 无 | 统一外壳 `{available, reason, questionRemaining, dailyRemaining, records}` |
| `POST /api/understanding/attempts/{attemptId}/ask/stream` | `{requestId, message}` | SSE，见下文 |
| `DELETE /api/understanding/attempts/{attemptId}/ask/{requestId}` | 无 | 统一外壳 `{stopped: true}`；停止幂等 |

`requestId` 为小写规范 UUID；`message` 去首尾空白后 1–1000 字符，不接受额外字段。
`available` 只表示配置可用，不表示尚有额度。未启用 AI、追问开关关闭、凭据/端点不完整或模型不是
`deepseek-flash` 时返回 `false`，`reason=DISABLED | UNCONFIGURED`，前端隐藏入口；可用时 reason 为 null。
理解页不依赖此请求成功。额度耗尽仍可读历史和显示剩余量；`dailyRemaining=null` 表示既有每日配额不限。
`questionRemaining` 默认每用户每题总计 5 次，跨尝试累计，可配置为正整数；每日沿用既有用户覆盖值或
`AI_DAILY_QUOTA`（默认 50，所有 AI 功能共享，按应用服务器日期计算）。

`records` 按时间升序返回本次尝试的记录，每条为
`{requestId, attemptId, message, content, status, errorCode, createdAt, suggestion}`。
`content` 为 Markdown，仅成功记录有正文；状态为 `RUNNING | SUCCEEDED | FAILED | CANCELLED`。
`errorCode` 成功/运行中为 null，失败为下表业务码。`suggestion` 为 null 或既有 next 的
`{action, reason, reasonSource}`，仅允许 `HINT` 或 `PLAY_VIZ`，不推进等级、不自动播放。
刷新后以 attemptId 读取同一次理解流程；其他用户、预览尝试或已撤回版本统一 1004。

SSE 的 JSON data：`content` 事件 `{content: "正文增量"}`；结束 `done`
`{requestId, questionRemaining, dailyRemaining, suggestion}`；失败 `error` 为 `{code, message}`，之后关闭。
业务校验失败发生在建流前，使用统一错误外壳；建流后的失败通过 error 事件。正文必须先完整缓冲并通过
服务端门禁才分片发送，因此首次正文可能在上游完成后到达。中断使用 DELETE 并 AbortController 关闭连接；
断连/超时也取消本次执行。停止不退回已预占次数或已登记的每日配额，不保存部分正文。

| 码 | 语义 |
|---|---|
| 1001 | 输入不合法 |
| 1004 | 不可访问的尝试或审核内容 |
| 1006 | 今日共享 AI 配额用完 |
| 1007 | 此用户同一道题已有运行中的追问，或重复 requestId（通过 GET 恢复） |
| 1010 | 追问不可用（未启用、关闭或未配置） |
| 1011 | 本题追问次数用完 |
| 1012 | 上游失败、协议或输出校验失败 |
| 1013 | 上游超时 |
| 1014 | 已停止或运行租约过期 |

按用户短事务串行预占次数，再释放数据库锁调用上游；同一题只允许一个有效运行，租约到期不接受迟到完成。
有效请求一经预占即计本题次数，包含失败和停止；输入、权限、配置、限次拒绝不计次。
每日配额只计统一 AI 准入已登记的调用；本题预占后若每日准入因并发被拒，保留失败记录与本题次数。
重复 requestId 不重复调用上游。记录和辅助事实在预占时同事务保存，与变式作答使用同一原尝试锁；
追问后的变式为 AIDED，不产生 FSRS 评分；原题提交时已有的独立评分不回写、不撤销。

门禁采用受限输出：模型只从服务端候选正文中选一条，最终逐字匹配，否则按 1012 拒绝。
答对或本次尝试已揭示正解之前，候选只有不涉及本题结论的安全推理引导语，不传正解或正确选项标记，
不直接输出模型自由文本；提示通过 suggestion 引导学习者显式领取。此限制避免单靠关键词扫描遗漏等价结论。
解锁后候选可包含本版本已审核讲解、所选错项误区及已审核提示，逐字引用，防止另起讲解口径。
模型输入不使用检索、外部工具、其他题目或未审核材料；历史仅用于显示，不把旧回答提升为已审核材料。
模型生成和选择不构成人工审核；记录的回答不标为“AI 生成·已审核”。

复用：`AiInvocationService → OpenAiProvider → ai-core/CloudChatModel` 的 OpenAI 兼容流式协议、
`Cancellation`、`AiCallGovernanceService/AiCallReservationService` 的每日配额与 usage/成本审计；
调用功能名 `QUESTION_ASK`。复用 `QuestionUnderstandingContent` 的内容门禁、正式作答事实和
`QuestionTutorService.next` 的动作格式与提示策略，不另建 AI 客户端或 Tutor Agent 工具循环。
配置沿用 `AI_*`，另加追问开关、每题次数和思考模式开关；DeepSeek Flash 单价按输入未命中
0.3 / 输出 1.2 USD 每百万 token 配置，缺 usage 时成本为空，不估算。
思考模式默认开启，关闭后仍执行同一门禁；只发正文，不发送 reasoning_content。
真实模型评测须价格获用户同意后单独运行；模拟结果不能决定真实教学效果或思考模式优劣。
