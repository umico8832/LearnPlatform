# AI 固定评测

评测覆盖试卷 AI 辅导、题目学习资产、结构化变式题和 Tutor Agent 工具循环、理解检查、分级提示与已审查变式训练动作，使用
[固定案例](../../backend/app/src/test/resources/ai-evaluation/cases.json)与现有 JUnit/Maven 测试体系。
题目为人工编写的非隐私数据结构小样本，可根据题干逐步复算。测试中的模拟响应仅用于检查程序约束。

## 离线回归

需要 Java 21、Maven，以及已安装的项目依赖；不需要应用 API 密钥、数据库、Redis 或 Docker。
默认 `mvn test` 包含固定评测，在线测试默认跳过。单独运行：

```bash
cd backend
mvn -Dtest=AiEvaluationRegressionTest,AiEvaluationRunnerTest test
```

Maven 依赖已缓存时可增加 `-o`，完全离线运行。报告输出至 `backend/app/target/ai-evaluation/offline.json`，
每次运行覆盖同名报告，构建产物不提交。

离线案例调用真实 `ExamLearningAiService`、`AiQuestionAssistanceService`、
`QuestionLearningAssetService`、`QuestionAssetContextService`、`QuestionAssetPromptFactory`、
`AiVariantQuestionService`、`TutorAgentRuntime` 和调用治理；Mapper、会话数据、Agent 工具结果与
上游响应使用夹具。
断言覆盖 Prompt 的材料边界及课程/最近作答事实、权限拒绝后的副作用、配额、变式题输出校验、
公开答案隔离、Agent 必选课程工具与按需学习证据工具、RAG 有结果/空结果/资料注入、稳定 run ID、逐调用审计、待审核状态、
失败记录及完成事件。运行器测试另用隔离 HTTP 服务验证真实
`OpenAiProvider` 的同步/流式调用、RAG 多轮工具消息、配置绑定、Prompt 指纹和 usage 聚合。

## 真实模型评测

先通过正常环境注入应用配置：`AI_ENABLED=true`、`AI_API_KEY`、`AI_MODEL`，
可选 `AI_API_BASE_URL`、`AI_TIMEOUT`、`AI_MAX_TOKENS`、`AI_STREAM_INCLUDE_USAGE`。
运行包含 Tutor Agent 的默认案例或指定 Agent 案例时还必须设置 `AI_TOOLS_SUPPORTED=true`；
只选择非 Agent 案例时不要求工具能力。
模型和兼容服务可替换，配置沿用 `AiConfig`。密钥由外部安全注入；不要写进命令参数、案例或报告，
也不要为评测读取 `.env`。Codex 账号使用额度不能代替应用模型 API 凭据或额度。

显式开启外部调用：

```bash
cd backend
AI_EVAL_ONLINE=true mvn -Dtest=AiEvaluationOnlineTest test
```

只评指定案例可设置逗号分隔的 ID：

```bash
AI_EVAL_ONLINE=true AI_EVAL_CASES=asset-stack-explanation,paper-wrong-attempt \
  mvn -Dtest=AiEvaluationOnlineTest test
```

只验证 Tutor Agent 的最小真实模型集合：

```bash
AI_EVAL_ONLINE=true AI_TOOLS_SUPPORTED=true \
  AI_EVAL_CASES=agent-reviewed-lesson,agent-injection-defense,agent-learning-evidence \
  mvn -Dtest=AiEvaluationOnlineTest test
```

默认在线只执行 `online: true` 的生成案例；普通案例每案一次调用，Agent 案例通常包含一次工具请求和
一次最终回答，因此至少调用两次，受运行时最多四次的硬上限约束。故障注入、权限和畸形响应案例属于离线回归。
未选择案例与离线专用案例在在线报告中显示 `SKIPPED`。缺少显式应用配置会使测试失败，
不会退回模拟响应。报告输出至 `backend/app/target/ai-evaluation/online.json`。

RAG 的最小真实生成模型集合：

```bash
AI_EVAL_ONLINE=true AI_TOOLS_SUPPORTED=true \
  AI_EVAL_CASES=agent-rag-found,agent-rag-empty,agent-rag-injection \
  mvn -Dtest=AiEvaluationOnlineTest test
```

