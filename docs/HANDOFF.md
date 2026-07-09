# AI 驱动的题目学习平台 - Agent 交接文档

本文档用于跨对话、跨 Agent、跨开发阶段交接项目状态。
新 Agent 接手时，必须先阅读本文件，再结合 `AGENTS.md`、`README.md`、`docs/ROADMAP.md`、`docs/CHANGELOG_AGENT.md` 和实际代码判断当前状态。涉及 Git、审查、重构或测试策略时，按 `AGENTS.md` 中的文档读取路由继续阅读对应子规则文档。

---

## 1. 项目基本信息

项目名称：AI 驱动的题目学习平台
项目定位：用于学习、刷题、错题复习、考试测评、题库管理和 AI 辅助学习的中大型 Web 项目。项目已完成题目学习资产、可视化讲解、学习诊断与内容治理底座；当前聚焦演示验收、AI 运营治理和学习效果闭环。
开发环境：macOS (本地 MySQL 8.0.43、JDK 26、Maven 3.9.16、Node v22)
技术栈：Vue 3 + TypeScript + Vite + Element Plus + Pinia | Spring Boot 3.2.5 + MyBatis-Plus + MySQL 8 + JWT + Knife4j | Docker Compose

---

## 2. 当前项目阶段

当前阶段：Phase 20 — 演示验收与 AI 运营治理 🚧 收尾中（独立配额、审计追踪、运营报告、异常提醒持久化与确认、顶部站内提醒入口、可选 webhook 站外通知、Prompt/模型配置追踪、正式题目 AI 复审建议缓存、疑似重复题检测、题目纠错反馈与处理留痕、题目版本记录、个人学习报告学习效果指标已完成，演示截图生成脚本与 11 张真实截图已完成，真实 Docker E2E 已复跑通过，本地 Testcontainers 集成测试已恢复，CI 实跑待完成）；Phase 21 — 前端信息架构与视觉体验优化 🚧 开发中（AppLayout 分组导航、全局样式变量、首页学习工作台样板、Practice/WrongQuestion/Review/ExamList/CourseList/CourseDetail/QuestionList/PracticeRecord/Favorite/AI Review/Profile/LearningPath 核心页整理已完成，学习报告学习效果面板 polish 已完成，管理端总览/Course/KnowledgePoint/User/Question/Exam/Submission/AI Usage 主要页面整理已完成，User/Question/Submission 长操作列收纳、批量操作和空状态已完成，顶部 AI 运营提醒入口已完成，已知 Element Plus radio `label` 旧 API 已清理）。Round 126 已新增 `skills/frontend-flow-test/SKILL.md`，用于规范低 token 的临时浏览器业务闭环验收；Round 132 已完成课程入口与详情页体验补齐；Round 133 已新增可复用 Playwright 演示截图脚本并产出真实截图；Round 134 已完成管理端表格长操作列收纳；Round 135 已完成管理端批量操作工具条和空状态；Round 136 已清理 Element Plus 单选按钮旧 API；Round 137 修复错题逻辑删除后再次答错的唯一键冲突，并确认 4 条真实 Playwright E2E 通过；Round 138 修复 Testcontainers 与 Docker Engine 29 兼容问题，并确认 53 个真实 MySQL 集成测试通过；Round 139 新增正式题目 AI 复审建议接口、缓存和管理端入口；Round 140 新增 AI 运营提醒持久化、当天去重和管理员确认入口；Round 141 新增管理员顶部栏 AI 运营提醒站内入口；Round 143 新增 AI 运营提醒可选 webhook 站外通知；Round 144 新增管理端疑似重复题检测；Round 145 新增题目纠错反馈闭环；Round 146 新增题目版本记录；Round 147 新增学习效果指标；Round 148 完成学习报告学习效果面板 polish；Round 149 完成刷题记录页练习复盘体验整理；Round 150 完成收藏题页重点题库体验整理；Round 151 完成 AI 复习建议页体验整理；Round 152 完成个人中心体验整理；Round 153 完成学习路径页体验整理。

下一阶段主线：Phase 20/21 进入推送后 CI 实跑确认；本地 `main` 仍领先远端，优先推送后确认 GitHub Actions 的后端、前端、Docker 和 Browser E2E。AI 调用日志已接入真实 token、按配置模型单价计算成本、用户独立配额、配额审计、请求追踪、Prompt/模型配置指纹、周期运营报告、异常提醒持久化与确认、正式题目复审建议缓存、管理端疑似重复题检测、题目纠错反馈处理留痕、题目版本记录，以及个人学习报告学习效果指标。OCR、爬虫、自动入库和复杂推荐仍非当前优先级。临时浏览器流程验收应先阅读 `skills/frontend-flow-test/SKILL.md`，只跑与当前任务相关的最小业务闭环，避免每次全量跑流程或输出大段 DOM。

