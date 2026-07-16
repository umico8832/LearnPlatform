# AI 题库与错题复习系统 - 简历项目描述

## 一、简历项目描述（中文）

### 简洁版（150字）

> **AI 题库与错题复习系统** — 基于 Vue 3 + Spring Boot 3 的全栈在线刷题平台。采用前后端分离架构，支持多题型题库管理、在线刷题、自动判分、错题本智能收集与重练、模拟考试、AI 智能解析与复习建议等功能。后端使用 MyBatis-Plus + Spring Security + JWT 实现数据持久化与权限控制，前端使用 Element Plus + Pinia + ECharts 构建用户界面与数据可视化。通过 Docker Compose 实现一键部署。

### 详细版（300字）

> **AI 题库与错题复习系统**
>
> 一款面向学习者和备考人群的在线刷题平台，集成 AI 辅助学习能力。
>
> **核心功能**：
> - 实现多角色（用户/管理员）权限体系，基于 Spring Security + JWT 鉴权机制
> - 支持单选、多选、判断等多题型题库管理，提供按课程、知识点、难度的多维筛选
> - 实现在线刷题练习，支持按课程、知识点、随机等多种模式，系统自动判分并记录
> - 错题自动收集机制，支持错题重练、掌握状态跟踪和高频错题知识点统计
> - 模拟考试系统，支持手动/随机组卷、倒计时考试、自动判分和成绩分析
> - AI 辅助功能，接入通用 AI Provider，提供题目解析生成、变式题生成、个性化复习建议
> - ECharts 数据可视化，展示用户学习趋势、课程正确率及管理端平台活跃与题型分布
>
> **技术栈**：
> - 前端：Vue 3 + TypeScript + Vite + Element Plus + Pinia + Vue Router + Axios + ECharts
> - 后端：Java 17 + Spring Boot 3 + MyBatis-Plus + Spring Security + JWT + Knife4j
> - 数据库：MySQL 8.0
> - 部署：Docker + Docker Compose + Nginx
>
> **项目亮点**：
> - 前后端分离，RESTful 接口规范，统一响应结构和全局异常处理
> - AI Provider 抽象层设计，支持多 AI 服务商切换，通过环境变量配置敏感信息
> - AI 学习效果闭环：将变式题升级为答案服务端隔离的结构化单选题，记录首次真实判分与正确率；同时对比阅读后同题表现，并以共享知识点、排除原题和 30 天窗口观察跨题迁移，展示样本量且不把调用量误当成学习提升
> - 完善的数据库设计，12 张核心业务表，支持逻辑删除和自动填充
> - Docker Compose 一键部署，包含健康检查和启动顺序控制
> - 规范化日志体系：HTTP 请求日志、业务操作日志、AI 调用日志
> - 性能优化：N+1 查询修复、复合索引、批量加载
> - 安全加固：安全响应头、越权校验、参数化查询与 Markdown HTML 净化

---

## 二、简历项目描述（英文）

### English Version (150 words)

> **AI Question Bank & Review System** — A full-stack online quiz platform built with Vue 3 and Spring Boot 3. Implements a front-end/back-end separated architecture supporting multi-type question management, online practice with auto-grading, smart wrong-answer collection and review, mock exams, and AI-powered explanations and study recommendations. Backend uses MyBatis-Plus + Spring Security + JWT for data persistence and access control. Frontend uses Element Plus + Pinia + ECharts for UI and data visualization. Deployed via Docker Compose with Nginx reverse proxy.

---

## 三、技术亮点（面试可讲）

### 3.1 架构设计亮点

1. **前后端分离架构**
   - Vue 3 SPA + Spring Boot REST API
   - 通过 Nginx 反向代理统一入口
   - Axios 请求拦截器统一处理 Token 注入和错误响应

2. **统一响应与全局异常处理**
   - 所有接口返回 `R<T>` 统一响应体（code + message + data）
   - `GlobalExceptionHandler` 统一捕获业务异常、参数校验异常、认证异常、权限异常
   - 自定义 `BusinessException` + `ResultCode` 枚举，错误码体系清晰

3. **JWT 鉴权 + Spring Security**
   - 自定义 `JwtAuthenticationFilter` 实现 Token 校验
   - `SecurityConfig` 配置精细化权限规则（公开接口、用户接口、管理接口）
   - 密码 BCrypt 加密存储

4. **MyBatis-Plus 最佳实践**
   - 自动填充 create_time/update_time
   - 逻辑删除（deleted 字段）
   - 分页插件
   - LambdaQueryWrapper 类型安全查询

