# AI 驱动的题目学习平台 - 开发路线图

## 阶段总览

| 阶段 | 名称 | 状态 | 预计工作量 | 说明 |
|:----:|------|:----:|-----------|------|
| 0 | 项目规划 | ✅ 已完成 | 0.5天 | 文档产出 |
| 1 | 项目骨架 | ✅ 已完成 | 1天 | 前后端骨架 + Docker |
| 2 | 用户与鉴权 | ✅ 已完成 | 1.5天 | 注册登录 + JWT |
| 3 | 课程与知识点 | ✅ 已完成 | 1天 | CRUD + 前端页面 |
| 4 | 题库系统 | ✅ 已完成 | 2天 | 题目 CRUD + 前端 |
| 5 | 刷题与判分 | ✅ 已完成 | 2天 | 答题 + 判分 + 记录 |
| 6 | 错题本 | ✅ 已完成 | 1.5天 | 错题收集 + 重练 |
| 7 | 试卷与考试 | ✅ 已完成 | 2.5天 | 组卷 + 考试 + 成绩 |
| 8 | AI 功能 | ✅ 已完成 | 2天 | AI 解析 + 复习建议 |
| 9 | 统计可视化 | ✅ 已完成 | 1.5天 | 图表 + 面板 |
| 10 | 质量提升 | ✅ 已完成 | 2天 | 重构 + 校验 + 文档 |
| 11 | 部署与简历 | ✅ 已完成 | 1.5天 | Docker + README + 简历材料，截图为非阻塞素材 |
| 12 | 体验增强迭代 | ✅ 基本完成 | 持续迭代 | 已完成 AI 流式输出、收藏、导入导出、学习计划、评论、监控等增强 |
| 13 | AI 题目学习资产 | ✅ 已完成 | Round 68-72 | 把一道题从"题干 + 答案 + 解析"升级为结构化 AI 学习对象，含反馈机制、单元测试、文档补全 |
| 14 | AI 可视化交互讲解 | ✅ 已完成 | Round 73-74,88-92 | 面向算法、代码、SQL、数据结构、网络协议、操作系统等过程类题目做可视化讲解（P0 文本可视化 ✅ + P1 Mermaid 流程图 ✅ + P2 代码执行动画 ✅ + 代码语法高亮 ✅ + SQL 执行顺序可视化 ✅ + 网络协议时序图 ✅ + 操作系统过程可视化 ✅），共 13 种可视化元素类型 |
| 15 | AI 学习画像与个性化推荐 | 🚧 开发中 | Round 75-79 | 基于错题、答题记录和 AI 学习行为做诊断与推荐（P0 学习诊断与每日推荐 ✅ + AI 个性化学习建议 ✅ + 相似题推荐 ✅ + 错题归因分析增强 ✅） |
| 16 | 题目投稿与 AI 题库生产 | 🚧 开发中 | Round 82-87 | 投稿、质检、标注、难度评估、结果缓存、一键填充审核意见（P0 投稿中心 ✅ + P1 AI 质检 ✅ + P1 AI 知识点标注 ✅ + P1 AI 难度评估 ✅ + P1 结果缓存 ✅ + P1 一键填充审核意见 ✅） |
| 17 | 间隔重复与智能复习 | ✅ 基本完成 | Round 94-97 | 基于 SM-2 算法的间隔重复系统（Flyway V9 ✅ + SpacedRepetitionService ✅ + ReviewController 10 个接口 ✅ + 前端 ReviewView ✅ + 错题自动复习调度 ✅ + 循环依赖消除 ✅ + AI 复习建议整合 ✅ + 复习统计整合到学习报告 ✅ + 25 个单元测试 ✅） |
| 18 | 全局搜索与快捷导航 | ✅ 基本完成 | Round 98-99 | 跨题目/课程/知识点的全局搜索（GlobalSearchService ✅ + GlobalSearchController 4 个接口 ✅ + 前端 GlobalSearchDialog 组件 ✅ + ⌘K/Ctrl+K 快捷键 ✅ + 键盘导航 ✅ + 关键词高亮 ✅ + 移动端适配 ✅ + 搜索历史记录 ✅ + 热门搜索推荐 ✅ + 搜索结果缓存 @Cacheable ✅ + 24 个单元测试 ✅） |
| 19 | AI 调用分析与成本控制 | ✅ 基本完成 | Round 100 | 管理端 AI 调用总览（趋势、功能/模型分布、Top 用户、失败调用、Tokens 与平均耗时） |
| 20 | 演示验收与 AI 运营治理 | 🚧 开发中 | 2-3 个迭代 | 已覆盖登录、课程、刷题错题闭环、考试及投稿审核入库的真实浏览器 E2E；继续完成演示截图、CI 实跑，再补齐 AI 配额、真实成本和调用报告 |