阶段状态：
- [x] Phase 0：项目规划 ✅
- [x] Phase 1：项目骨架 ✅（已验证可运行）
- [x] Phase 2：用户与鉴权 ✅
- [x] Phase 3：课程与知识点 ✅
- [x] Phase 4：题库系统 ✅
- [x] Phase 5：刷题与判分 ✅
- [x] Phase 6：错题本 ✅
- [x] Phase 7：试卷与考试 ✅
- [x] Phase 8：AI 功能 ✅
- [x] Phase 9：统计可视化 ✅
- [x] Phase 10：质量提升 ✅（参数校验、接口文档、前端体验优化、日志规范化、SQL优化、安全检查）
- [x] Phase 11：部署与简历 ✅（项目截图为非阻塞演示素材）
- [x] Phase 12：体验增强迭代（✅ 基本完成：AI 题目助手、管理端统计、AI 流式输出、用户个人中心、错题重练、收藏题练习、Excel 导入导出、学习计划、AI 调用日志、核心业务可信度修复、后端核心服务测试、社区评论、多端适配、题目难度自适应、填空简答判分增强、个人学习报告、GitHub Actions CI、CommentController/AdminExam/AdminQuestion Controller 测试、CommentRequest @Max→@Size 修复、AdminUser/AdminCourse/AdminKnowledgePoint Controller 测试、前端 API 模块测试 7 个模块 62 个测试、Testcontainers 集成测试 ExamService 10 个 + PracticeService 16 个 + WrongQuestionService 16 个 + StatisticsService 10 个、AI 智能组卷、学习路径推荐、知识图谱可视化、Redis 缓存集成）
- [x] Phase 13：AI 题目学习资产 ✅（结构化题目讲解 ✅、小白版解析 ✅、步骤拆解 ✅、错误选项分析 ✅、常见误区 ✅、变式题闭环 ✅、学习资产缓存 ✅、答错后 AI 讲解入口 ✅、AI 调用日志统一 ✅、错题本折叠优化 ✅、API/DB 文档补全 ✅、QuestionLearningAssetService 单元测试 ✅、AI 资产质量反馈机制 ✅、PracticeSessionView 折叠模式 ✅、管理端清除 AI 缓存入口 ✅）
- [x] Phase 14：AI 可视化交互讲解 ✅（P0 文本可视化 ✅；P1 Mermaid 流程图 ✅：第 9 种 mermaid 元素类型 ✅、mermaid.js 动态 import ✅、异步 SVG 渲染 ✅、语法错误 fallback ✅、后端 Prompt 增强 ✅、单元测试 ✅；P2 代码执行动画 ✅：第 10 种 code_animation 元素类型 ✅、CodeAnimationViewer 组件 ✅、播放/暂停/步进控制 ✅、变量状态面板 ✅、暗色代码面板 ✅、Prompt 增强 ✅、单元测试 ✅；代码语法高亮 ✅：highlight.js 按需加载 18 种语言 ✅、逐行高亮方案 ✅、github-dark 主题 ✅、语言别名映射 ✅；P3 SQL 执行顺序可视化 ✅：第 11 种 sql_execution 元素类型 ✅、SqlExecutionViewer 组件 ✅、暗色 SQL 面板+子句高亮 ✅、中间结果预览 ✅、播放控制+速度调节 ✅、Prompt 增强 ✅、单元测试 ✅；P4 网络协议和操作系统过程可视化 ✅：第 12 种 network_protocol 元素类型 ✅、NetworkProtocolViewer 组件 ✅、时序图风格渲染 ✅、箭头方向+状态高亮 ✅；第 13 种 os_process 元素类型 ✅、OsProcessViewer 组件 ✅、可折叠步骤面板+状态表格 ✅、甘特图 ✅、Prompt 增强 ✅、单元测试 ✅）
- [ ] Phase 15：AI 学习画像与个性化推荐 🚧（P0 学习诊断与每日推荐 ✅；AI 个性化学习建议 ✅；相似题推荐 ✅；错题归因增强 ✅；单题错因分析 ✅：作答历史时间线、掌握趋势（IMPROVING/STAGNANT/DECLINING）、错误模式描述、连续错误检测、反复错题分析；待做：向量相似度增强推荐）
- [ ] Phase 16：题目投稿与 AI 题库生产 🚧（P0 题目投稿中心 ✅；P1 AI 质检 ✅；P1 AI 知识点标注 ✅；P1 AI 难度评估 ✅；P1 结果缓存 ✅；P1 一键填充审核意见 ✅；Markdown 导入 ✅；内容来源记录与复审机制 ✅：Flyway V8、5 种来源类型自动标记、复审记录表、定期复审机制、管理端来源筛选+复审弹窗；正式题目 AI 复审建议缓存 ✅）
- [x] Phase 17：间隔重复与智能复习 ✅ 基本完成（SM-2 算法、复习计划、错题同步、AI 复习建议、学习报告复习统计、25 个单元测试）
- [x] Phase 18：全局搜索与快捷导航 ✅ 基本完成（GlobalSearchService ✅；GlobalSearchController 4 个接口 ✅；前端 GlobalSearchDialog 组件 ✅；⌘K/Ctrl+K 快捷键 ✅；键盘导航 ✅；关键词高亮 ✅；移动端适配 ✅；搜索历史记录 ✅；热门搜索推荐 ✅；搜索结果缓存 @Cacheable ✅；搜索历史单条删除和全部清除 ✅；24 个单元测试 ✅）
- [x] Phase 19：AI 调用分析与成本控制 ✅ 基本完成（调用趋势、功能/模型分布、Top 用户、失败调用、真实 Tokens、平均耗时与按配置单价聚合成本）
- [ ] Phase 20：演示验收与 AI 运营治理 🚧（已完成：考试完整浏览器验收、考试页交互回归、关键业务浏览器 E2E、真实 token 与模型成本日志、管理员用户独立配额及审计、请求 traceId 追踪、Prompt/模型配置指纹、周期运营报告与异常提醒持久化确认、真实演示截图、错题复活唯一键冲突修复、管理端疑似重复题检测、题目纠错反馈处理留痕、题目版本记录、学习效果指标；待做：CI 实跑）
- [ ] Phase 21：前端信息架构与视觉体验优化 🚧（P0 壳层导航与全局样式 ✅；P1 首页学习工作台样板 ✅；P2 Practice/WrongQuestion/Review/ExamList/CourseList/CourseDetail/QuestionList/PracticeRecord/Favorite/AI Review/Profile/LearningPath ✅；P3 管理端总览/Course/KnowledgePoint/User/Question/Exam/Submission/AI Usage 主要页面整理 ✅；P4 User/Question/Submission 长操作列收纳 ✅；P5 管理端批量操作和空状态 ✅；P6 Element Plus radio 旧 API 清理 ✅；P7 学习报告学习效果面板 polish ✅；真实 Docker E2E 复跑 ✅；待做：CI 实跑）