5. **AI Provider 抽象层**
   - 接口 `AiProvider` + 实现 `OpenAiProvider`
   - 支持通过环境变量配置 API Base URL、API Key、模型名称
   - AI 功能降级：未配置 Key 时返回友好提示而非报错
   - 后续可扩展多个 AI Provider 实现

6. **数据库设计**
   - 12 张核心业务表，字段设计考虑后续扩展
   - 中间表实现题目与知识点的多对多关联
   - 逻辑外键而非物理外键，应用层维护一致性
   - 合理的索引设计

### 3.2 功能亮点

1. **智能错题收集**
   - 答错自动加入错题本，同一题不重复添加
   - 支持掌握状态跟踪（未掌握/部分掌握/已掌握）
   - 高频错题知识点统计，帮助定位薄弱环节

2. **多模式刷题**
   - 按课程、按知识点、随机、顺序四种模式
   - 自动判分 + 即时解析展示

3. **模拟考试**
   - 手动选题和随机组卷
   - 倒计时 + 超时自动提交
   - 完整的成绩分析和错题分析

4. **数据可视化**
   - ECharts 图表展示学习趋势、正确率、知识点掌握雷达图
   - 管理端数据总览

---

## 四、面试常见问题与回答

### Q1：介绍一下这个项目？

> 这是一个 AI 题库与错题复习系统，面向学习者和备考人群的在线刷题平台。技术栈是 Vue 3 + Spring Boot 3 前后端分离架构。
>
> 核心功能包括多题型题库管理、在线刷题自动判分、错题本智能收集与重练、模拟考试、AI 辅助解析和学习建议。
>
> 后端采用 Spring Security + JWT 实现鉴权，MyBatis-Plus 做持久化，所有接口统一响应结构和全局异常处理。前端使用 Element Plus 构建 UI，Pinia 管理状态，ECharts 做数据可视化。
>
> 项目已经做到 Docker Compose 一键部署，AI 功能通过 Provider 抽象层接入，支持环境变量配置，做到了敏感信息不硬编码。

### Q2：数据库是怎么设计的？

> 数据库一共 12 张核心表。用户表、课程表、知识点表是基础数据。题库相关的有题目表、选项表，题目和知识点是多对多关系，通过中间表关联。
>
> 刷题相关的是刷题记录表，每次答题记录用户答案和对错。错题本通过唯一索引保证同一用户同一题不重复添加，支持逻辑删除实现"移出错题本"。
>
> 考试相关的有试卷表、试卷题目关联表、考试记录表和答题详情表，支持一张试卷多次考试的场景。
>
> 所有表都有 create_time、update_time、deleted 三个通用字段，使用 MyBatis-Plus 的自动填充和逻辑删除。不使用物理外键，通过应用层维护数据一致性。

### Q3：JWT 鉴权是怎么实现的？