**Phase 0-12 预计总工期**：约 17-20 天

**当前阶段：Phase 19 已基本完成，Phase 20 已规划。先做演示验收与质量收尾；功能迭代先做 AI 运营治理，再做内容质量和学习效果闭环。向量推荐、OCR、爬虫和自动入库不在近期主线。**

---

## Phase 0：项目规划 ✅

### 目标
完成项目前期文档，明确技术方案和开发计划。

### 产出物
- [x] docs/PRD.md - 产品需求文档
- [x] docs/ARCHITECTURE.md - 架构设计文档
- [x] docs/DB_DESIGN.md - 数据库设计文档
- [x] docs/ROADMAP.md - 开发路线图
- [x] docs/RESUME.md - 简历项目描述
- [x] docs/API_DESIGN.md - 接口设计文档
- [x] .gitignore - Git 忽略规则
- [x] .env.example - 环境变量示例
- [x] README.md - 项目说明

---

## Phase 1：项目骨架 ✅

### 目标
创建可运行的前后端项目骨架，配置基础架构。

### 后端任务
- [x] 创建 Spring Boot 3 项目（Maven）
- [x] 配置 pom.xml 依赖（MyBatis-Plus、Spring Security、JWT、Knife4j、Validation、Lombok）
- [x] 配置 application.yml（数据库、JWT、AI、Knife4j）
- [x] 创建项目目录结构（config、common、entity、mapper、service、controller、dto、security、enums）
- [x] 实现统一响应体 R<T>
- [x] 实现响应码枚举 ResultCode
- [x] 实现业务异常 BusinessException
- [x] 实现全局异常处理器 GlobalExceptionHandler
- [x] 配置 MyBatis-Plus（分页插件、自动填充、逻辑删除）
- [x] 配置 Knife4j 接口文档
- [x] 配置 CORS 跨域
- [x] 创建 health check 接口
- [x] 创建 Dockerfile

### 前端任务
- [x] 使用 Vite 创建 Vue 3 + TypeScript 项目
- [x] 安装依赖（Element Plus、Pinia、Vue Router、Axios、ECharts）
- [x] 配置 Vite（代理、别名）
- [x] 创建目录结构（api、assets、components、router、stores、types、utils、views）
- [x] 配置 Axios 实例（基础 URL、拦截器、Token 注入）
- [x] 配置 Vue Router（路由守卫骨架）
- [x] 配置 Pinia Store 骨架
- [x] 创建基础布局组件（Header、Sidebar）
- [x] 创建 404 页面
- [x] 创建 Dockerfile 和 nginx.conf

### 部署任务
- [x] 创建 docker-compose.yml（MySQL + Backend + Frontend）
- [x] 创建 .env.example
- [x] 创建 MySQL 初始化 SQL（schema.sql）
- [ ] 更新 README.md（启动说明）

### 验收标准
1. `docker-compose up` 可以一键启动所有服务
2. 后端访问 `http://localhost:8080/doc.html` 可以看到 Knife4j 接口文档
3. 前端访问 `http://localhost:5173` 可以看到基础页面
4. 健康检查接口 `GET /api/public/health` 返回正常
5. 前端能成功请求后端接口（通过 Vite 代理）

---

## Phase 2：用户与鉴权 ✅

### 目标
实现用户注册、登录、JWT 鉴权，前端完成登录注册页面。

### 后端任务
- [x] 创建 User 实体类
- [x] 创建 UserMapper
- [x] 实现 JwtTokenProvider（生成、解析、验证 Token）
- [x] 实现 JwtAuthenticationFilter
- [x] 配置 SecurityConfig（权限规则）
- [x] 实现 UserDetailsServiceImpl
- [x] 实现 AuthService（注册、登录）
- [ ] 实现 UserService（用户信息查询、修改）（后期补充）
- [x] 实现 AuthController（注册、登录、当前用户）
- [ ] 实现 UserController（修改昵称、头像）（后期补充）
- [x] DTO：LoginRequest、RegisterRequest、LoginResponse、UserVO
- [x] 参数校验（@NotBlank、@Size 等）

