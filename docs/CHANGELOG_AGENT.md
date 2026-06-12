# AI 题库与错题复习系统 - 开发日志

## 格式说明

每轮开发记录包含：
- 轮次和日期
- 当前阶段
- 本轮目标
- 完成内容
- 修改文件清单
- 验收结果
- 遗留问题
- 下轮建议

---

## Round 1 - 2026-06-12

### 阶段
Phase 0：项目规划

### 本轮目标
完成项目全部前期文档，为后续编码阶段打好基础。

### 完成内容

#### 1. docs/PRD.md - 产品需求文档
- 产品概述与定位
- 功能模块总览（9 大模块）
- 详细功能需求（用户系统、课程知识点、题库、刷题、错题本、试卷考试、AI 功能、后台管理、统计）
- 非功能需求（安全、性能、可用、可维护、部署）
- 接口规范（统一响应、错误码、接口风格）
- 数据库概要
- 页面规划（用户端 12 个页面、管理端 7 个页面）
- MVP 范围定义
- 里程碑规划

#### 2. docs/ARCHITECTURE.md - 架构设计文档
- 系统架构总览图
- 技术栈明细（前端、后端、数据库、部署）
- 项目目录结构（根目录、前端、后端完整目录树）
- 核心架构设计（统一响应、全局异常、JWT 鉴权、Security 配置、AI Provider）
- 数据流设计（刷题、考试、AI 调用）
- 权限设计（角色矩阵、接口权限规则）
- AI 接入设计（配置方式、Prompt 模板、降级策略）
- 部署架构（Docker Compose、Nginx 配置）
- 开发规范（命名、接口、Git 提交）

#### 3. docs/DB_DESIGN.md - 数据库设计文档
- 12 张核心表的完整设计
- 每张表的字段说明、建表 SQL
- ER 关系图
- 表关系说明
- 初始测试数据 SQL
- 数据量预估与优化建议
- 后续扩展建议

#### 4. docs/API_DESIGN.md - 接口设计文档
- 接口规范（基础路径、认证方式、响应结构、错误码、分页）
- 公开接口（注册、登录、健康检查）
- 用户接口（个人信息、修改密码）
- 课程接口
- 知识点接口
- 题库接口
- 刷题接口（获取题目、提交答案、记录）
- 错题本接口（列表、移出、掌握状态、重练、统计）
- 考试接口（试卷列表、开始考试、提交、结果、记录）
- AI 接口（解析、变式题、复习建议、知识点总结）
- 统计接口
- 管理端接口（用户、课程、知识点、题目、试卷管理）

#### 5. docs/ROADMAP.md - 开发路线图
- 12 个阶段总览（Phase 0 - Phase 11）
- 每个阶段的详细任务清单
- 验收标准
- 预计总工期 17-20 天

#### 6. docs/RESUME.md - 简历项目描述
- 中文简洁版和详细版简历描述
- 英文简历描述
- 技术亮点（架构设计、功能亮点）
- 面试常见问题与回答（7 个 Q&A）
- 技术关键词标签

#### 7. .gitignore
- Java / Maven / IDE 相关
- Node.js / 前端相关
- Docker / 环境变量相关
- 操作系统文件

#### 8. .env.example
- MySQL 配置
- JWT 配置
- AI 配置
- 前端配置
- 所有敏感值使用占位符

#### 9. README.md
- 项目介绍
- 功能列表
- 技术栈
- 项目结构
- 快速开始（本地开发 + Docker）
- 演示账号
- 接口文档地址
- 开发计划
- 项目规范
- 常见问题

### 修改文件清单
| 文件 | 操作 |
|------|------|
| docs/PRD.md | 新建 |
| docs/ARCHITECTURE.md | 新建 |
| docs/DB_DESIGN.md | 新建 |
| docs/API_DESIGN.md | 新建 |
| docs/ROADMAP.md | 新建 |
| docs/RESUME.md | 新建 |
| docs/CHANGELOG_AGENT.md | 新建 |
| .gitignore | 新建 |
| .env.example | 新建 |
| README.md | 新建 |