---

## 3. 已完成内容

### 核心功能模块
1. **用户与鉴权**：JWT 登录注册、路由守卫、角色权限、用户管理
2. **课程与知识点**：CRUD + 树形结构 + 前端页面
3. **题库系统**：5 种题型 CRUD + 选项管理 + 知识点关联
4. **刷题与判分**：自动判分 + 答题记录 + 统计 + 填空多空+多可选答案判分 + 简答关键词匹配
5. **错题本**：自动收集 + 掌握程度管理 + 错题重练 + 统计
6. **试卷与考试**：手动组卷 + 考试答题(倒计时) + 自动判分 + 成绩查看
7. **AI 功能**：OpenAI 兼容 API + 题目解析 + 变式题 + 复习建议 + 知识点总结 + 流式输出
8. **统计可视化**：首页统计卡片 + ECharts 趋势图 + 雷达图 + 快捷入口 + 个人月度学习报告
9. **AI 题目助手**：刷题结果与错题本内直接生成 AI 深度解析和变式题
10. **管理端统计面板**：平台指标、题型分布、近 7 日活跃趋势、用户与试卷状态
11. **AI 流式输出**：题目解析、变式题与复习建议支持 JWT 认证的 POST SSE，前端实时渲染 Markdown
12. **部署与构建收尾**：Docker Compose 三服务健康启动、UTF-8 演示数据、可配置宿主机端口、前端按需加载
13. **用户个人中心**：修改昵称与密码，学习报告跳转入口
14. **体验增强**：错题重练、题目收藏、收藏题练习、Excel 导入导出、每日学习计划、题目讨论评论
15. **业务安全修复**：用户端答案隐藏、考试题目归属/重复/超时校验、前端 API 路径统一
16. **考试一致性**：提交行锁、超时状态保留、答题唯一约束、已发布试卷及引用题目不可变
17. **数据库迁移**：Flyway 基线与增量迁移，已有数据库可自动基线升级
18. **AI 用户级限流**：每日调用配额（默认 50 次/天），所有同步/流式接口受保护
19. **后端核心测试**：151 个后端测试通过（JWT、判分、考试校验、刷题、错题本、试卷状态、Controller MockMvc 集成测试覆盖 10 个 Controller）
20. **集成测试**：4 个 Testcontainers 集成测试（ExamService 10 个 + PracticeService 16 个 + WrongQuestionService 17 个 + StatisticsService 10 个，共 53 个），标记 `@Tag("integration")`，需时通过 `mvn test -DexcludedGroups= -Dgroups=integration` 执行
23. **前端 Vitest 测试**：187 个前端测试通过（21 个测试文件，覆盖 auth、user Store、路由守卫、基础组件、全部 13 个 API 模块及 3 个页面级组件），CI 已集成 `npm test`
20. **多端适配**：移动端抽屉导航、答题界面触摸友好、统计图表响应式
21. **题目难度自适应**：基于用户历史正确率的加权概率采样推荐
22. **GitHub Actions CI**：后端测试 + 前端构建 + Docker 镜像验证
23. **知识图谱可视化**：ECharts Graph 力导向/环形布局展示知识点关系，节点按掌握程度着色，支持课程筛选、详情抽屉和快捷跳转
24. **Redis 缓存集成**：统计数据接口缓存（7 个缓存区域独立 TTL），刷题/考试/错题本变更时自动清除缓存，CACHE_TYPE 环境变量控制 Redis/Simple 切换，Docker Compose 新增 Redis 服务
25. **Grafana Dashboard 自动导入**：Provisioning 配置自动加载 Prometheus 数据源和预置 Dashboard（20 个面板：应用概览、HTTP 请求、JVM 内存/线程/GC、系统资源、连接池），Docker Compose 启动即可用
26. **登录验证码**：基于 Java AWT 的数学验证码图片生成，ConcurrentHashMap 内存存储 + 5 分钟 TTL + 一次性使用，与 IP 级限流形成完整登录安全防护链
27. **结构化 JSON 日志**：logstash-logback-encoder 7.4 + logback-spring.xml 多环境日志配置（dev=可读文本+traceId, prod/docker=结构化 JSON），TraceIdFilter 为每个请求生成 8 位 traceId 写入 MDC 和响应头 X-Trace-Id，MDC 携带 traceId/clientIp/userId/httpMethod/httpUri/httpStatus/durationMs
28. **Grafana Loki 日志聚合**：Loki 2.9.4 服务 + Grafana 预配置 Loki 数据源 + 日志探索 Dashboard（6 个面板：日志量趋势、ERROR/WARN 趋势、日志流、级别分布、Top URI），Docker Compose 启动即可用
29. **前端信息架构样板**：AppLayout 已从长菜单改为学习中心、练习复习、考试测评、AI 与诊断、内容共建、管理后台分组导航；顶部栏展示页面语境与全局搜索；首页已重做为学习工作台，整合今日计划、下一步建议、关键指标、6 个任务入口和图表区。
30. **核心业务页体验整理**：PracticeView 改为“智能推荐 + 自选模式 + 练习方式说明”的任务面板；WrongQuestionView 统一统计、筛选和操作区；ReviewView 聚焦今日待复习、掌握进度和复习会话，并修复复习 API 重复 `/api` 前缀；ExamListView 区分可用试卷与考试记录；CourseListView/CourseDetailView 改为课程入口与知识点结构工作台，补齐课程搜索、摘要卡和查看题目动线；QuestionListView 改为题库浏览工作台，统一页头、筛选侧栏、结果摘要、题目卡片、收藏与讨论入口；PracticeRecordView 改为练习复盘页，补充当前页摘要、筛选重置、表格空状态和移动端折行；FavoriteView 改为重点题库页，补充收藏摘要、收藏练习配置、表格空状态和移动端折行。
31. **管理端体验样板**：`global.css` 已新增管理端通用页头、操作区、筛选区、统计卡、表格卡片、分页样式、行操作收纳、批量操作工具条和空状态样式；`AdminDashboard.vue`、`CourseManage.vue`、`KnowledgePointManage.vue`、`UserManage.vue`、`QuestionManage.vue`、`ExamManage.vue`、`SubmissionManage.vue`、`AiUsageView.vue` 已接入该基线，且 `UserManage.vue`、`QuestionManage.vue`、`SubmissionManage.vue` 已完成长操作列收纳、批量操作和空状态。
32. **题目纠错反馈闭环**：用户端题库卡片可提交题干、答案、解析、知识点或其他纠错反馈；管理端题目管理页可按状态查看、标记已处理或驳回，并记录处理人、处理说明和处理时间；该能力只做治理留痕，不自动修改题库。
33. **题目版本记录**：新增 `question_version` 表，题目创建、编辑、删除和正式复审会记录前后 JSON 快照；管理端题目列表可在“更多”中查看版本时间线，辅助追溯内容治理变化，不自动回滚题库。
34. **学习效果指标**：个人学习报告基于正确率变化、错题转化、复习掌握和活跃学习天数生成综合分、等级和建议摘要；前端已将正确率变化、错题转化率、复习掌握率和活跃学习天数整理为带进度条的解释型指标，帮助展示 AI 讲解、错题复习与间隔复习后的学习效果。