> 使用 Spring Security + JWT 方案。自定义了 JwtAuthenticationFilter 继承 OncePerRequestFilter，在每次请求时从 Header 中提取 Bearer Token，通过 JwtTokenProvider 验证 Token 有效性，解析出用户信息设置到 SecurityContext 中。
>
> SecurityConfig 配置了精细化的权限规则：登录注册接口放行，管理端 /api/admin/** 需要 ADMIN 角色，其他 /api/** 接口需要登录。密码使用 BCrypt 加密存储。
>
> Token 默认有效期 7 天，通过 application.yml 可配置。前端通过 Axios 拦截器自动注入 Token，401 时跳转登录页。

### Q4：AI 功能是怎么设计的？

> AI 功能采用 Provider 抽象层设计。定义了 AiProvider 接口，包含生成解析、生成变式题、复习建议等方法。当前实现了 OpenAiProvider，通过 RestTemplate 调用 OpenAI 兼容的 API。
>
> API Base URL、API Key、模型名称都通过环境变量配置，不硬编码在代码中。如果未配置 API Key，AI 功能会降级返回友好提示而不是报错。
>
> 第一版是非流式调用，后续可以扩展 SSE 流式输出。Prompt 模板在 Service 层管理，针对不同场景（题目解析、变式题、复习建议等）设计不同的 Prompt。

### Q5：前后端是怎么联调的？

> 前端开发时通过 Vite 的 proxy 配置将 /api 请求代理到后端 localhost:8080，解决跨域问题。生产环境通过 Nginx 反向代理统一入口。
>
> 前端统一封装了 Axios 实例，通过请求拦截器自动注入 JWT Token，响应拦截器统一处理错误（401 跳转登录、业务错误弹提示）。
>
> 接口文档使用 Knife4j（Swagger 增强），前端开发时可以参考文档确定接口格式。所有接口返回统一的 R<T> 结构，前端定义了对应的 TypeScript 类型。

### Q6：项目中遇到过什么技术难点？

> 1. **错题本的去重处理**：使用唯一索引（user_id + question_id）保证数据唯一性，已存在的错题更新 wrong_count，支持逻辑删除实现"移出错题本"而不丢失历史数据。
>
> 2. **AI Provider 抽象设计**：需要支持多种 AI 服务商切换，同时做好降级处理。通过接口抽象 + 环境变量配置 + 错误处理三层设计实现。
>
> 3. **多题型判分逻辑**：单选、多选、判断的判分逻辑不同，特别是多选题需要对比用户选择和正确答案的集合。统一在 Service 层处理。
>
> 4. **考试倒计时和超时处理**：前端使用 setInterval 做倒计时，超时自动提交。后端记录开始时间和结束时间，可以校验是否超时。

### Q7：如果要继续优化，你会怎么做？

> 1. **性能优化**：引入 Redis 缓存热点数据（课程列表、题目详情），减少数据库查询
> 2. **AI 增强**：实现 SSE 流式输出，用户体验更好；增加 AI 调用日志和用量统计
> 3. **功能扩展**：添加学习计划、收藏夹、题目讨论区、邮件通知等
> 4. **安全加固**：接口限流、操作日志审计、更细粒度的权限控制
> 5. **前端优化**：骨架屏、虚拟列表、PWA 支持
> 6. **部署优化**：CI/CD 流水线、多环境配置、日志收集和监控

### Q8：你是怎么做性能优化的？

> 1. **N+1 查询修复**：错题本列表查询原来是逐条查 Question，改为批量加载后用 Map 缓存，将 N+1 查询优化为 2 次查询。
>
> 2. **复合索引**：针对高频查询场景添加了复合索引，例如 `practice_record(user_id, create_time)` 优化用户刷题记录按时间查询，`wrong_question(user_id, mastery_level)` 优化按掌握程度筛选。
>
> 3. **AI 调用超时优化**：为 AI 接口创建独立的 Axios 实例，超时时间从默认 15 秒调整为 60 秒，避免 AI 长响应导致误报超时。

### Q9：日志是怎么设计的？

> 采用三层日志体系：
>
> 1. **HTTP 请求日志**：通过 `RequestLoggingFilter` 过滤器记录所有 API 调用，包括请求方法、URI、状态码、耗时。跳过健康检查和静态资源，4xx/5xx 用 warn 级别。
>
> 2. **业务操作日志**：核心 Service（AuthService、PracticeService、ExamService、WrongQuestionService）在关键操作处添加 info 级别日志，如用户注册、登录、答题提交、考试开始/结束等。
>
> 3. **安全日志**：认证失败、越权访问等安全事件通过 Spring Security 框架和自定义异常处理器记录。

### Q10：项目中做了哪些安全措施？

> 1. **认证授权**：Spring Security + JWT，管理端接口强制 ADMIN 角色
> 2. **越权校验**：考试结果、错题本操作都校验 userId 是否匹配当前用户
> 3. **安全响应头**：X-Content-Type-Options: nosniff（防 MIME 嗅探）、X-Frame-Options: SAMEORIGIN（防点击劫持）
> 4. **SQL 注入防护**：MyBatis-Plus 参数化查询，无原生 SQL 拼接
> 5. **XSS 防护**：题干和 AI 内容使用 marked 转换 Markdown，再通过 DOMPurify 净化 HTML 后渲染
> 6. **参数校验**：所有创建/更新请求 DTO 添加 @NotBlank/@NotNull/@Valid 注解，Controller 方法添加 @Valid 校验
> 7. **敏感信息管理**：API Key、JWT Secret 等通过环境变量配置，.env 文件不入 Git

---

## 五、技术关键词（简历标签）

```
Vue 3 | TypeScript | Spring Boot 3 | MyBatis-Plus | Spring Security | JWT
MySQL | Element Plus | Pinia | Vue Router | Axios | ECharts
Docker | Docker Compose | Nginx | RESTful API | 前后端分离
AI 集成 | 接口设计 | 数据库设计 | 权限控制 | 数据可视化
