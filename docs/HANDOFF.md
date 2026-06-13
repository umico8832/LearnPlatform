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

当前阶段：Phase 10 — 质量提升（进行中）

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
- [ ] Phase 10：质量提升（进行中 - 参数校验和接口文档已补全）
- [ ] Phase 11：部署与简历（待开始）

---

## 3. 已完成内容

### 核心功能模块
1. **用户与鉴权**：JWT 登录注册、路由守卫、角色权限
2. **课程与知识点**：CRUD + 树形结构 + 前端页面
3. **题库系统**：5 种题型 CRUD + 选项管理 + 知识点关联
4. **刷题与判分**：自动判分 + 答题记录 + 统计
5. **错题本**：自动收集 + 掌握程度管理 + 统计
6. **试卷与考试**：手动组卷 + 考试答题(倒计时) + 自动判分 + 成绩查看
7. **AI 功能**：OpenAI 兼容 API + 题目解析 + 变式题 + 复习建议 + 知识点总结
8. **统计可视化**：首页统计卡片 + ECharts 趋势图 + 雷达图 + 快捷入口

### 后端关键文件
- 统一响应：`R.java` + `ResultCode.java` + `BusinessException` + `GlobalExceptionHandler`
- 实体：User, Course, KnowledgePoint, Question, QuestionOption, QuestionKnowledgePoint, PracticeRecord, WrongQuestion, ExamPaper, ExamQuestion, ExamRecord, ExamAnswer
- 服务：AuthService, CourseService, KnowledgePointService, QuestionService, PracticeService, WrongQuestionService, ExamPaperService, ExamService, AiService, StatisticsService
- AI：AiConfig, AiProvider(接口), OpenAiProvider, AiController

### 前端关键文件
- API 封装：auth(user store), course, knowledgePoint, question, practice, wrongQuestion, exam, ai, statistics
- 组件：AppLayout(侧边栏), MarkdownRenderer
- 页面：HomeView(统计面板), Login/Register, CourseList/Detail, QuestionList, Practice/Session/Records, WrongQuestion, ExamList/Take/Result, ReviewSuggestion
- 管理端：CourseManage, KnowledgePointManage, QuestionManage, ExamManage

---

## 4. 运行方式

### 本地开发
```bash
# MySQL
sudo /usr/local/mysql/support-files/mysql.server start

# 后端
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

---

## 5. 当前遗留问题

- Phase 2 ROADMAP 状态未更新为 ✅（代码已实现但文档标记为 🔵 进行中）
- 管理端统计接口未实现（用户数、题目数、试卷数）
- AiCallLog 调用日志未实现
- 题目解析/变式题按钮未整合到刷题/错题页面
- 无 AI 流式输出（SSE）
- 管理端路由未做前端角色守卫（仅隐藏菜单，后端已做 ADMIN 校验）

---

## 6. 下一步建议任务

任务名称：Phase 10 - 质量提升

任务目标：
- 代码审查和重构
- 参数校验补全（所有接口）
- 接口文档补全（Knife4j 注解）
- 前端体验优化（加载状态、错误提示、空状态）
- 边界情况处理（重复提交、并发、空数据）
- 日志规范化
- SQL 优化检查
- 安全检查

建议 commit message: `refactor(all): Phase 10 质量提升`

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

当前阶段：Phase 0-9 已完成，下一步进入 Phase 10 质量提升。

已完成模块：用户鉴权、课程知识点、题库、刷题判分、错题本、试卷考试、AI 功能、统计可视化。
待完成：质量提升（Phase 10）和部署简历（Phase 11）。

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