### 前端任务
- [x] 实现 auth.ts（Token 存储、获取、清除）
- [x] 实现 request.ts（Axios 拦截器：401 跳转登录）
- [x] 实现 user store（登录状态、用户信息）
- [x] 实现 API：user.ts（login、register、getCurrentUser、updateProfile）
- [x] 创建 LoginView.vue（登录表单）
- [x] 创建 RegisterView.vue（注册表单）
- [x] 配置路由守卫（未登录跳转登录页）
- [x] 登录成功后跳转首页

### 验收标准
1. 用户可以通过 POST /api/auth/register 注册
2. 用户可以通过 POST /api/auth/login 登录并获得 JWT
3. 携带 JWT 可以访问受保护接口
4. 未携带 JWT 访问受保护接口返回 401
5. 前端可以正常注册、登录
6. 登录状态刷新后保持
7. 退出登录清除 Token

---

## Phase 3：课程与知识点 ✅

### 目标
实现课程和知识点的 CRUD，管理端页面可操作。

### 后端任务
- [x] Course 实体 + Mapper + Service + Controller
- [x] KnowledgePoint 实体 + Mapper + Service + Controller
- [x] AdminCourseController（管理端课程 CRUD）
- [x] AdminKnowledgePointController（管理端知识点 CRUD）
- [x] CourseController（用户端课程列表、详情）
- [x] DTO：CourseCreateRequest、CourseVO、KnowledgePointCreateRequest、KnowledgePointVO
- [x] 知识点树形结构查询

### 前端任务
- [x] 用户端 CourseListView.vue
- [x] 用户端 CourseDetailView.vue（课程下知识点列表）
- [x] 管理端 CourseManage.vue（课程表格 + 增删改）
- [x] 管理端 KnowledgePointManage.vue（知识点管理）
- [x] API：course.ts、knowledgePoint.ts

### 验收标准
1. 管理端可以创建、编辑、删除课程
2. 管理端可以创建、编辑、删除知识点
3. 知识点支持父子层级展示
4. 用户端可以看到课程列表
5. 课程详情页显示知识点树

---

## Phase 4：题库系统 ✅

### 目标
实现题目的完整 CRUD，管理端可管理题目。

### 后端任务
- [x] Question 实体 + Mapper + Service
- [x] QuestionOption 实体 + Mapper
- [x] QuestionKnowledgePoint 关联表 Mapper
- [x] AdminQuestionController（管理端题目 CRUD）
- [x] QuestionController（用户端查询题目）
- [x] 题目创建时同步创建选项和知识点关联
- [x] 题目查询支持按课程、知识点、题型、难度筛选
- [x] 题目分页查询

### 前端任务
- [x] 管理端 QuestionManage.vue（题目列表、筛选）
- [x] 题目创建/编辑表单（题干、选项、解析、知识点选择、难度）
- [x] QuestionCard.vue 组件（题目展示 - QuestionListView.vue）
- [x] API：question.ts

### 验收标准
1. 管理端可以创建各类型题目
2. 创建题目时可以设置选项、关联知识点
3. 题目列表支持筛选和分页
4. 题目详情显示完整信息

---

## Phase 5：刷题与判分 ✅

### 目标
用户可以进行刷题练习，系统自动判分并记录。

### 后端任务
- [x] PracticeRecord 实体 + Mapper
- [x] PracticeService（获取题目、提交答案、判分）
- [x] PracticeController（获取练习题目、提交答案、刷题记录）
- [x] 判分逻辑：单选/多选/判断自动判分
- [x] 答题后返回正确答案和解析
- [x] 刷题记录查询
- [x] 练习统计

### 前端任务
- [x] PracticeView.vue（选择刷题模式 + 统计卡片）
- [x] PracticeSessionView.vue（答题界面 + 结果弹窗 + 完成总结）
- [x] 答题后展示结果（对错、解析）
- [x] 刷题记录页面（筛选 + 分页）
- [x] API：practice.ts

### 验收标准
1. 用户可以选择按课程/知识点/随机模式刷题
2. 提交答案后自动判分
3. 答错题目显示正确答案和解析
4. 刷题记录正确保存

