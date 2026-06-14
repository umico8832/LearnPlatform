# AI 题库与错题复习系统 - 开发路线图

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
| 12 | 体验增强迭代 | 🔵 进行中 | 持续迭代 | 按 FUTURE.md 推进 P0/P1 功能 |

**预计总工期**：约 17-20 天

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

## Phase 4：题库系统 🔵

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

## Phase 5：刷题与判分 🔵

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

## Phase 6：错题本 🔵

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

## Phase 12：体验增强迭代 🔵

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
