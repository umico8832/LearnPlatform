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

当前阶段：Phase 12 — 体验增强迭代（进行中）

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
- [x] Phase 12：体验增强迭代（🔵 已完成 AI 题目助手、管理端统计、AI 流式输出、用户个人中心、错题重练、收藏、Excel 导入导出、学习计划、AI 调用日志与核心业务可信度修复）

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
9. **AI 题目助手**：刷题结果与错题本内直接生成 AI 深度解析和变式题
10. **管理端统计面板**：平台指标、题型分布、近 7 日活跃趋势、用户与试卷状态
11. **AI 流式输出**：题目解析、变式题与复习建议支持 JWT 认证的 POST SSE，前端实时渲染 Markdown
12. **部署与构建收尾**：Docker Compose 三服务健康启动、UTF-8 演示数据、可配置宿主机端口、前端按需加载与 Vite 8 安全升级
13. **用户个人中心**：修改昵称与密码，ProfileView 页面，通过顶部下拉菜单进入
14. **体验增强**：错题重练、题目收藏、Excel 导入导出、每日学习计划
15. **业务安全修复**：用户端答案隐藏、考试题目归属/重复/超时校验、前端 API 路径统一
16. **考试一致性**：提交行锁、超时状态保留、答题唯一约束、已发布试卷及引用题目不可变
17. **数据库迁移**：Flyway 基线与增量迁移，已有数据库可自动基线升级

### 后端关键文件
- 统一响应：`R.java` + `ResultCode.java` + `BusinessException` + `GlobalExceptionHandler`
- 实体：User, Course, KnowledgePoint, Question, QuestionOption, QuestionKnowledgePoint, PracticeRecord, WrongQuestion, ExamPaper, ExamQuestion, ExamRecord, ExamAnswer
- 服务：AuthService, CourseService, KnowledgePointService, QuestionService, PracticeService, WrongQuestionService, ExamPaperService, ExamService, AiService, StatisticsService
- AI：AiConfig, AiAsyncConfig, AiProvider(同步/流式接口), OpenAiProvider, AiController

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

- 管理端用户列表、角色调整和账号启停尚未实现，不应在 README 中宣称已完成
- AI 接口尚未加入用户级调用配额或限流，启用付费模型前应补充
- 前端仍缺少组件测试和端到端自动化测试

---

## 6. 下一步建议任务

任务名称：Phase 12 - 体验增强迭代

已完成：
- [x] Docker Compose 完善（健康检查、启动顺序、环境变量）
- [x] README.md 完善（状态表、Lombok、JDK、FAQ）
- [x] 演示流程文档（docs/DEMO.md）
- [x] 简历材料完善（docs/RESUME.md 技术亮点 + 面试问答 Q8-Q10）
- [x] 后续扩展方向文档（docs/FUTURE.md）
- [x] Git 历史整理（提交记录规范、分支清晰）

后续开发建议：补充管理端用户管理，增加 AI 用户级调用配额/限流，或继续增加核心 Service/Controller 集成测试。项目截图可在浏览器自动化连接恢复后补充。

建议 commit message: `fix(platform): 修复核心接口契约与考试判分漏洞`

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

当前阶段：Phase 0-11 已完成，Phase 12 体验增强迭代进行中。

已完成模块：用户鉴权、课程知识点、题库、刷题判分、错题本、试卷考试、AI 功能、统计可视化、质量提升、部署简历。
后续扩展方向：见 docs/FUTURE.md；P0 AI 体验与管理端统计已完成，下一步进入 P1 用户个人中心或错题重练。

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