---

## Phase 6：错题本 ✅

### 目标
自动收集错题，支持错题重练和管理。

### 后端任务
- [x] WrongQuestion 实体 + Mapper
- [x] WrongQuestionService（错题列表、移出、掌握状态更新、统计）
- [x] WrongQuestionController
- [x] PracticeService 中集成自动加入错题本逻辑
- [x] 错题按掌握程度筛选
- [x] 高频错题课程统计

### 前端任务
- [x] WrongQuestionView.vue（错题列表 + 统计卡片）
- [x] 错题筛选功能（掌握程度）
- [x] 掌握状态切换（单选按钮组）
- [x] 移出错题本（Popconfirm 确认）
- [x] API：wrongQuestion.ts

### 验收标准
1. 答错自动加入错题本
2. 同一题不重复添加（答错次数累加）
3. 可以手动移出错题本
4. 答对自动从错题本移出
5. 可以查看高频错题课程统计

---

## Phase 7：试卷与考试 ✅

### 目标
管理端可创建试卷，用户可参加考试并查看成绩。

### 后端任务
- [x] ExamPaper、ExamQuestion、ExamRecord、ExamAnswer 实体 + Mapper
- [x] ExamPaperService（试卷 CRUD、组卷）
- [x] ExamService（开始考试、提交试卷、判分、考试记录）
- [x] AdminExamController（管理端试卷管理）
- [x] ExamController（用户端考试）
- [x] 手动选题和随机组卷逻辑

### 前端任务
- [x] 管理端 ExamManage.vue（试卷管理、组卷）
- [x] 用户端 ExamListView.vue（试卷列表 + 考试记录）
- [x] 用户端 ExamTakeView.vue（考试答题界面、倒计时）
- [x] 用户端 ExamResultView.vue（成绩、答题详情）
- [x] API：exam.ts

### 验收标准
1. 管理端可以创建试卷、手动/随机组卷
2. 用户端可以看到已发布试卷
3. 用户可以参加考试并提交
4. 系统自动判分并记录成绩
5. 用户可以查看考试结果和答题详情

---

## Phase 8：AI 功能 ✅

### 目标
接入 AI Provider，实现题目解析、变式题、复习建议等功能。

### 后端任务
- [x] AiProvider 接口
- [x] OpenAiProvider 实现（支持 OpenAI 兼容 API）
- [x] AiService（题目解析、变式题、复习建议、知识点总结）
- [x] AiController（4 个接口）
- [x] AiConfig（AI_ENABLED、API_BASE_URL、API_KEY、MODEL 环境变量注入）
- [x] AI 调用错误处理（未启用/未配置/API 错误）
- [x] AiCallLog 实体与管理端调用日志查询

### 前端任务
- [x] MarkdownRenderer.vue 组件（使用 marked 渲染 Markdown）
- [x] AI API 封装（ai.ts）
- [x] ReviewSuggestionView.vue（AI 复习建议页面）
- [x] AI 功能入口整合到侧边栏导航
- [x] 题目解析和变式题按钮整合到刷题/错题页面

### 验收标准
1. 未配置 API Key 时显示友好提示
2. 配置后可调用 AI 生成题目解析
3. AI 解析以 Markdown 格式渲染
4. AI 调用超时/异常有友好提示
5. 可以请求 AI 生成复习建议

---

## Phase 9：统计可视化 ✅

### 目标
提供用户学习面板和管理端数据总览。

### 后端任务
- [x] StatisticsService（用户统计：总刷题、正确率、每日趋势、课程分布）
- [x] StatisticsController（3 个接口：overview、daily-trend、course-stats）
- [x] StatisticsVO（统计 VO）
- [x] 管理端统计接口：用户、题目、试卷与平台活跃度

### 前端任务
- [x] HomeView.vue（学习面板：统计卡片 + ECharts 图表 + 快捷入口）
- [x] ECharts 图表：7 天刷题趋势柱状图、课程正确率雷达图
- [x] API：statistics.ts

### 验收标准
1. 用户首页显示学习统计数据（总刷题、正确率、今日刷题、连续天数）
2. 首页有 ECharts 图表（趋势图 + 雷达图）
3. 快捷入口（刷题、错题本、考试、AI 复习建议）

---

## Phase 10：质量提升 ✅

### 目标
代码重构、补齐校验、完善文档、优化体验。

