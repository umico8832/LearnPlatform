# AI 题库与错题复习系统 - 架构设计文档

## 一、系统架构总览

```
┌─────────────────────────────────────────────────────────┐
│                      用户浏览器                          │
└───────────────┬─────────────────────────┬───────────────┘
                │ HTTP                    │ HTTP
                ▼                         ▼
┌───────────────────────┐   ┌───────────────────────────┐
│   前端 (Vue 3 + Vite)  │   │   管理端前端 (同一项目)     │
│   用户端 SPA           │   │   /admin 路由             │
└───────────┬───────────┘   └───────────┬───────────────┘
            │                           │
            │  Axios HTTP 请求           │
            ▼                           ▼
┌─────────────────────────────────────────────────────────┐
│              Nginx (反向代理 / 静态资源)                   │
└───────────────────────────┬─────────────────────────────┘
                            │ Proxy Pass
                            ▼
┌─────────────────────────────────────────────────────────┐
│            后端 Spring Boot 3 应用                        │
│                                                         │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ Controller  │→ │   Service    │→ │    Mapper     │  │
│  │ (REST API)  │  │ (业务逻辑)    │  │ (数据访问)     │  │
│  └─────────────┘  └──────────────┘  └───────┬───────┘  │
│                                              │          │
│  ┌─────────────┐  ┌──────────────┐          │          │
│  │ JWT Filter  │  │ AI Service   │          │          │
│  │ (鉴权拦截)   │  │ (AI Provider)│          │          │
│  └─────────────┘  └──────────────┘          │          │
└──────────────────────────┬──────────────────┬───────────┘
                           │                  │
                           ▼                  ▼
                ┌──────────────┐
                │  MySQL 8     │
                │  (主数据库)    │
                └──────────────┘

                           │
                           ▼
                ┌──────────────────┐
                │  AI Provider API │
                │  (OpenAI 等)     │
                └──────────────────┘
```

---

## 二、技术栈明细

### 2.1 前端技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | 3.4+ | 前端框架，Composition API |
| TypeScript | 5.x | 类型安全 |
| Vite | 8.x | 构建工具 |
| Element Plus | 2.x | UI 组件库 |
| Pinia | 2.x | 状态管理 |
| Vue Router | 4.x | 路由管理 |
| Axios | 1.x | HTTP 请求 |
| ECharts | 5.x | 图表可视化 |
| marked | 18.x | Markdown 转 HTML |
| DOMPurify | 3.x | Markdown HTML 安全净化 |

### 2.2 后端技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17+ | 编程语言 |
| Spring Boot | 3.2+ | 应用框架 |
| MyBatis-Plus | 3.5+ | ORM 框架 |
| Spring Security | 6.x | 安全框架（JWT 集成） |
| JWT (jjwt) | 0.12+ | Token 签发与验证 |
| Knife4j | 4.x | 接口文档（Swagger 增强） |
| Validation | - | 参数校验 |
| Lombok | - | 简化 Java 代码 |
| MapStruct (可选) | - | 对象映射 |

### 2.3 数据库

| 技术 | 版本 | 用途 |
|------|------|------|
| MySQL | 8.0+ | 主数据库 |

### 2.4 部署

| 技术 | 用途 |
|------|------|
| Docker | 容器化 |
| Docker Compose | 多容器编排 |
| Nginx | 前端静态资源 + 反向代理 |

---

## 三、项目目录结构

### 3.1 项目根目录

```
LearnPlatform/
├── frontend/                    # 前端项目
├── backend/                     # 后端项目
├── docs/                        # 项目文档
├── docker-compose.yml           # Docker 编排
├── .env.example                 # 环境变量示例
├── .gitignore                   # Git 忽略
└── README.md                    # 项目说明
```

### 3.2 前端目录结构