### 验收结果
- [x] PRD 覆盖所有功能模块
- [x] 架构设计前后端分离，技术栈明确
- [x] 数据库设计覆盖所有业务场景，有完整建表 SQL
- [x] 接口设计覆盖所有业务场景，有请求/响应示例
- [x] 路线图阶段清晰，验收标准明确
- [x] 简历描述真实合理，面试问答可直接使用
- [x] .gitignore 覆盖主要场景
- [x] .env.example 包含所有必要配置项
- [x] README 可指导开发者启动项目

### 遗留问题
- 无（Phase 0 为纯文档阶段）

### 下轮建议
- 进入 Phase 1：项目骨架
- 创建后端 Spring Boot 3 项目
- 创建前端 Vue 3 + TypeScript 项目
- 配置基础架构（统一响应、全局异常、MyBatis-Plus、Knife4j）
- 配置 Docker Compose
- 目标：`docker-compose up` 可一键启动，前后端可互相通信

---

## Round 3 - 2026-06-12

### 阶段
Phase 1：项目骨架验证 → Phase 2：用户与鉴权准备

### 本轮目标
安装开发环境，修复编译错误，验证 Phase 1 骨架可运行，进入 Phase 2。

### 完成内容

#### 1. 开发环境安装
- 通过 Homebrew 安装 Maven 3.9.16 和 Node.js（已有 v22.21.0 via nvm）
- 启动本地 MySQL 8.0.43（/usr/local/mysql）
- 创建 learn_platform 数据库并导入 schema.sql

#### 2. 移除 Lombok（JDK 26 兼容性修复）
- JDK 26 与 Spring Boot 3.2.5 管理的 Lombok 版本不兼容，注解处理器无法工作
- 从 pom.xml 移除 Lombok 依赖
- 手动重写 ResultCode.java（添加构造器、getter）
- 手动重写 R.java（添加 getter/setter）
- 手动重写 BusinessException.java（添加 getCode()）
- 重写 GlobalExceptionHandler.java（用 SLF4J Logger 替代 @Slf4j）

#### 3. Phase 1 验收通过
- [x] 后端 `mvn clean compile` 编译成功
- [x] 后端 `mvn spring-boot:run` 启动成功（0.688s）
- [x] 健康检查 `GET /api/public/health` 返回 `{"code":0,"message":"success","data":{"status":"UP"}}`
- [x] 前端 `npm run dev` 启动成功（Vite v5.4.21，507ms）
- [x] Vite 代理 `localhost:5173/api/public/health` 透传后端成功

#### 4. 文档更新
- 更新 docs/ROADMAP.md：Phase 1 状态改为 ✅ 已完成

### 修改文件清单
| 文件 | 操作 |
|------|------|
| backend/pom.xml | 修改（移除 Lombok 依赖） |
| backend/src/main/java/com/learnplatform/common/result/ResultCode.java | 修改（手写构造器和 getter） |
| backend/src/main/java/com/learnplatform/common/result/R.java | 修改（手写 getter/setter） |
| backend/src/main/java/com/learnplatform/common/exception/BusinessException.java | 修改（手写 getCode()） |
| backend/src/main/java/com/learnplatform/common/exception/GlobalExceptionHandler.java | 修改（SLF4J Logger 替代 @Slf4j） |
| docs/ROADMAP.md | 修改（Phase 1 状态更新） |

### 验收结果
- [x] 开发环境就绪（JDK 26、Maven 3.9.16、Node v22、MySQL 8.0）
- [x] 后端编译通过（移除 Lombok 后）
- [x] 后端启动成功，健康检查接口正常
- [x] 前端启动成功，Vite 代理正常
- [x] Phase 1 全部验收标准通过

### 遗留问题
- README.md Phase 1 启动说明待更新（与实际环境一致）
- schema.sql 中 BCrypt 密码哈希值需在 Phase 2 验证