### 任务
- [x] 参数校验补全（所有创建/更新请求 DTO + Controller @Valid）
- [x] 接口文档补全（8 个 Controller Knife4j 注解）
- [x] 前端体验优化（loading 状态、空状态、错误提示、AI 超时优化）
- [x] 日志规范化（RequestLoggingFilter + 核心 Service 操作日志）
- [x] SQL 优化检查（复合索引 + N+1 查询修复）
- [x] 安全检查（安全响应头 + 越权校验 + SQL 注入/XSS 验证）
- [x] URL Bug 修复（API 路径重复 /api/api/ 问题）

---

## Phase 11：部署与简历 ✅

### 目标
完善部署流程，产出简历和面试材料。

### 任务
- [x] Docker Compose 完善（backend/frontend 健康检查、启动顺序、AI 环境变量）
- [x] README.md 完善（状态表更新、Lombok 说明、JDK 版本、FAQ 补充、演示流程链接）
- [ ] 项目截图制作（非阻塞演示素材，不影响阶段完成）
- [x] 演示流程文档（docs/DEMO.md）
- [x] docs/RESUME.md 完善（技术亮点更新、面试问答补充 Q8-Q10）
- [x] 面试问答整理（性能优化、日志设计、安全措施）
- [x] 技术亮点总结（已整合到 RESUME.md）
- [x] 后续扩展方向文档（docs/FUTURE.md）
- [x] Git 历史整理（提交记录规范、分支清晰）
- [x] 部署环境变量与本地运行说明校准
- [x] 演示账号密码校验

---

## Phase 12：体验增强迭代 ✅ 基本完成

### 目标
按 `docs/FUTURE.md` 的优先级持续补强核心体验和项目竞争力。

### 当前进度
- [x] AI 功能整合到刷题结果与错题本
- [x] 管理端统计面板
- [x] Markdown XSS 防护与管理端前端角色守卫
- [x] JWT 与客观题判分基础自动化测试
- [x] AI 流式输出（题目解析与变式题 SSE）
- [x] Docker 全链路验收与演示数据补齐
- [x] 前端按需加载、构建拆包与依赖安全升级
- [x] 用户个人中心
- [x] 错题重练模式
- [x] 题目导入/导出（Excel）
- [x] 学习计划与提醒
- [x] AiCallLog 调用日志接入
- [x] 核心业务可信度修复（API 路径、答案隐藏、考试防篡改、导入一致性）
- [x] 考试并发与事务一致性修复（行锁、答题唯一约束、发布后不可变）
- [x] Flyway 数据库版本迁移接入
- [x] 社区讨论功能（题目评论/回复/点赞）
- [x] 多端响应式适配（移动端侧边栏、答题、图表）
- [x] 题目难度自适应（根据用户各难度答题正确率动态推荐题目）
- [x] 填空题多空判分与简答题关键词匹配
- [x] 个人月度学习报告（本月刷题量、正确率、环比、错题变化、考试数据、多维度图表）
- [x] GitHub Actions CI 流水线（后端测试 + 前端构建 + Docker 镜像验证）
- [x] 个人中心学习报告跳转入口
- [x] 测试策略收敛（保留关键回归测试，停止为简单 CRUD 堆叠等价用例）
- [x] P3 监控体系集成（Spring Boot Actuator + Micrometer Prometheus + Docker Compose Prometheus + Grafana）

---

## Phase 13：AI 题目学习资产 ✅

### 目标
把一道题从“题干 + 答案 + 解析”升级为结构化 AI 学习对象，让用户答错后可以直接进入 AI 讲解、误区理解和变式训练闭环。

### P0 范围
- [x] AI 结构化题目讲解
- [x] 小白版解析
- [x] 步骤拆解
- [x] 错误选项分析
- [x] 常见误区
- [x] 变式题闭环
- [x] 题目 AI 学习资产缓存
- [x] 答错后 AI 讲解入口

### 当前阶段只做
- 聚焦单题学习资产，不扩展到复杂内容生产链路。
- 复用现有题库、刷题、错题本、AI 调用日志、配额和流式输出基础能力。
- 后续真正开发时，再同步更新 `docs/API_DESIGN.md`、`docs/DB_DESIGN.md` 和 `docs/ARCHITECTURE.md`。