### 后端关键文件
- 统一响应：`R.java` + `ResultCode.java` + `BusinessException` + `GlobalExceptionHandler`
- 实体：User, Course, KnowledgePoint, Question, QuestionOption, QuestionKnowledgePoint, PracticeRecord, WrongQuestion, ExamPaper, ExamQuestion, ExamRecord, ExamAnswer
- 服务：AuthService, CourseService, KnowledgePointService, QuestionService, PracticeService, WrongQuestionService, ExamPaperService, ExamService, AiService, StatisticsService
- AI：AiConfig, AiAsyncConfig, AiProvider(同步/流式接口), OpenAiProvider, AiController

### 前端关键文件
- API 封装：auth(user store), course, knowledgePoint, question, practice, wrongQuestion, exam, ai, statistics
- 组件：AppLayout(侧边栏), MarkdownRenderer, AiQuestionAssistant, QuestionComment
- 页面：HomeView(统计面板), Login/Register, CourseList/Detail, QuestionList, Practice/Session/Records, WrongQuestion, ExamList/Take/Result, ReviewSuggestion, LearningReport, Favorite, Profile
- 管理端：AdminDashboard, CourseManage, KnowledgePointManage, QuestionManage, ExamManage, UserManage

---

## 4. 运行方式

### 本地开发
```bash
# MySQL
sudo /usr/local/mysql/support-files/mysql.server start

# 后端（在项目根目录加载环境变量，避免直接 source .env）
source scripts/load-env.sh .env
cd backend
mvn spring-boot:run

# 前端
cd frontend
npm run dev
```

### Docker
```bash
cp .env.example .env
docker compose up -d
```

> 若本机已有历史镜像，修改后端代码后请执行 `docker compose build backend && docker compose up -d backend`，确保运行镜像与源码同步。

---

## 5. 当前遗留问题