```
frontend/
├── public/
│   └── favicon.ico
├── src/
│   ├── api/                     # API 请求模块
│   │   ├── index.ts             # Axios 实例配置
│   │   ├── user.ts              # 用户相关 API
│   │   ├── course.ts            # 课程相关 API
│   │   ├── question.ts          # 题目相关 API
│   │   ├── practice.ts          # 刷题相关 API
│   │   ├── wrongQuestion.ts     # 错题本 API
│   │   ├── exam.ts              # 考试相关 API
│   │   ├── ai.ts                # AI 相关 API
│   │   └── statistics.ts        # 统计 API
│   ├── assets/                  # 静态资源
│   │   ├── styles/              # 全局样式
│   │   └── images/              # 图片资源
│   ├── components/              # 公共组件
│   │   ├── layout/              # 布局组件
│   │   │   ├── AppHeader.vue
│   │   │   ├── AppSidebar.vue
│   │   │   └── AppFooter.vue
│   │   ├── common/              # 通用组件
│   │   │   ├── MarkdownRenderer.vue
│   │   │   └── LoadingSpinner.vue
│   │   └── question/            # 题目相关组件
│   │       ├── QuestionCard.vue
│   │       └── QuestionOption.vue
│   ├── router/                  # 路由配置
│   │   └── index.ts
│   ├── stores/                  # Pinia 状态管理
│   │   ├── user.ts              # 用户状态
│   │   └── app.ts               # 应用全局状态
│   ├── types/                   # TypeScript 类型定义
│   │   ├── api.ts               # API 响应类型
│   │   ├── user.ts              # 用户类型
│   │   ├── question.ts          # 题目类型
│   │   └── exam.ts              # 考试类型
│   ├── utils/                   # 工具函数
│   │   ├── request.ts           # Axios 封装
│   │   ├── auth.ts              # Token 管理
│   │   └── format.ts            # 格式化工具
│   ├── views/                   # 页面视图
│   │   ├── auth/                # 登录注册
│   │   │   ├── LoginView.vue
│   │   │   └── RegisterView.vue
│   │   ├── home/                # 首页
│   │   │   └── HomeView.vue
│   │   ├── course/              # 课程
│   │   │   ├── CourseListView.vue
│   │   │   └── CourseDetailView.vue
│   │   ├── practice/            # 刷题
│   │   │   ├── PracticeView.vue
│   │   │   └── PracticeSessionView.vue
│   │   ├── wrong/               # 错题本
│   │   │   └── WrongQuestionView.vue
│   │   ├── exam/                # 考试
│   │   │   ├── ExamListView.vue
│   │   │   ├── ExamTakeView.vue
│   │   │   └── ExamResultView.vue
│   │   ├── profile/             # 个人中心
│   │   │   └── ProfileView.vue
│   │   ├── statistics/          # 统计
│   │   │   └── StatisticsView.vue
│   │   └── admin/               # 管理端
│   │       ├── AdminDashboard.vue
│   │       ├── UserManage.vue
│   │       ├── CourseManage.vue
│   │       ├── KnowledgePointManage.vue
│   │       ├── QuestionManage.vue
│   │       └── ExamManage.vue
│   ├── App.vue                  # 根组件
│   └── main.ts                  # 入口文件
├── index.html
├── vite.config.ts
├── tsconfig.json
├── tsconfig.node.json
├── package.json
└── .env.development             # 开发环境变量
```