### 当前阶段不做
- PDF / 图片 OCR
- 爬虫
- 用户上传题库自动入库
- AI 自动审核发布题目
- 复杂推荐系统

---

## Phase 14：AI 可视化交互讲解 ✅

### 目标
在 Phase 13 的结构化题目学习资产基础上，为适合过程展示的题型提供可视化交互讲解。

### P0 范围（文本可视化，前端渲染，不引入额外库）
- [x] 新增 `VISUAL_INTERACTIVE` 资产类型
- [x] 后端结构化 Prompt：强制 AI 输出标准 JSON
- [x] 前端解析与渲染：8 种可视化元素
- [x] 智能选择器：根据题型判断是否需要可视化
- [x] 错误处理：AI 输出不规范 JSON 时 fallback 为文本显示

### 支持的可视化元素类型（8 种）
1. `text` — 普通文本/Markdown
2. `table` — 二维数据表
3. `bar_chart` — 横向柱状图
4. `number_line` — 数轴/进度
5. `step_list` — 步骤列表（带状态）
6. `tree` — 树结构
7. `state_array` — 状态数组（高亮变化）
8. `matrix` — 矩阵/二维网格

### P1 范围（Mermaid 流程图渲染）
- [x] 新增 `mermaid` 可视化元素类型（第 9 种）
- [x] 后端 Prompt 增加 mermaid 类型定义和使用规则
- [x] 前端 mermaid.js 动态 import + 异步渲染 SVG
- [x] Mermaid 语法错误时 fallback 为代码显示
- [x] 后端单元测试验证 Prompt 包含 mermaid 指令

### P2 范围（代码执行动画）
- [x] 新增 `code_animation` 可视化元素类型（第 10 种）
- [x] 后端 Prompt 增加 code_animation 类型定义和使用规则
- [x] 前端 CodeAnimationViewer 组件：播放/暂停/上一步/下一步控制、可调速度
- [x] 暗色主题代码面板：行号 + 行高亮（当前执行行）
- [x] 变量状态面板：变量名/值 + changed 变化高亮标签
- [x] 控制台输出区域
- [x] 进度条 + 步骤描述
- [x] 响应式适配（移动端变量面板自动换行）
- [x] 后端单元测试验证 Prompt 包含 code_animation 指令

- [x] 代码语法高亮（highlight.js 按需加载 18 种语言、逐行高亮方案、github-dark 主题、语言别名映射）

### P3 范围（SQL 执行顺序可视化）
- [x] 新增 `sql_execution` 可视化元素类型（第 11 种）
- [x] 后端 Prompt 增加 sql_execution 类型定义和使用规则
- [x] 前端 SqlExecutionViewer 组件：暗色 SQL 面板 + 子句高亮 + 步骤描述 + 中间结果预览 + 播放控制
- [x] 最终结果面板展示
- [x] 速度调节 + 进度条
- [x] 后端单元测试验证 Prompt 包含 sql_execution 指令

### P4 范围（网络协议和操作系统过程可视化）
- [x] 新增 `network_protocol` 可视化元素类型（第 12 种）
- [x] 后端 Prompt 增加 network_protocol 类型定义和使用规则（entities + messages 时序图）
- [x] 前端 NetworkProtocolViewer 组件：时序图风格渲染（实体头部 + 生命线 + SVG 箭头消息）
- [x] 支持 current/highlight 状态高亮
- [x] 新增 `os_process` 可视化元素类型（第 13 种）
- [x] 后端 Prompt 增加 os_process 类型定义和使用规则（steps 状态 + ganttChart 甘特图）
- [x] 前端 OsProcessViewer 组件：可折叠步骤面板 + 进程状态表格 + 甘特图
- [x] 后端单元测试验证 Prompt 包含 network_protocol 和 os_process 指令（+2 个测试）

---

## Phase 15：AI 学习画像与个性化推荐 🚧

### 目标
基于答题记录、错题、AI 讲解使用情况和变式题完成情况，建立轻量学习画像，并提供个性化推荐和复习建议。