这三个案例验证模型对检索工具、空结果和资料注入的处理，以及 Runtime 追加的来源标识。
检索内容始终来自固定的人工小样本，不调用 Embedding 或 Qdrant，也不要求启用应用检索配置。
因此在线通过只提供生成模型遵循工具与利用给定资料的验收输入，不能证明真实召回率或知识版本已获审核。

在线入口使用相同业务服务和调用治理，以及真实 Provider，但持久化与会话仍是隔离夹具，
不会发布题目或写入应用数据库。运行器关闭该次运行的应用日志，并仅报告异常类型和业务码，
避免上游异常体或连接地址泄露凭据。保留的输入和模型回复均来自固定案例，人工复核时按报告内容判断。

## 独立检索评测

[检索微语料](../../backend/app/src/test/resources/ai-evaluation/retrieval-cases.json)提供人工原创的中文片段、
查询和相关片段标注。独立入口调用真实 Embedding 与生产 Qdrant 适配器，在 Testcontainers 临时向量库中
建立索引、逐条查询并计算 Recall@3、倒数排名与命中率。它不连接应用数据库或日常 Qdrant，容器结束后回收。
审计 Mapper 使用隔离夹具；Embedding 协议、调用治理、usage 和成本计算仍走实际实现。

需要 Docker，并通过环境安全注入 `AI_EMBEDDING_ENABLED=true`、`AI_EMBEDDING_API_BASE_URL`、
`AI_EMBEDDING_API_KEY`、`AI_EMBEDDING_MODEL`、`AI_EMBEDDING_DIMENSIONS`；可选
`AI_EMBEDDING_TIMEOUT_SECONDS`（1–60）。不要求启用对话模型或应用的知识检索开关。
可按实际供应商价格提供 `AI_RAG_EVAL_INPUT_PRICE_PER_MILLION`（USD / 百万输入 token，非负）；
缺少模型价格或上游 usage 时成本为 null，不推测价格或用量。

```bash
cd backend
AI_RAG_EVAL_ONLINE=true ./mvnw -pl app -am \
  -Dtest=KnowledgeRetrievalEvaluationOnlineTest -DexcludedGroups= test
```

外部调用默认关闭，缺少配置会失败且不会改用模拟向量。一次完整运行调用一次批量文档 Embedding，
再为每个查询调用一次 Embedding。报告为 `backend/app/target/ai-evaluation/retrieval-online.json`，
记录语料哈希、模型/维度、端点哈希、逐查询排名与指标、向量查询耗时、逐调用审计和总 usage/成本。
上游失败保留审计和已完成查询，但标记 `executionStatus: FAILED`、`retrievalQuality: INCOMPLETE`，
不输出全量均值。真实云评测的 `SUCCEEDED` 仅表示流水线完成，不表示已达到检索质量门槛。

该微语料只用于可复跑的小样本比较；即使 Recall@3 为 1，也不能推断真实课程召回率、
生成答案忠实度或学习效果。需要人工复核标注，并在首批正式知识内容完成审核后另行验收课程检索。
不调用云的 `KnowledgeRetrievalEvaluationIntegrationTest` 使用脚本向量与真实临时 Qdrant 验证流水线，
其 `embeddingOrigin` 始终为 `SCRIPTED_FIXTURE`，检索质量和教学质量均为 `NOT_EVALUATED`。

## 阅读结果

- `contractStatus`：自动约束的 `PASS` / `FAIL` / `SKIPPED`，`failures` 列出未满足的约束。
- `responseOrigin`：`SCRIPTED_FIXTURE` 或 `REAL_PROVIDER`。离线始终标记 `teachingQuality: NOT_EVALUATED`；
  在线始终标记 `NOT_REVIEWED`，不自动生成教学正确率。
- `response` 保留模型原文，`publicOutput` 保留业务服务最终输出；Agent 输出为序列化的
  `{ "content": "...", "actions": [...] }`，保留检查操作、提示等级及变式题标识，不从回复文本推断动作。来源附录由 Runtime 生成，
  应与实际工具返回的版本和片段一致。附录表示本轮读取的资料，不保证回答每个结论都有证据支持。
- `toolTrace` 保留固定案例中的 call ID、实际工具名、参数、返回内容及检索 run ID，供核对教学结论和来源忠实度。
  提示工具包含运行时补入的服务端等级及引导，与送入模型的 TOOL 消息一致；未进入后续模型调用的工具保留原始结果。
  `retrievalOrigin: SYNTHETIC_TOOL_FIXTURE` 表明检索资料是夹具，在线也保持该标记；
  `retrievalQuality: NOT_EVALUATED` 表明没有测量真实向量召回质量。当前报告 schemaVersion 为 5。