### 3.3 后端目录结构

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/learnplatform/
│   │   │   ├── LearnPlatformApplication.java       # 启动类
│   │   │   ├── config/                              # 配置类
│   │   │   │   ├── WebMvcConfig.java                # Web MVC 配置
│   │   │   │   ├── SecurityConfig.java              # Spring Security 配置
│   │   │   │   ├── MyBatisPlusConfig.java           # MyBatis-Plus 配置
│   │   │   │   ├── Knife4jConfig.java               # 接口文档配置
│   │   │   │   └── CorsConfig.java                  # 跨域配置
│   │   │   ├── common/                              # 公共模块
│   │   │   │   ├── result/                          # 统一响应
│   │   │   │   │   ├── R.java                       # 统一响应体
│   │   │   │   │   └── ResultCode.java              # 响应码枚举
│   │   │   │   ├── exception/                       # 异常处理
│   │   │   │   │   ├── BusinessException.java       # 业务异常
│   │   │   │   │   └── GlobalExceptionHandler.java  # 全局异常处理器
│   │   │   │   └── constants/                       # 常量定义
│   │   │   │       └── Constants.java
│   │   │   ├── entity/                              # 实体类
│   │   │   │   ├── User.java
│   │   │   │   ├── Course.java
│   │   │   │   ├── KnowledgePoint.java
│   │   │   │   ├── Question.java
│   │   │   │   ├── QuestionOption.java
│   │   │   │   ├── PracticeRecord.java
│   │   │   │   ├── WrongQuestion.java
│   │   │   │   ├── ExamPaper.java
│   │   │   │   ├── ExamQuestion.java
│   │   │   │   ├── ExamRecord.java
│   │   │   │   └── ExamAnswer.java
│   │   │   ├── mapper/                              # MyBatis Mapper
│   │   │   │   ├── UserMapper.java
│   │   │   │   ├── CourseMapper.java
│   │   │   │   ├── KnowledgePointMapper.java
│   │   │   │   ├── QuestionMapper.java
│   │   │   │   ├── QuestionOptionMapper.java
│   │   │   │   ├── PracticeRecordMapper.java
│   │   │   │   ├── WrongQuestionMapper.java
│   │   │   │   ├── ExamPaperMapper.java
│   │   │   │   ├── ExamRecordMapper.java
│   │   │   │   └── ExamAnswerMapper.java
│   │   │   ├── service/                             # Service 接口
│   │   │   │   ├── UserService.java
│   │   │   │   ├── AuthService.java
│   │   │   │   ├── CourseService.java
│   │   │   │   ├── KnowledgePointService.java
│   │   │   │   ├── QuestionService.java
│   │   │   │   ├── PracticeService.java
│   │   │   │   ├── WrongQuestionService.java
│   │   │   │   ├── ExamPaperService.java
│   │   │   │   ├── ExamService.java
│   │   │   │   ├── StatisticsService.java
│   │   │   │   └── ai/
│   │   │   │       ├── AiProvider.java              # AI Provider 接口
│   │   │   │       ├── AiService.java               # AI 业务服务
│   │   │   │       └── impl/
│   │   │   │           └── OpenAiProvider.java       # OpenAI 实现
│   │   │   ├── service/impl/                        # Service 实现
│   │   │   │   ├── UserServiceImpl.java
│   │   │   │   ├── AuthServiceImpl.java
│   │   │   │   ├── CourseServiceImpl.java
│   │   │   │   ├── KnowledgePointServiceImpl.java
│   │   │   │   ├── QuestionServiceImpl.java
│   │   │   │   ├── PracticeServiceImpl.java
│   │   │   │   ├── WrongQuestionServiceImpl.java
│   │   │   │   ├── ExamPaperServiceImpl.java
│   │   │   │   ├── ExamServiceImpl.java
│   │   │   │   └── StatisticsServiceImpl.java
│   │   │   ├── controller/                          # Controller 层
│   │   │   │   ├── auth/
│   │   │   │   │   └── AuthController.java
│   │   │   │   ├── user/
│   │   │   │   │   └── UserController.java
│   │   │   │   ├── course/
│   │   │   │   │   └── CourseController.java
│   │   │   │   ├── question/
│   │   │   │   │   └── QuestionController.java
│   │   │   │   ├── practice/
│   │   │   │   │   └── PracticeController.java
│   │   │   │   ├── wrong/
│   │   │   │   │   └── WrongQuestionController.java
│   │   │   │   ├── exam/
│   │   │   │   │   └── ExamController.java
│   │   │   │   ├── ai/
│   │   │   │   │   └── AiController.java
│   │   │   │   ├── statistics/
│   │   │   │   │   └── StatisticsController.java
│   │   │   │   └── admin/
│   │   │   │       ├── AdminUserController.java
│   │   │   │       ├── AdminCourseController.java
│   │   │   │       ├── AdminKnowledgePointController.java
│   │   │   │       ├── AdminQuestionController.java
│   │   │   │       ├── AdminExamController.java
│   │   │   │       └── AdminDashboardController.java
│   │   │   ├── dto/                                 # 数据传输对象
│   │   │   │   ├── auth/
│   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   ├── RegisterRequest.java
│   │   │   │   │   └── LoginResponse.java
│   │   │   │   ├── question/
│   │   │   │   │   ├── QuestionCreateRequest.java
│   │   │   │   │   ├── QuestionUpdateRequest.java
│   │   │   │   │   ├── QuestionQueryRequest.java
│   │   │   │   │   └── QuestionVO.java
│   │   │   │   └── ...
│   │   │   ├── security/                            # 安全模块
│   │   │   │   ├── JwtTokenProvider.java            # JWT 工具
│   │   │   │   ├── JwtAuthenticationFilter.java     # JWT 过滤器
│   │   │   │   └── UserDetailsServiceImpl.java      # 用户认证服务
│   │   │   └── enums/                               # 枚举类
│   │   │       ├── RoleEnum.java
│   │   │       ├── QuestionTypeEnum.java
│   │   │       ├── DifficultyEnum.java
│   │   │       ├── ExamStatusEnum.java
│   │   │       └── MasteryLevelEnum.java
│   │   └── resources/
│   │       ├── application.yml                      # 主配置
│   │       ├── application-dev.yml                  # 开发环境
│   │       ├── application-prod.yml                 # 生产环境
│   │       ├── mapper/                              # MyBatis XML
│   │       │   └── *.xml
│   │       └── db/
│   │           └── migration/                       # Flyway 版本化迁移脚本
│   └── test/
│       └── java/com/learnplatform/
│           ├── service/
│           └── controller/
├── pom.xml
├── Dockerfile
└── .env.example
```

---

## 四、核心架构设计

### 4.1 统一响应结构

所有 API 返回统一的 `R<T>` 格式：

```java
public class R<T> {
    private int code;       // 业务状态码
    private String message; // 提示信息
    private T data;         // 数据
}
```

### 4.2 全局异常处理

```
GlobalExceptionHandler
├── @ExceptionHandler(BusinessException.class)    → 业务异常
├── @ExceptionHandler(MethodArgumentNotValidException.class)  → 参数校验异常
├── @ExceptionHandler(AccessDeniedException.class) → 权限异常
├── @ExceptionHandler(AuthenticationException.class) → 认证异常
└── @ExceptionHandler(Exception.class)             → 未知异常兜底
```

### 4.3 JWT 鉴权流程

```
请求流程:
Client → JWT Filter → Security Context → Controller → Service → Response