### P0 范围（学习诊断与每日推荐）
- [x] 知识点薄弱诊断（Top 8 薄弱知识点，正确率 < 70%，优先级排序）
- [x] 课程掌握概况（按正确率排序，含薄弱知识点数统计）
- [x] 错因分析汇总（掌握程度分布、反复出错题目数、近 7 天新增错题、高频错题课程）
- [x] 学习习惯分析（日均刷题、偏好题型/课程、学习频次评级、近 7 天趋势）
- [x] 每日推荐题目（高频错题间隔复习 → 薄弱知识点强化 → 未练习题目）
- [x] 每日学习建议文本（规则生成）
- [x] 前端学习诊断页面（6 个可视化区域 + 侧边栏入口 + 路由）
- [x] AI 个性化学习建议（基于完整诊断数据构建 Prompt + SSE 流式 Markdown 输出）

- [x] 相似题推荐（多维相似度评分：同知识点/题型/难度/课程，错题本和诊断页入口）
- [x] 错题归因分析增强（题型分布、难度分布、知识点错因排名、反复错题详情、每周错题趋势 + AI Prompt 增强）
- [x] 单题错因分析（作答历史时间线、掌握趋势、错误模式描述、连续错误检测、反复错题分析）

### 候选方向（后续迭代）
- 多次错题归因
- 向量相似度增强推荐

---

## Phase 16：题目投稿与 AI 题库生产 🚧

### 目标
在学习资产和诊断推荐能力稳定后，再进入题库供给侧建设，支持题目投稿、AI 规范化、质检、标注、难度评估和管理员审核辅助。

### P0 范围（题目投稿中心）
- [x] 题目投稿表（question_submission，Flyway V7）
- [x] 用户端投稿提交（支持 5 种题型 + 选项 + 解析 + 标签 + 来源）
- [x] 用户端投稿列表（按状态筛选 + 分页）
- [x] 管理端投稿列表（状态/课程/关键词筛选 + 分页）
- [x] 管理员审核（通过/拒绝 + 审核意见）
- [x] 投稿入库为正式题目（自动创建题目 + 选项 + 知识点关联）
- [x] 投稿统计（各状态数量）
- [x] 前端用户投稿页面 + 管理端审核页面
- [x] 路由和侧边栏导航
- [x] 投稿链路修复与加固（统一前端 API 返回契约、判断题答案提交、单选正确项校验、填空/简答入库后可参与正式判分、后端投稿服务单元测试）

### P1 范围（AI 质检与审核辅助）
- [x] AI 题目质检（5 维度：格式规范、内容完整性、答案正确性、解析质量、知识点相关性）
- [x] 结构化质检结果输出（质量评分、推荐审核意见、风险点、修改建议）
- [x] AI 降级回退（AI 不可用时自动回退到基础规则检查）
- [x] 管理端质检对话框（综合评分、5 维检查卡片、风险点、修改建议）
- [x] 后端单元测试（7 个测试，覆盖正常/异常/降级场景）

- [x] AI 知识点标注（自动为投稿推荐最相关知识点，置信度+理由，一键应用，降级关键词匹配，8 个单元测试）

- [x] AI 难度评估（基于题目内容自动评估难度 1-5 星，布鲁姆认知层次分析，影响因素列表，投稿者标注对比，降级规则评估，8 个单元测试）

### 已完成（本轮新增）
- [x] 质检结果缓存（@Cacheable, TTL 30 分钟）
- [x] 标注结果缓存（@Cacheable, TTL 30 分钟）
- [x] 难度评估结果缓存（@Cacheable, TTL 30 分钟）
- [x] 一键填充审核意见（基于缓存质检结果生成，管理端审核对话框）

### 已完成（后续迭代）
- [x] Excel / Markdown 导入增强（Markdown 格式题目批量导入，Round 91）
- [x] 内容来源记录和复审机制（Flyway V8，5 种来源类型自动标记，复审记录表，定期复审，管理端来源筛选+复审弹窗，Round 93）

### 候选方向（后续迭代）
- 复审结果缓存

---

## Phase 17：间隔重复与智能复习 ✅ 基本完成

### 目标
基于 SM-2（SuperMemo 2）算法实现间隔重复复习系统，将刷题和错题自动纳入复习计划，系统根据答题质量动态调整复习间隔。复习统计数据整合到月度学习报告。

