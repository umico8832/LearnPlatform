# AI 题库与错题复习系统 - Agent 交接文档

本文档用于跨对话、跨 Agent、跨开发阶段交接项目状态。
新 Agent 接手时，必须先阅读本文件，再结合 `AGENTS.md`、`README.md`、`docs/ROADMAP.md`、`docs/CHANGELOG_AGENT.md` 和实际代码判断当前状态。

---

## 1. 项目基本信息

项目名称：AI 题库与错题复习系统
项目定位：用于学习、刷题、错题复习和 AI 辅助学习的中大型 Web 项目
开发环境：macOS (本地 MySQL 8.0.43、JDK 26、Maven 3.9.16、Node v22)
技术栈：Vue 3 + TypeScript + Vite + Element Plus + Pinia | Spring Boot 3.2.5 + MyBatis-Plus + MySQL 8 + JWT + Knife4j | Docker Compose

---

## 2. 当前项目阶段

当前阶段：Phase 12 — 体验增强迭代（基本完成，后端+前端测试持续扩充中）

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
- [x] Phase 12：体验增强迭代（✅ 基本完成：AI 题目助手、管理端统计、AI 流式输出、用户个人中心、错题重练、收藏题练习、Excel 导入导出、学习计划、AI 调用日志、核心业务可信度修复、后端核心服务测试、社区评论、多端适配、题目难度自适应、填空简答判分增强、个人学习报告、GitHub Actions CI、CommentController/AdminExam/AdminQuestion Controller 测试、CommentRequest @Max→@Size 修复、AdminUser/AdminCourse/AdminKnowledgePoint Controller 测试、前端 API 模块测试 7 个模块 62 个测试）

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
23. **前端 Vitest 测试**：144 个前端测试通过（auth 工具函数 9 个、user Store 7 个、MarkdownRenderer 组件 10 个、NotFoundView 组件 3 个、Practice API 14 个、Exam API 14 个、Favorite API 9 个、Course API 9 个、KnowledgePoint API 7 个、Statistics API 6 个、User API 3 个、AdminUser API 9 个、Comment API 7 个、WrongQuestion API 7 个、LearningPlan API 4 个、Question API 12 个、AI API 14 个），CI 已集成 `npm test`
20. **多端适配**：移动端抽屉导航、答题界面触摸友好、统计图表响应式
21. **题目难度自适应**：基于用户历史正确率的加权概率采样推荐
22. **GitHub Actions CI**：后端测试 + 前端构建 + Docker 镜像验证

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

- 前端页面级组件（PracticeSessionView、HomeView 等）尚未覆盖，需要更多 mocking 工作
- tokensUsed 字段暂未从上游 API 提取，仅记录调用次数
- 管理端缺少按用户单独调整配额的能力（当前全局统一配额）
- 项目截图未制作（非阻塞演示素材）
- CI 流水线需推送到 GitHub 后才能实际触发验证

---

## 6. 下一步建议任务

任务名称：Phase 12 后续 — 体验增强收尾与远期规划

Phase 12 已基本完成。所有 P0-P2 功能、技术债务均已偿还。前端全部 13 个 API 模块测试已覆盖。

后续可选方向：
- 扩展前端测试覆盖到 router guards 和页面级组件
- 补充项目截图/演示素材（FUTURE.md #7）
- 进入 P3 远期规划：多租户、移动端 App、Redis 缓存、监控告警等

建议 commit message: `test(frontend): 补充剩余 6 个 API 模块单元测试（53 个测试）`

---

## 7. 新对话续接提示词

```
你现在接手一个长期开发中的全栈 Web 项目。

项目名称：AI 题库与错题复习系统
开发环境：macOS（本地 MySQL 8.0、JDK 26、Maven 3.9.16、Node v22）
技术栈：Vue 3 + TypeScript + Vite + Element Plus + Pinia | Spring Boot 3.2.5 + MyBatis-Plus + MySQL 8 + JWT + Knife4j | Docker Compose

重要注意：本项目已移除 Lombok（JDK 26 兼容性问题），所有 Java 实体类需要手写 getter/setter/toString。

请先阅读以下文件：
1. AGENTS.md
2. README.md
3. docs/ROADMAP.md
4. docs/CHANGELOG_AGENT.md
5. docs/HANDOFF.md

工作方式：
1. 先根据 docs/HANDOFF.md 理解当前项目状态；
2. 再根据 docs/ROADMAP.md 判断当前阶段；
3. 再根据代码实际情况验证文档是否过时；
4. 自动选择下一步最高优先级任务；
5. 继续开发、测试、修复、更新文档；
6. 除非遇到重大方向问题，否则不要频繁问我；
7. 每轮结束都要更新 docs/CHANGELOG_AGENT.md 和必要文档。

当前阶段：Phase 0-12 基本完成，P0-P2 功能全部实现。

已完成模块：用户鉴权、课程知识点、题库、刷题判分（含填空简答增强）、错题本（含重练）、试卷考试、AI 功能（含流式输出与配额管理）、统计可视化（含个人学习报告）、质量提升、部署简历、收藏题练习、Excel 导入导出、学习计划、题目评论、多端适配、难度自适应、管理端用户管理、后端核心测试（151 个）、前端 Vitest 测试（91 个，含 7 个 API 模块测试）、GitHub Actions CI。
后续扩展方向：见 docs/FUTURE.md；可扩展前端测试覆盖或进入 P3 远期规划。

本地运行方式：
- MySQL: sudo /usr/local/mysql/support-files/mysql.server start
- 后端: cd backend && mvn spring-boot:run
- 前端: cd frontend && npm run dev
```

---

## 8. 交接注意事项

- 不要依赖旧对话记忆
- 不要把 AGENTS.md 当进度表
- 不要清空 docs/CHANGELOG_AGENT.md
- 不要覆盖真实 .env
- 不要提交真实 API Key
- 先检查代码，再相信文档
- 发现文档与代码不一致时，以代码为准，并修正文档
- **不要使用 Lombok**，手写 getter/setter/toString（JDK 26 兼容性）