登录流程:
Client → AuthController.login()
  → AuthService.authenticate()
  → Password matches
  → JwtTokenProvider.generateToken()
  → Return token

鉴权流程:
Request → JwtAuthenticationFilter
  → Extract token from header
  → JwtTokenProvider.validateToken()
  → Set SecurityContext
  → Continue to Controller
```

### 4.4 Spring Security 配置

```
SecurityFilterChain:
  - /api/auth/login        → permitAll
  - /api/auth/register     → permitAll
  - /api/public/**         → permitAll
  - /doc.html, /webjars/** → permitAll (Knife4j)
  - /api/admin/**          → hasRole('ADMIN')
  - /api/**                → authenticated
  - 其他                     → denyAll
```

### 4.5 AI Provider 抽象层

```
AiProvider (接口)
├── generateExplanation(question)      → 生成解析
├── generateVariantQuestion(question)  → 生成变式题
├── generateReviewSuggestion(wrongQuestions) → 复习建议
├── generateSummary(knowledgePoint)    → 知识点总结
└── gradeShortAnswer(question, answer) → 简答题评分

OpenAiProvider (实现)
├── @Value ai.api-base-url
├── @Value ai.api-key
├── @Value ai.model
└── RestTemplate / WebClient 调用外部 API

AiService (业务服务)
├── 调用 AiProvider
├── 错误处理和降级
├── 日志记录
└── 缓存结果（后期）
```

---

## 五、数据流设计

### 5.1 刷题数据流

```
用户选择模式 → PracticeController.getQuestions()
  → 根据模式查询题目列表
  → 返回题目（不含正确答案）

用户提交答案 → PracticeController.submitAnswer()
  → Service 判分
  → 记录 PracticeRecord
  → 如果答错，自动加入 WrongQuestion
  → 返回结果（正确答案 + 解析）

用户查看统计 → StatisticsController.getUserStats()
  → 查询 PracticeRecord 统计
  → 查询 WrongQuestion 统计
  → 返回统计数据
```

### 5.2 考试数据流

```
用户选择试卷 → ExamController.startExam()
  → 创建 ExamRecord
  → 返回试卷题目（不含答案）

用户提交试卷 → ExamController.submitExam()
  → 逐题判分
  → 记录 ExamAnswer
  → 计算总分
  → 更新 ExamRecord
  → 答错题目加入 WrongQuestion
  → 返回考试结果

用户查看结果 → ExamController.getExamResult()
  → 查询 ExamRecord
  → 查询 ExamAnswer 详情
  → 返回完整结果
```

### 5.3 AI 调用数据流

```
用户请求 AI 解析 → AiController.generateExplanation()
  → 检查 AI 功能是否启用
  → 构造 Prompt
  → 调用 AiProvider
  → 解析返回结果
  → 记录调用日志（后期）
  → 返回 AI 生成内容

降级处理:
  → AI 调用失败 → 返回友好提示
  → AI 未配置   → 提示"AI 功能暂未开启"
```

题目解析与变式题同时支持流式链路：前端使用带 JWT 请求头的 `fetch + ReadableStream` 发起 POST 请求，`AiController` 将任务交给独立 `aiTaskExecutor`，`OpenAiProvider` 读取上游 OpenAI 兼容 SSE 并通过 `SseEmitter` 转发 `content`、`done`、`error` 事件。同步接口继续保留，避免影响复习建议、知识点总结及已有调用方。

---

## 六、权限设计

### 6.1 角色权限矩阵

| 功能 | USER | ADMIN |
|------|:----:|:-----:|
| 注册/登录 | ✅ | ✅ |
| 查看课程 | ✅ | ✅ |
| 刷题 | ✅ | ✅ |
| 错题本 | ✅ | ✅ |
| 参加考试 | ✅ | ✅ |
| 查看统计 | ✅ | ✅ |
| 管理用户 | ❌ | ✅ |
| 管理课程 | ❌ | ✅ |
| 管理知识点 | ❌ | ✅ |
| 管理题目 | ❌ | ✅ |
| 管理试卷 | ❌ | ✅ |
| 管理端统计 | ❌ | ✅ |

### 6.2 接口权限规则

```yaml
# 公开接口（无需登录）
/api/auth/login
/api/auth/register
/api/public/**

# 普通用户接口（需登录）
/api/courses/**
/api/questions/**
/api/practice/**
/api/wrong-questions/**
/api/exams/**
/api/statistics/**
/api/users/me

# 管理员接口（需 ADMIN 角色）
/api/admin/**
```

---

## 七、AI 接入设计

### 7.1 配置方式

通过环境变量或 application.yml 配置：

```yaml
ai:
  enabled: true
  api-base-url: ${AI_API_BASE_URL:https://api.openai.com/v1}
  api-key: ${AI_API_KEY:}
  model: ${AI_MODEL:gpt-3.5-turbo}
  timeout: 30000
  max-tokens: 2000
```

### 7.2 Prompt 模板管理

```
Prompt 模板示例：

题目解析:
"你是一位专业的教育辅导老师。请对以下题目提供详细的解析：
题目：{question}
选项：{options}
正确答案：{correctAnswer}
请用清晰的语言解释为什么这个答案是正确的，以及其他选项为什么是错误的。"

变式题生成:
"基于以下题目，生成一道类似但不同的变式题：
题目：{question}
题型：{type}
难度：{difficulty}
..."
```

### 7.3 降级策略

```
if (ai.api-key 为空) {
    return "AI 功能暂未配置，请联系管理员";
}
if (AI 调用超时) {
    return "AI 服务响应超时，请稍后重试";
}
if (AI 调用异常) {
    return "AI 服务暂时不可用，请稍后重试";
}
```

---

## 八、部署架构

### 8.1 Docker Compose 编排

```yaml
services:
  mysql:
    image: mysql:8.0
    volumes: mysql-data:/var/lib/mysql
    env: MYSQL_DATABASE, DB_PASSWORD

  redis:  # 可选
    image: redis:7-alpine
    ports: 6379:6379

  backend:
    build: ./backend
    ports: ${BACKEND_HOST_PORT:-8080}:8080
    depends_on: mysql
    env: DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, AI_API_KEY, AI_TIMEOUT, AI_MAX_TOKENS

  frontend:
    build: ./frontend
    ports: ${FRONTEND_HOST_PORT:-80}:80
    depends_on: backend
    nginx.conf: 反向代理 /api → backend:8080
```

MySQL 仅在 Compose 内部网络暴露，前后端宿主机端口可通过环境变量调整。Element Plus 组件与 ECharts 图表模块均按需引入，避免生产包整体加载。

### 8.2 Nginx 配置

```nginx
server {
    listen 80;

    # 前端静态资源
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理
    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

---

## 九、开发规范

### 9.1 命名规范
- 数据库表名：snake_case（如 `practice_record`）
- 数据库字段：snake_case（如 `create_time`）
- Java 类名：PascalCase（如 `PracticeRecord`）
- Java 方法名：camelCase（如 `getPracticeRecord`）
- 前端组件名：PascalCase（如 `QuestionCard.vue`）
- 前端文件夹：camelCase 或 kebab-case

### 9.2 接口规范
- RESTful 风格
- GET 查询，POST 创建，PUT 更新，DELETE 删除
- 路径使用 kebab-case
- 返回统一 R<T> 结构

### 9.3 Git 提交规范
- feat(module): 新功能
- fix(module): 修复 Bug
- docs: 文档更新
- refactor: 重构
- style: 样式调整
- test: 测试相关
- chore: 构建/工具相关
