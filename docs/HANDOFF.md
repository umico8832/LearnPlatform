# AI 驱动的题目学习平台 - Agent 交接文档

本文档用于跨对话、跨 Agent、跨开发阶段交接项目状态。
新 Agent 接手时，必须先阅读本文件，再结合 `AGENTS.md`、`README.md`、`docs/ROADMAP.md`、`docs/CHANGELOG_AGENT.md` 和实际代码判断当前状态。涉及 Git、审查、重构或测试策略时，按 `AGENTS.md` 中的文档读取路由继续阅读对应子规则文档。

---

## 1. 项目基本信息

项目名称：AI 驱动的题目学习平台
项目定位：用于学习、刷题、错题复习、考试测评、题库管理和 AI 辅助学习的中大型 Web 项目。下一阶段将把题目从“题干 + 答案 + 解析”升级为可讲解、可交互、可诊断、可推荐、可共建的学习对象。
开发环境：macOS (本地 MySQL 8.0.43、JDK 26、Maven 3.9.16、Node v22)
技术栈：Vue 3 + TypeScript + Vite + Element Plus + Pinia | Spring Boot 3.2.5 + MyBatis-Plus + MySQL 8 + JWT + Knife4j | Docker Compose

---

## 2. 当前项目阶段

当前阶段：Phase 13 — AI 题目学习资产（代码实现 + 体验优化 + 文档补全 + 单元测试完成）

下一阶段主线：先把一道题讲透，把单题从“题干 + 答案 + 解析”升级为结构化 AI 学习对象，再考虑题库上传、OCR、爬虫和社区共建。

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
- [ ] Phase 13：AI 题目学习资产（进行中：结构化题目讲解 ✅、小白版解析 ✅、步骤拆解 ✅、错误选项分析 ✅、常见误区 ✅、变式题闭环 ✅、学习资产缓存 ✅、答错后 AI 讲解入口 ✅、AI 调用日志统一 ✅、错题本折叠优化 ✅、API/DB 文档补全 ✅、QuestionLearningAssetService 单元测试 ✅）

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
20. **集成测试**：4 个 Testcontainers 集成测试（ExamService 10 个 + PracticeService 16 个 + WrongQuestionService 16 个 + StatisticsService 10 个，共 52 个），标记 `@Tag("integration")`，需时通过 `mvn test -Dgroups=integration` 执行
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

# 后端
cd backend
set -a
source ../.env
set +a
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

---

## 5. 当前遗留问题

- 前端页面级组件（PracticeSessionView、HomeView 等）部分覆盖（LoginView/RegisterView/ProfileView 已完成），其余需要更多 mocking 工作
- tokensUsed 字段暂未从上游 API 提取，仅记录调用次数
- 管理端缺少按用户单独调整配额的能力（当前全局统一配额）
- 项目截图未制作（非阻塞演示素材）
- CI 流水线需推送到 GitHub 后才能实际触发验证
- 本地 JDK 25 + Testcontainers 1.20.1 Docker socket 兼容性问题，集成测试需在 CI（JDK 17）环境验证实际运行
- 缓存 TTL 参数目前硬编码在 RedisConfig 中，后续可抽为 application.yml 配置项
- Phase 13 P0 核心能力已实现（Round 68），体验优化与文档补全完成（Round 69），QuestionLearningAssetService 单元测试完成（Round 70，22 个测试）：question_ai_asset 表、QuestionLearningAssetService、6 种结构化 Prompt、流式生成+缓存、前端 QuestionLearningAsset.vue 组件、答错后 AI 讲解入口、AI 调用日志统一写入 ai_call_log 表、错题本中学习资产折叠展示、API/DB 文档已补全、后端全量 173 个测试通过
- 缓存 TTL 参数目前硬编码在 RedisConfig 中，后续可抽为 application.yml 配置项

---

## 6. 下一步建议任务

任务名称：Phase 13 — AI 题目学习资产（继续深化）

Phase 13 P0 核心能力已实现（Round 68），体验优化与文档补全完成（Round 69），QuestionLearningAssetService 单元测试完成（Round 70，22 个测试）。下一步建议：
- 管理端题目编辑页面添加清除 AI 学习资产缓存的入口
- AI 资产质量反馈机制（用户反馈讲解是否有帮助）
- 优化 PracticeSessionView 中 QuestionLearningAsset 的展示（考虑也使用 collapsible 模式）

当前不优先做：
- PDF / 图片 OCR
- 爬虫
- 用户上传题库自动入库
- AI 自动审核发布题目
- 复杂推荐系统

建议 commit message: `feat(ai): 统一 AI 调用日志 + 错题本学习资产折叠优化 + 补全 API/DB 文档`

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

工作方式：
1. 先根据 docs/HANDOFF.md 理解当前项目状态；
2. 再根据 docs/ROADMAP.md 判断当前阶段；
3. 再根据代码实际情况验证文档是否过时；
4. 如果用户有明确任务，以用户当前要求优先；如果用户只说“继续开发”，再自动选择下一步最高优先级任务；
5. 继续开发、测试、修复、更新文档；
6. 除非遇到重大方向、破坏性操作或需求冲突，否则不要频繁问我；
7. 每轮结束都要更新 docs/CHANGELOG_AGENT.md 和必要文档。

当前阶段：Phase 13 — AI 题目学习资产（P0 核心能力已完成第一轮实现）。

已完成模块：用户鉴权、课程知识点、题库、刷题判分（含填空简答增强）、错题本（含重练）、试卷考试、AI 功能（含流式输出与配额管理）、统计可视化（含个人学习报告）、质量提升、部署简历、收藏题练习、Excel 导入导出、学习计划、题目评论、多端适配、难度自适应、管理端用户管理、后端核心测试（151 个）、前端 Vitest 测试（187 个，21 个测试文件）、GitHub Actions CI。
下一阶段主线：Phase 13，把一道题从“题干 + 答案 + 解析”升级为结构化 AI 学习对象。P0 范围包括 AI 结构化题目讲解、小白版解析、步骤拆解、错误选项分析、常见误区、变式题闭环、题目 AI 学习资产缓存、答错后 AI 讲解入口。
当前不优先做：PDF / 图片 OCR、爬虫、用户上传题库自动入库、AI 自动审核发布题目、复杂推荐系统。
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
