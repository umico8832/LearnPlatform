# AI 固定评测

评测覆盖试卷 AI 辅导、题目学习资产和结构化变式题，使用
[固定案例](../../backend/src/test/resources/ai-evaluation/cases.json)与现有 JUnit/Maven 测试体系。
题目为人工编写的非隐私数据结构小样本，可根据题干逐步复算。测试中的模拟响应仅用于检查程序约束。

## 离线回归

需要 Java 21、Maven，以及已安装的项目依赖；不需要应用 API 密钥、数据库、Redis 或 Docker。
默认 `mvn test` 包含固定评测，在线测试默认跳过。单独运行：

```bash
cd backend
mvn -Dtest=AiEvaluationRegressionTest,AiEvaluationRunnerTest test
```

Maven 依赖已缓存时可增加 `-o`，完全离线运行。报告输出至 `backend/target/ai-evaluation/offline.json`，
每次运行覆盖同名报告，构建产物不提交。

离线案例调用真实 `ExamLearningAiService`、`AiQuestionAssistanceService`、
`QuestionLearningAssetService`、`QuestionAssetContextService`、`QuestionAssetPromptFactory`、
`AiVariantQuestionService` 和调用治理；Mapper、会话数据与上游响应使用夹具。
断言覆盖 Prompt 的材料边界及课程/最近作答事实、权限拒绝后的副作用、配额、变式题输出校验、
公开答案隔离、待审核状态、失败记录及完成事件。运行器测试另用内存 HTTP 响应验证真实
`OpenAiProvider` 的同步/流式调用、配置绑定、Prompt 指纹和 usage 传递。

## 真实模型评测

先通过正常环境注入应用配置：`AI_ENABLED=true`、`AI_API_KEY`、`AI_MODEL`，
可选 `AI_API_BASE_URL`、`AI_TIMEOUT`、`AI_MAX_TOKENS`、`AI_STREAM_INCLUDE_USAGE`。
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

默认在线只执行 `online: true` 的生成案例，每案一次调用；故障注入、权限和畸形响应案例属于离线回归。
未选择案例与离线专用案例在在线报告中显示 `SKIPPED`。缺少显式应用配置会使测试失败，
不会退回模拟响应。报告输出至 `backend/target/ai-evaluation/online.json`。

在线入口使用相同业务服务和调用治理，以及真实 Provider，但持久化与会话仍是隔离夹具，
不会发布题目或写入应用数据库。运行器关闭该次运行的应用日志，并仅报告异常类型和业务码，
避免上游异常体或连接地址泄露凭据。保留的输入和模型回复均来自固定案例，人工复核时按报告内容判断。

## 阅读结果

- `contractStatus`：自动约束的 `PASS` / `FAIL` / `SKIPPED`，`failures` 列出未满足的约束。
- `responseOrigin`：`SCRIPTED_FIXTURE` 或 `REAL_PROVIDER`。离线始终标记 `teachingQuality: NOT_EVALUATED`；
  在线始终标记 `NOT_REVIEWED`，不自动生成教学正确率。
- `promptHash`：实际发送的 system/user Prompt 的 SHA-256，与调用治理采用相同拼接规则。
  被权限阻断而没有发送 Prompt 时为 null。报告也保留实际 Prompt，供定位和复跑。
- `modelConfigVersion`：现有调用治理的配置指纹；报告同时记录模型、端点哈希、最大输出量、
  当前 Provider 固定温度、超时与流式 usage 开关。端点原文和密钥不写入报告。
- `usage`：Provider 实际返回的用量；模拟、缺失或未调用时为 null，不以字符数估算。
  `costUsd` 仅在现有成本计算器获得必要 usage 和配置价格时有值，否则为 null。
- `corpusHash`、`corpusVersion`、`generatedAt`：用于确认案例版本和运行时间。

逐案对照 `manualCriteria` 和实际回复，人工记录通过/失败/无法判断及具体证据。
可将报告复制为独立的人工审阅记录，附审阅人、日期、结论和问题片段；不要改写原始执行报告。
模型自评分、JSON 合法、关键词命中或模拟响应通过均不能证明教学内容正确。
真实模型输出可能变化，相同 Prompt 指纹也不保证重现同一回复。

## 增加案例

在 `questions` 中维护可复算的题干、选项、参考答案、解析、知识点与课程，
在 `cases` 中用 `question` 引用必要上下文，并增加唯一 `id`、实际业务 `route/type`、
场景、模拟响应、预期业务码、系统 Prompt 约束、输出约束和人工标准。
`PAPER` 使用服务端会话夹具；特定 `scenario` 的作答或权限条件由
[AiEvaluationFixture](../../backend/src/test/java/com/learnplatform/service/evaluation/AiEvaluationFixture.java)实现。
新增场景时应补充对应副作用断言，不能仅添加名称与文案。

优先加入已发现的失败及未覆盖边界，避免重复改写等价题目。修复缺陷先确认该案例因目标行为失败，
再修改实现并复验。纯 Prompt 哈希变化不等于有效失败证据；Prompt 内容约束保护的是策略存在，
策略能否抵抗注入或产生正确教学仍需在线结果与人工复核。

## 验证边界

当前试卷 AI 入口要求先作答，再提供完整解析或带答案的 Markdown 练习；没有专用的“只提示”模式。
题目解析资产允许直接讲解答案；结构化变式训练通过服务端公开 VO 隐藏答案和解析，首次提交后的
判分与管理员发布规则由原业务测试保护。评测不把 Prompt 承诺当作答案隔离措施。

变式题使用生产校验器；可视化案例额外检查 JSON 外层及元素类型，这只是评测断言，
不代表生产资产服务已具备完整可视化 schema 校验、前端安全渲染或教学状态转移证明。
权限案例验证服务级拒绝及调用顺序。资产请求在权限拒绝前不建立交互；试卷学习会话已通过校验、
但后续题目内容访问被拒绝时保留失败交互，且不调用 Provider、不写调用日志或成功事件。
会话所有权查询、数据库事务、HTTP 鉴权、浏览器和真实持久化
需由已有集成/E2E 测试验证。本入口不替代这些测试，也不评价确定性 Tutor、考试判分或学习效果。