- 考试完整作答、提交与结果查看已完成真实 Docker 浏览器验收；项目已新增 `npm run screenshots:demo` 演示截图脚本，并已在 `docs/demo-screenshots/` 产出 11 张真实桌面截图。
- GitHub Actions 已配置，但仍需推送后确认后端、前端和 Docker Job 的实际结果。
- 已建立隔离的 `e2e` Profile，并以 Playwright 覆盖真实账号密码、验证码、JWT、课程浏览、“刷题答错→错题本→掌握度更新→重练”、“考试三题作答→提交→自动判分→结果详情”，以及“用户投稿→管理员通过→正式入库”闭环。Round 137 已在当前源码 Docker E2E 环境中复跑 4 条全部通过，并修复重复答错逻辑删除错题导致 `/api/practice/submit` 500 的问题。若普通 Docker 环境已启动，按 `docs/TESTING.md` 使用 `--force-recreate` 切换到 E2E Profile。
- 已从 OpenAI 兼容上游响应记录真实输入/输出/总 Token；流式用量默认通过 `stream_options.include_usage` 请求，无法支持该扩展的上游可设 `AI_STREAM_INCLUDE_USAGE=false`，对应日志保持空值而不估算。管理员需在 `ai.model-prices` 配置各模型的输入/输出 USD/百万 Token 单价，未配置价格或 token 不完整的调用成本保持空值。
- 配额调整已要求填写原因，并可查询管理员、前后值和时间审计历史；AI 调用日志已记录请求 `traceId`、Prompt 模板名、不可逆 Prompt SHA-256 指纹和模型配置版本指纹，未保存原始 Prompt 内容。
- 运营提醒已在报告生成时持久化为 `ai_usage_alert`，同类型、同周期天数、同一天生成的未确认提醒会复用同一条记录；管理员可在 AI 调用分析页或顶部栏提醒下拉中确认提醒。若配置 `AI_ALERT_WEBHOOK_ENABLED=true` 和 `AI_ALERT_WEBHOOK_URL`，新提醒创建后会发送一次结构化 webhook，复用提醒不重复发送；发送失败只记录日志，不影响报告生成。
- 本地 Testcontainers 已在 Docker Desktop / Docker Engine 29 环境恢复：Round 138 升级 Testcontainers 到 `1.21.4`，集成测试容器数据库与基线迁移对齐为 `learn_platform`，Flyway 使用容器 root 用户执行迁移；`mvn test -DexcludedGroups= -Dgroups=integration` 53 个真实 MySQL 集成测试通过。
- Redis 缓存 TTL 已迁移到 `application.yml` 的 `app.cache.ttl` 与环境变量配置。
- 前端暂未配置 lint 脚本或 ESLint；`npm run build` 仍有第三方 `@vueuse/core` pure annotation 和大 chunk 警告，但不阻断构建。
- Phase 15 的向量相似度推荐仍未完成，但不阻断当前主线；Phase 16 的正式题目复审建议缓存已在 Round 139 完成；题目版本记录已在 Round 146 完成；个人学习报告学习效果指标已在 Round 147 完成，学习效果面板视觉 polish 已在 Round 148 完成；刷题记录页练习复盘体验整理已在 Round 149 完成；收藏题页重点题库体验整理已在 Round 150 完成；AI 复习建议页体验整理已在 Round 151 完成；个人中心体验整理已在 Round 152 完成；学习路径页体验整理已在 Round 153 完成。

---

## 6. 下一步建议任务

任务名称：Phase 21 — 前端信息架构与视觉体验优化

用户反馈当前项目“功能太混乱、布局不好看、太粗糙”，希望下一轮新对话先做前端美化和体验整理。新 Agent 应先阅读 `skills/frontend-design/SKILL.md`（当前仓库存在），再按 `docs/ROADMAP.md` 的 Phase 21 计划执行。

建议下一轮做 Phase 21 / Phase 20 收尾：
1. 推送后确认 GitHub Actions 的后端、前端、Docker 和 Browser E2E 实跑结果。
2. 如需刷新演示素材，启动 Docker/E2E 环境后执行 `cd frontend && npm run screenshots:demo`，截图会覆盖 `docs/demo-screenshots/`。
3. 必要时刷新演示素材，或继续学习诊断、知识图谱等二级用户页的体验整理。

Round 122 已完成 Phase 21 第一轮：`AppLayout.vue` 分组导航、`global.css` 设计变量和 `HomeView.vue` 学习工作台样板；本轮已通过前端测试、构建和桌面/移动端视觉检查。Round 123 完成工程体检：前端 `npm ci`、`npm audit --audit-level=moderate`、`npm test -- --run`、`npm run build` 通过；后端 `mvn test` 360 passed、`mvn package -DskipTests` 通过；`docker compose config --quiet` 通过。Round 124 完成 Phase 21 P2：整理 Practice/WrongQuestion/Review/ExamList，修复复习 API 重复 `/api` 前缀，并通过前端测试、构建和桌面/移动端浏览器布局检查。Round 125 完成 QuestionListView 题库浏览页整理，并通过前端测试、构建和桌面/移动端浏览器布局检查。Round 127 完成管理端通用样式基线、AdminDashboard/CourseManage/UserManage 样板整理，并通过前端测试、构建和桌面/移动端布局检查。Round 128 修复全局搜索重复 `/api` 前缀、统计流式接口 Base URL、Actuator 默认暴露面，并将 Redis 缓存 TTL 迁移到配置。Round 129 完成 QuestionManage/ExamManage/SubmissionManage/AiUsageView 管理页主整理，并通过前端测试、构建和桌面/移动端 mock 布局检查。Round 130 完成 KnowledgePointManage 体验补齐，新增知识点摘要卡、树结构搜索和桌面/移动端 mock 布局检查。Round 132 完成 CourseList/CourseDetail 体验补齐，并验证从课程详情进入题库会携带 `courseId` 筛选。Round 133 新增 `frontend/scripts/capture-demo-screenshots.mjs` 和 `npm run screenshots:demo`，修复 simple cache 模式统计接口 500，调整 E2E profile 日志，并在真实 E2E 环境中生成 11 张演示截图。Round 134 完成 User/Question/Submission 管理页长操作列收纳，并通过前端测试与构建。Round 135 完成 User/Question/Submission 管理页批量操作工具条和空状态，并通过前端测试、构建和 Playwright mock 浏览器检查。Round 136 清理 LearningPathView 的 Element Plus radio 旧 API，并通过前端测试与构建。Round 137 修复错题逻辑删除后再次答错的唯一键冲突，更新集成测试命令配置，并通过后端 361 个测试与 4 条真实 Docker E2E。Round 138 修复 Testcontainers 与 Docker Engine 29 兼容问题、对齐真实迁移约束下的集成测试夹具，并通过后端 361 个默认测试与 53 个真实 MySQL 集成测试。Round 139 新增正式题目 AI 复审建议服务、`questionReviewSuggestion` 缓存和管理端复审弹窗入口，并通过后端 365 个默认测试、前端 207 个测试与前端构建。Round 140 新增 AI 运营提醒持久化与确认入口，并通过后端 367 个默认测试、前端 209 个测试与前端构建。Round 141 新增管理员顶部栏 AI 运营提醒站内入口，并通过前端 AI Usage API 测试与前端构建。