- `promptHash`：消息列表序列化后的 SHA-256，直接读取本次调用审计中的指纹。
  被权限阻断而没有发送 Prompt 时为 null。报告也保留实际 Prompt，供定位和复跑。
- `modelConfigVersion`：现有调用治理的配置指纹；报告同时记录模型、端点哈希、最大输出量、
  当前配置温度、超时与流式 usage 开关。端点原文和密钥不写入报告。
- `modelCalls`、`callMetrics`：模型调用次数，以及每次调用的 run ID、结束原因、Prompt 指纹、耗时、usage
  和成本；Agent 的工具请求与最终回答分别记录，并应共享同一 run ID。
- `usage`：整个案例所有已审计调用的实际用量之和；任一调用缺失 usage 时为 null，不以字符数估算。
  `costUsd` 同样聚合整个案例，只有每次调用都能按配置价格计算时才有值。
- `corpusHash`、`corpusVersion`、`generatedAt`：用于确认案例版本和运行时间。

逐案对照 `manualCriteria` 和实际回复，人工记录通过/失败/无法判断及具体证据。
可将报告复制为独立的人工审阅记录，附审阅人、日期、结论和问题片段；不要改写原始执行报告。
模型自评分、JSON 合法、关键词命中或模拟响应通过均不能证明教学内容正确。
真实模型输出可能变化，相同 Prompt 指纹也不保证重现同一回复。

## 增加案例

在 `questions` 中维护可复算的题干、选项、参考答案、解析、知识点与课程，
在 `cases` 中用 `question` 引用必要上下文，并增加唯一 `id`、实际业务 `route/type`、
场景、模拟响应、预期业务码、系统 Prompt 约束、输出约束和人工标准。
RAG 案例的 `retrieval` 保存完整引用原文和内容哈希；`RAG_EMPTY` 使用空列表，
`RAG_INJECTION` 将注入内容放在检索片段正文中，避免仅测试用户输入注入。
`PAPER` 使用服务端会话夹具；特定 `scenario` 的作答或权限条件由
[AiEvaluationFixture](../../backend/app/src/test/java/com/learnplatform/service/evaluation/AiEvaluationFixture.java)实现。
新增场景时应补充对应副作用断言，不能仅添加名称与文案。

语料 v5 的变式案例覆盖已选题、无可用题、真实错误结果和聊天自称答对；核对题目动作与工具题号一致，
无候选时不造入口，结果工具保留实际 run ID 与服务端结果原文。固定夹具不测推荐内容质量或真实审批权限；
审批、并发首次判分、事务回滚和刷新恢复另由 MySQL 与隔离浏览器验证。

优先加入已发现的失败及未覆盖边界，避免重复改写等价题目。修复缺陷先确认该案例因目标行为失败，
再修改实现并复验。纯 Prompt 哈希变化不等于有效失败证据；Prompt 内容约束保护的是策略存在，
策略能否抵抗注入或产生正确教学仍需在线结果与人工复核。

## 验证边界

当前试卷 AI 入口要求先作答，再提供完整解析或带答案的 Markdown 练习；没有专用的“只提示”模式。
题目解析资产允许直接讲解答案；结构化变式训练通过服务端公开 VO 隐藏答案和解析，首次提交后的
判分与管理员发布规则由原业务测试保护。评测不把 Prompt 承诺当作答案隔离措施。

变式题使用生产校验器；可视化案例额外检查 JSON 外层及元素类型，这只是评测断言，
不代表生产资产服务已具备完整可视化 schema 校验、前端安全渲染或教学状态转移证明。
权限案例验证服务级拒绝及调用顺序。Tutor Agent 案例执行真实受限循环，但课程内容和学习证据由固定
工具夹具提供，不验证数据库所有权查询或内容审查表。资产请求在权限拒绝前不建立交互；试卷学习会话已通过校验、
但后续题目内容访问被拒绝时保留失败交互，且不调用 Provider、不写调用日志或成功事件。
会话所有权查询、数据库事务、HTTP 鉴权、浏览器和真实持久化
需由已有集成/E2E 测试验证。本入口不替代这些测试，也不评价确定性 Tutor、考试判分或学习效果。