### 已完成
- [x] question_review_schedule 表（Flyway V9）
- [x] SpacedRepetitionService（SM-2 算法核心）
- [x] ReviewController（8+2 个接口：stats、due、cards、add、submit、remove、reset + sync-wrong-questions + ai-suggestion、ai-suggestion/stream）
- [x] 刷题自动加入复习计划（PracticeService 集成）
- [x] 前端 ReviewView 页面（统计卡片、复习会话、全部卡片列表）
- [x] 错题自动复习调度（一键同步错题本到复习计划）
- [x] 循环依赖消除（SpacedRepetitionService 直接操作 WrongQuestionMapper）
- [x] AI 复习建议整合（ReviewContextVO + AiService 6 个新方法 + SSE 流式输出）
- [x] 复习统计整合到学习报告（LearningReportVO +6 字段 + StatisticsService.buildReviewStats + 前端复习指标卡片和月度复习趋势图）
- [x] 25 个单元测试（SM-2 算法 12 + 错题同步 4 + AI 复习建议 5 + 学习报告复习统计 4）

---

## Phase 18：全局搜索与快捷导航 ✅ 基本完成

### 目标
让用户能从任意页面快速定位题目、课程和知识点，并提供常用搜索建议。

### 已完成
- [x] 跨题目、课程、知识点的分组模糊搜索
- [x] ⌘K / Ctrl+K / `/` 快捷入口、键盘导航和关键词高亮
- [x] 搜索历史、热门搜索、单条/全部清除
- [x] 搜索结果缓存与移动端适配
- [x] 24 个后端单元测试

---

## Phase 19：AI 调用分析与成本控制 ✅ 基本完成

### 目标
让管理员看清 AI 功能的使用量、稳定性和成本相关指标，为配额与模型策略提供依据。

### 已完成
- [x] AI 调用总览：总调用、成功率、今日调用、Tokens、平均耗时
- [x] 每日趋势、按功能/模型分布、Top 活跃用户、最近失败调用
- [x] 管理端 `AiUsageView` 与 7/14/30/90 天筛选
- [x] `AiUsageServiceTest` 11 个单元测试

### 候选方向
- 按用户独立配置 AI 配额
- 基于模型单价的成本预估
- AI 调用日报/周报与异常告警

---

## Phase 20：演示验收与 AI 运营治理 📋

### 目标
将已完成的业务能力收敛为可稳定演示、可自动验证、可控制 AI 成本的项目基线；本阶段不新增与该目标无关的大型功能。

### P0：演示验收与质量收尾
- [x] 考试完整作答、提交、结果查看已完成真实 Docker 浏览器验收（Round 108：3 题作答、确认提交、自动判分与结果详情均正常）。
- [ ] 按 `docs/DEMO.md` 制作用户端和管理端真实演示截图。
- [ ] 推送后确认 GitHub Actions 的后端、前端和 Docker Job 实际通过。
- [x] 建立安全的浏览器 E2E 登录态：隔离 `e2e` Profile 固定一次性验证码答案，仍验证真实账号密码、JWT 与路由守卫（Round 110）。
- [x] 为刷题、错题复习、考试和管理端审核补充少量高价值端到端测试（已补考试页 2 条页面级交互回归；浏览器 E2E 覆盖登录、课程浏览、刷题答错→错题本→掌握度更新→重练、三种题型作答→提交→自动判分→结果详情，以及用户投稿→管理员通过→正式入库；Round 113）。
- [ ] 处理 Testcontainers 本地兼容性，并将 Redis 缓存 TTL 提取为配置项。

### P1：AI 运营治理
- [x] 从 OpenAI 兼容上游响应记录真实 token 用量：同步响应读取 `usage`，流式请求默认请求并读取最终 `usage` 事件；不支持该扩展的上游可关闭 `AI_STREAM_INCLUDE_USAGE`，此时不进行本地估算。
- [ ] 按模型单价估算调用成本，并在管理端显示成本趋势。
- [ ] 支持管理员按用户调整 AI 配额，并记录调整原因和时间。
- [ ] 增加调用日报/周报、失败率与异常用量提醒。
- [ ] 为调用日志补充 Prompt/模型版本等追踪信息。

### P2：后续衔接
- [ ] 缓存投稿复审结果，降低重复审核成本。
- [ ] 实现重复题/高度相似题检测、题目版本和纠错记录。
- [ ] 建立 AI 讲解、变式训练与间隔复习的学习效果指标。

### 暂不优先
- 向量相似度增强推荐（待题库规模和真实效果数据证明必要性）
- PDF / 图片 OCR、爬虫、用户上传题库自动入库
- AI 自动审核发布题目、多租户和移动端应用