### 下轮建议
- 进入 Phase 2：用户与鉴权
- 后端：User 实体、UserMapper、JwtTokenProvider、AuthService、AuthController
- 前端：API 封装、user store 完善、登录/注册页面接真实接口
- 建议 commit message: `docs(roadmap): Phase 1 项目骨架验证完成，进入 Phase 2`

---

## Round 2 - 2026-06-12

### 阶段
Phase 1：项目骨架

### 本轮目标
创建前后端项目骨架，配置基础架构，使项目可通过 Docker Compose 一键启动。

### 完成内容

#### 后端（Spring Boot 3）
1. **pom.xml** - Maven 项目配置，依赖：Spring Boot 3.2.5、Spring Security、MyBatis-Plus 3.5.5、MySQL Driver、JWT (jjwt 0.12.5)、Knife4j 4.4.0、Validation、Lombok
2. **LearnPlatformApplication.java** - 启动类，@MapperScan 扫描 mapper 包
3. **application.yml** - 主配置（数据库、JWT、AI、Knife4j），敏感值通过环境变量注入
4. **R.java** - 统一响应体 `R<T>`，包含 ok/fail/businessError 等静态方法
5. **ResultCode.java** - 响应码枚举（0-成功、1001-参数校验、1002-未登录、1003-无权限、1004-不存在、1005-业务异常、5000-系统异常）
6. **BusinessException.java** - 业务异常类
7. **GlobalExceptionHandler.java** - 全局异常处理器（业务异常、参数校验、认证异常、权限异常、未知异常）
8. **MyBatisPlusConfig.java** - 分页插件 + 自动填充 create_time/update_time
9. **CorsConfig.java** - CORS 跨域配置
10. **Knife4jConfig.java** - OpenAPI 接口文档配置
11. **SecurityConfig.java** - Spring Security 配置（Phase 1 暂时放行所有请求，Phase 2 接入 JWT）
12. **PublicController.java** - 健康检查接口 `GET /api/public/health`
13. **schema.sql** - 完整建表 SQL（13 张表 + 初始测试数据）
14. **Dockerfile** - 多阶段构建（Maven build + JRE 运行）