Round 144 已完成管理端疑似重复题检测：新增 `/api/admin/questions/duplicates` 只读接口、题目管理页“重复检测”抽屉、后端服务/Controller 测试和前端 API 契约测试；已通过后端 376 个默认测试、前端 210 个测试与前端构建。Round 145 已完成题目纠错反馈闭环：新增 `question_correction_report` 表、用户端提交接口、管理端列表和处理接口、题库页纠错弹窗、题目管理页纠错记录抽屉，并补充后端服务/Controller 测试和前端 API 契约测试；已通过后端 384 个默认测试、前端 213 个测试与前端构建。Round 146 已完成题目版本记录：新增 `question_version` 表、版本记录服务和管理端版本时间线抽屉，并补充后端服务/Controller 测试和前端 API 契约测试；已通过后端 387 个默认测试、前端 214 个测试与前端构建。Round 147 已完成个人学习报告学习效果指标：新增后端聚合字段、前端学习效果面板、后端服务/Controller 断言和前端 API 契约同步。Round 148 已完成学习报告学习效果面板 polish：正确率变化改为百分点展示，新增综合分构成说明和 4 个带进度条的拆解指标，并通过前端构建与桌面/移动端 mock 浏览器冒烟。Round 149 已完成刷题记录页 polish：新增练习复盘页头、当前页摘要卡、筛选重置、记录明细空状态和移动端折行，并通过前端构建。Round 150 已完成收藏题页 polish：新增重点题库页头、收藏摘要、收藏练习配置、表格空状态和移动端折行，并通过前端测试、构建与桌面/移动端 mock 浏览器冒烟。Round 151 已完成 AI 复习建议页 polish：新增 AI 学习教练台布局、生成范围说明、流式结果元信息、复制结果和移动端折行，并通过前端构建与桌面/移动端 mock 浏览器冒烟。Round 152 已完成个人中心 polish：新增账户与学习档案布局、身份档案、常用入口、设置卡片和移动端折行，并通过个人中心页面测试、前端构建与桌面/移动端 mock 浏览器冒烟。Round 153 已完成学习路径页 polish：新增学习路线工作台、优先处理卡片、路径分布筛选、课程概况、移动端路径卡片和 `courseId` 入参联动，并通过前端构建与桌面/移动端 mock 浏览器冒烟。

Round 105 已验证 Docker Redis 网络连接、8 个缓存/管理接口及前端代理的 AI 用量接口均返回 200；Round 106 已完成核心浏览器点击验收并修复刷题结果弹窗闪屏；Round 108 已完成考试全流程浏览器验收；Round 110 已接入首条 Playwright E2E；Round 111 已补齐刷题错题闭环 E2E；Round 112 已补齐考试作答、提交及结果详情 E2E；Round 113 已补齐投稿审核入库 E2E；Round 114 已接入上游真实 token 用量记录；Round 115 已完成按配置单价固化和聚合模型成本；Round 116 已完成管理员用户独立 AI 日配额；Round 117 已完成周期运营报告和实时异常提醒；Round 118 已完成配额调整审计与 AI 调用 traceId 追踪；Round 120 已完成 Prompt/模型配置指纹追踪；Round 122 已完成前端壳层导航、全局样式和首页学习工作台样板。

当前不优先做：
- 复杂推荐系统（包括向量相似度等）
- PDF / 图片 OCR
- 爬虫
- 用户上传题库自动入库
- AI 自动审核发布题目

建议 commit message: `feat(frontend): 优化课程入口页体验`

---

## 7. 新对话续接提示词