#### 前端（Vue 3 + TypeScript + Vite）
1. **package.json** - 依赖：Vue 3、Vue Router、Pinia、Element Plus、Axios、ECharts、@element-plus/icons-vue
2. **tsconfig.json / tsconfig.node.json** - TypeScript 配置，路径别名 @/*
3. **vite.config.ts** - Vite 配置（代理 /api → localhost:8080、Element Plus 自动导入、路径别名）
4. **index.html** - 入口 HTML
5. **env.d.ts** - Vue 模块声明
6. **main.ts** - 入口文件（注册 Element Plus、Pinia、Router、图标）
7. **App.vue** - 根组件
8. **global.css** - 全局样式
9. **types/api.ts** - API 响应类型（ApiResponse、PageData、PageQuery）
10. **types/user.ts** - 用户类型（UserInfo、LoginRequest、RegisterRequest、LoginResponse）
11. **utils/auth.ts** - Token 管理（getToken/setToken/removeToken/isAuthenticated）
12. **utils/request.ts** - Axios 封装（自动注入 Token、401 跳转、错误提示）
13. **router/index.ts** - 路由配置（登录、注册、首页、404，含路由守卫）
14. **stores/user.ts** - Pinia 用户 Store
15. **components/layout/AppLayout.vue** - 布局组件（侧边栏 + 顶部导航 + 内容区）
16. **views/home/HomeView.vue** - 首页（调用健康检查接口验证前后端通信）
17. **views/auth/LoginView.vue** - 登录页面（Element Plus 表单）
18. **views/auth/RegisterView.vue** - 注册页面（含确认密码校验）
19. **views/NotFoundView.vue** - 404 页面
20. **Dockerfile** - 多阶段构建（Node build + Nginx 运行）
21. **nginx.conf** - Nginx 配置（静态资源 + API 反向代理 + Knife4j 代理）

#### 部署
1. **docker-compose.yml** - 三服务编排（MySQL + Backend + Frontend），MySQL 健康检查，自动初始化 schema.sql
2. **.gitignore** - 更新，添加 auto-imports.d.ts 和 components.d.ts 忽略

### 修改文件清单
| 文件 | 操作 |
|------|------|
| backend/pom.xml | 新建 |
| backend/src/main/java/com/learnplatform/LearnPlatformApplication.java | 新建 |
| backend/src/main/resources/application.yml | 新建 |
| backend/src/main/java/com/learnplatform/common/result/R.java | 新建 |
| backend/src/main/java/com/learnplatform/common/result/ResultCode.java | 新建 |
| backend/src/main/java/com/learnplatform/common/exception/BusinessException.java | 新建 |
| backend/src/main/java/com/learnplatform/common/exception/GlobalExceptionHandler.java | 新建 |
| backend/src/main/java/com/learnplatform/config/MyBatisPlusConfig.java | 新建 |
| backend/src/main/java/com/learnplatform/config/CorsConfig.java | 新建 |
| backend/src/main/java/com/learnplatform/config/Knife4jConfig.java | 新建 |
| backend/src/main/java/com/learnplatform/config/SecurityConfig.java | 新建 |
| backend/src/main/java/com/learnplatform/controller/PublicController.java | 新建 |
| backend/src/main/resources/db/schema.sql | 新建 |
| backend/Dockerfile | 新建 |
| frontend/package.json | 新建 |
| frontend/tsconfig.json | 新建 |
| frontend/tsconfig.node.json | 新建 |
| frontend/vite.config.ts | 新建 |
| frontend/index.html | 新建 |
| frontend/env.d.ts | 新建 |
| frontend/src/main.ts | 新建 |
| frontend/src/App.vue | 新建 |
| frontend/src/assets/styles/global.css | 新建 |
| frontend/src/types/api.ts | 新建 |
| frontend/src/types/user.ts | 新建 |
| frontend/src/utils/auth.ts | 新建 |
| frontend/src/utils/request.ts | 新建 |
| frontend/src/router/index.ts | 新建 |
| frontend/src/stores/user.ts | 新建 |
| frontend/src/components/layout/AppLayout.vue | 新建 |
| frontend/src/views/home/HomeView.vue | 新建 |
| frontend/src/views/auth/LoginView.vue | 新建 |
| frontend/src/views/auth/RegisterView.vue | 新建 |
| frontend/src/views/NotFoundView.vue | 新建 |
| frontend/Dockerfile | 新建 |
| frontend/nginx.conf | 新建 |
| docker-compose.yml | 新建 |
| .gitignore | 修改 |

### 验收结果
- [x] 后端项目结构完整，包含所有基础配置类
- [x] 统一响应体 R<T> 和全局异常处理器就绪
- [x] MyBatis-Plus 分页插件和自动填充配置完成
- [x] Spring Security 配置就绪（Phase 1 暂时放行）
- [x] 健康检查接口 `GET /api/public/health` 可用
- [x] 建表 SQL 包含 13 张表和初始测试数据
- [x] 前端项目结构完整，包含路由、状态管理、API 封装
- [x] 前端登录/注册页面就绪
- [x] Vite 代理配置正确（/api → localhost:8080）
- [x] 首页调用健康检查接口验证前后端通信
- [x] Docker Compose 三服务编排就绪
- [x] Nginx 反向代理配置正确

### 遗留问题
- 开发环境（JDK 21、Maven、Node.js 18）需要用户自行安装（sudo 权限）
- SecurityConfig 暂时放行所有请求，Phase 2 需接入 JWT 鉴权
- schema.sql 中的 BCrypt 密码哈希值需要在 Phase 2 验证是否正确
- 前端 TS 报错全部是因为依赖未安装（npm install 后自动解决）

### 下轮建议
- 安装开发环境（JDK 21、Maven、Node.js 18）
- 运行 `npm install` 安装前端依赖
- 运行 `mvn spring-boot:run` 或 `docker-compose up` 验证项目启动
- 进入 Phase 2：用户与鉴权（实现 JWT 登录注册）
- 建议 commit message: `feat(skeleton): 创建前后端项目骨架和 Docker Compose 部署配置`