```
你现在接手一个长期开发中的全栈 Web 项目。

项目名称：AI 驱动的题目学习平台
开发环境：macOS（本地 MySQL 8.0、JDK 26、Maven 3.9.16、Node v22）
技术栈：Vue 3 + TypeScript + Vite + Element Plus + Pinia | Spring Boot 3.2.5 + MyBatis-Plus + MySQL 8 + JWT + Knife4j | Docker Compose

重要注意：本项目已移除 Lombok（JDK 26 兼容性问题），所有 Java 实体类需要手写 getter/setter/toString。

请先阅读以下文件：
1. AGENTS.md
2. docs/HANDOFF.md
3. docs/ROADMAP.md
4. docs/CHANGELOG_AGENT.md
5. README.md

按任务类型继续阅读：
- 新增功能、重构、结构归属不清：docs/ENGINEERING_RULES.md
- 测试策略或是否补测试：docs/TESTING.md
- 接口变化：docs/API_DESIGN.md
- 数据库变化：docs/DB_DESIGN.md
- 架构、目录或模块边界：docs/ARCHITECTURE.md
- commit、分支、回滚或 Git 历史：docs/AGENT_GIT_RULES.md
- 审查、复盘或判断能否 commit：docs/AGENT_REVIEW_CHECKLIST.md
- 演示或截图：docs/DEMO.md
- 简历表述：docs/RESUME.md
- AI 学习平台战略、AI 长期能力地图、AI 功能优先级：docs/AI_LEARNING_PLATFORM_STRATEGY.md
- 临时前端浏览器流程验收、模拟用户操作、页面点击检查：skills/frontend-flow-test/SKILL.md，并结合 docs/TESTING.md

工作方式：
1. 先根据 docs/HANDOFF.md 理解当前项目状态；
2. 再根据 docs/ROADMAP.md 判断当前阶段；
3. 再根据代码实际情况验证文档是否过时；
4. 如果用户有明确任务，以用户当前要求优先；如果用户只说“继续开发”，再自动选择下一步最高优先级任务；
5. 继续开发、测试、修复、更新文档；
6. 除非遇到重大方向、破坏性操作或需求冲突，否则不要频繁问我；
7. 每轮结束都要更新 docs/CHANGELOG_AGENT.md 和必要文档。

当前阶段：Phase 20“演示验收与 AI 运营治理”收尾中，Phase 21“前端信息架构与视觉体验优化”开发中。Round 105 已修复 Docker Redis 缓存与 AI 用量接口路径，Round 106 已完成核心浏览器点击验收并修复刷题结果弹窗的关闭动画闪屏，Round 108 已完成考试完整作答、提交和结果查看的真实 Docker 浏览器验收，Round 109 已补考试页交互回归测试，Round 110-113 已补 Playwright 登录/课程、刷题错题、考试判分及投稿审核入库闭环 E2E，Round 114 已记录上游真实 token，Round 115 已按管理员配置的模型单价固化并聚合成本，Round 116 已支持管理员给单用户设置、取消或解除 AI 日配额，Round 118 已补齐配额原因/历史审计与调用 traceId 追踪，Round 120 已补齐 Prompt 模板名、不可逆 Prompt 指纹和模型配置版本指纹，Round 122 已完成 AppLayout 分组导航、global.css 设计变量和 HomeView 学习工作台样板，Round 124 已完成 Practice/WrongQuestion/Review/ExamList 核心页整理，Round 125 已完成 QuestionListView 题库浏览页整理，Round 126 已新增 frontend-flow-test skill 规范临时浏览器流程验收，Round 127 已完成管理端总览/Course/User 样板整理，Round 129 已完成 QuestionManage/ExamManage/SubmissionManage/AiUsageView 管理页主整理，Round 130 已完成 KnowledgePointManage 体验补齐，Round 132 已完成 CourseList/CourseDetail 课程入口页整理，Round 134 已完成 User/Question/Submission 管理页长操作列收纳，Round 135 已完成 User/Question/Submission 管理页批量操作和空状态，Round 136 已清理 Element Plus 单选按钮旧 API，Round 137 已修复错题逻辑删除后再次答错的唯一键冲突并复跑真实 Docker E2E 通过，Round 138 已修复 Testcontainers 与 Docker Engine 29 兼容问题并确认 53 个真实 MySQL 集成测试通过，Round 139 已完成正式题目 AI 复审建议缓存和管理端入口，Round 140 已完成 AI 运营提醒持久化与确认入口，Round 141 已完成管理员顶部栏 AI 运营提醒站内入口，Round 145 已完成题目纠错反馈闭环，Round 146 已完成题目版本记录，Round 147 已完成个人学习报告学习效果指标，Round 148 已完成学习报告学习效果面板 polish，Round 149 已完成刷题记录页练习复盘体验整理，Round 150 已完成收藏题页重点题库体验整理，Round 151 已完成 AI 复习建议页体验整理，Round 152 已完成个人中心体验整理，Round 153 已完成学习路径页体验整理。

已完成模块：用户鉴权、课程知识点、题库、刷题判分（含填空简答增强）、错题本（含重练）、试卷考试、AI 功能（含流式输出、真实 token/成本日志、按用户独立的日配额及原因审计、调用 traceId、Prompt/模型配置追踪、周期运营报告/异常提醒持久化确认、正式题目 AI 复审建议缓存）、统计可视化（含个人学习报告和学习效果指标）、质量提升、部署简历、收藏题练习、Excel/Markdown 导入导出、学习计划、题目评论、多端适配、难度自适应、管理端用户管理、管理端疑似重复题检测、题目纠错反馈处理留痕、题目版本记录、Phase 13 AI 题目学习资产、Phase 14 全部可视化讲解（13 种元素类型）、Phase 15 的学习诊断/建议/规则相似题与错因分析、Phase 16 的投稿/AI 质检/标注/难度评估/来源追踪与复审机制、Phase 17 间隔重复复习系统（SM-2 算法）、Phase 18 全局搜索与快捷导航（核心功能）、Phase 21 用户端与管理端主要页面整理、刷题记录页复盘体验、收藏题页重点题库体验、AI 复习建议页体验、个人中心账户与学习档案体验、学习路径路线工作台体验、管理端高频列表批量操作和空状态、后端测试 387 个、前端 Vitest 测试（最新已验证 214 个）、GitHub Actions CI 配置。
下一步建议：
1. 推送至 GitHub 后确认 Actions CI 实际运行结果。
2. 必要时刷新 `docs/demo-screenshots/` 演示截图。
3. 之后继续后续缓存/运营治理细节。

当前验收基线：后端 `mvn test` 387 passed，`mvn test -DexcludedGroups= -Dgroups=integration` 53 passed，`mvn package -DskipTests` 成功；前端 `npm ci`、`npm audit --audit-level=moderate`、`npm test -- --run` 214 passed、`npm run build` 成功；`docker compose config --quiet` 成功。Round 122 已用 Playwright 检查桌面 1440x980 和移动端 390x844 首页无横向溢出，移动端抽屉导航展开正常。Round 124 已用本地 Playwright 检查 `/practice`、`/wrong-questions`、`/review`、`/exams` 桌面 1440x980 与移动端 390x844 均无横向溢出，并确认 `/review` 请求路径为 `/api/review/stats`。Round 125 已用本地 Playwright 检查 `/questions` 桌面 1440x980 与移动端 390x844 均无横向溢出。Round 129 已用本地 Playwright mock 数据检查 `/admin/questions`、`/admin/exams`、`/admin/submissions`、`/admin/ai-usage` 在桌面 1440x980 与移动端 390x844 均无横向溢出。Round 130 已用本地 Playwright mock 数据检查 `/admin/knowledge-points?courseId=1&courseName=Java%20%E5%90%8E%E7%AB%AF` 在桌面 1440x980 与移动端 390x844 均无横向溢出，并验证知识点树搜索可收窄结果。Round 132 已用本地 Playwright mock 数据检查 `/courses`、`/courses/1` 在桌面 1440x980 与移动端 390x844 均无横向溢出，并验证从课程详情进入题库会请求 `/api/questions?pageNum=1&pageSize=10&courseId=1`。Round 133 已用 E2E Docker 环境生成 11 张真实演示截图。Round 135 已用本地 Playwright mock 数据检查 `/admin/users`、`/admin/questions`、`/admin/submissions` 批量工具条、清空选择、题目空状态和桌面 1440x980 无横向溢出。Round 136 已确认 `frontend/src` 中不再存在 `el-radio` / `el-radio-button` 旧 `label` 取值写法。Round 137 已在当前源码 Docker E2E 环境中重建 backend/frontend 并运行 `npm run test:e2e`，4 条真实业务闭环通过；Round 138 已确认本机 Testcontainers 集成测试可连接 Docker Desktop 并通过 53 个真实 MySQL 用例。Round 150 已用本地 Playwright mock 数据检查 `/favorites` 桌面 1440x980 与移动端 390x844 均无页面级横向溢出，收藏摘要与关键操作可见。Round 151 已用本地 Playwright mock 数据检查 `/ai/review` 桌面 1440x980 与移动端 390x844 均无页面级横向溢出，页面标题、当前范围和 mock 流式结果可见。Round 152 已用本地 Playwright mock 当前用户接口检查 `/profile` 桌面 1440x980 与移动端 390x844 均无页面级横向溢出，学习报告按钮、保存修改按钮和密码安全提示可见。Round 153 已用本地 Playwright mock 数据检查 `/learning-path?courseId=2` 桌面 1440x980 与移动端 390x844 均无页面级横向溢出，学习路径接口携带 `courseId=2`，页面标题、优先知识点和路径计数可见。核心用户与管理员页面已通过真实浏览器点击验收；后续临时浏览器流程验收应按 `skills/frontend-flow-test/SKILL.md` 只跑最小相关闭环。
当前不优先做：PDF / 图片 OCR、爬虫、用户上传题库自动入库、AI 自动审核发布题目、复杂推荐系统（包括向量推荐）。
后续扩展方向：见 docs/AI_LEARNING_PLATFORM_STRATEGY.md、docs/FUTURE.md 和 docs/TESTING.md；测试按业务风险补充。

本地运行方式：
- MySQL: sudo /usr/local/mysql/support-files/mysql.server start
- 后端: cd backend && mvn spring-boot:run
- 前端: cd frontend && npm run dev
```

---

## 8. 交接注意事项

- 不要依赖旧对话记忆
- 不要把 AGENTS.md 当进度表
- 不要把所有协作细节继续堆进 AGENTS.md；优先按 AGENTS.md 的读取路由维护子规则文档
- 不要清空 docs/CHANGELOG_AGENT.md
- 不要覆盖真实 .env
- 不要提交真实 API Key
- 先检查代码，再相信文档
- 发现文档与代码不一致时，以代码为准，并修正文档
- **不要使用 Lombok**，手写 getter/setter/toString（JDK 26 兼容性）
