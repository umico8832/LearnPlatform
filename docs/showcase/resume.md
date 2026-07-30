# 简历与面试材料

本文档只使用真实已实现能力。提交简历前应再次核对[项目状态](../project/status.md)和代码，不把候选方向写成完成项。

## 中文简历描述

### 简洁版

> **LearnPlatform AI 题库与错题复习系统**：基于 Vue 3、TypeScript、Spring Boot 3 和 MySQL 的前后端分离学习平台，覆盖多题型题库、服务端判分、错题与间隔复习、模拟考试、AI 流式讲解、结构化变式训练、投稿审核和学习诊断。使用 Spring Security + JWT 保护权限，通过 Flyway、Docker Compose、Testcontainers、Vitest 和 Playwright 建立可迁移、可测试、可演示的工程基线。

### 详细版

> 设计并实现一套 AI 辅助题目学习平台，形成“课程与题目—练习判分—错题复习—模拟考试—学习诊断”的完整业务闭环。后端使用 Spring Boot、Spring Security、MyBatis-Plus 和 Flyway，前端使用 Vue 3、Element Plus、Pinia 和 ECharts。
>
> 将 AI 输出从一次性文本扩展为可缓存、可反馈的题目学习资产，支持 JWT 鉴权的 SSE 流式生成、答案服务端隔离的结构化变式题和首次真实判分。实现投稿 AI 质检、知识点标注和难度评估，但保留管理员审核与显式入库。
>
> 建立 AI Token、成本、用户配额、调整审计、trace、提醒和观察性学习效果分析；通过样本量与去重学习者双门槛避免把少数高频用户或调用次数解释成学习提升。使用 Docker Compose、Prometheus、Grafana、Loki、JUnit、Testcontainers、Vitest 和 Playwright 完成部署、监控和关键流程验证。

## English summary

> **LearnPlatform** is a full-stack learning and question-practice platform built with Vue 3, TypeScript, Spring Boot 3, and MySQL. It provides server-side grading, wrong-answer review, spaced repetition, mock exams, AI streaming explanations, structured variant training, content submission workflows, and learning diagnostics. The system uses Spring Security with JWT, Flyway migrations, Docker Compose, Testcontainers, Vitest, and Playwright. AI usage is governed through token, cost, quota, audit, trace, and alert data, while learning-effect reports remain explicitly observational.

## 可讲的工程亮点

### 判分与考试一致性

- 多题型答案归一化和最终判分位于 Service。
- 考试提交锁定考试记录，校验归属、题目集合、重复题号和时限。
- `exam_answer` 唯一约束为并发重复提交提供数据库兜底。
- 用户端在提交前不获取正确选项和私有解析。

### AI 资产与结构化训练

- `AiProvider` 隔离业务层和 OpenAI 兼容上游协议。
- 同步与 SSE 流式接口并存，前端处理增量、取消、错误和完成事件。
- AI 学习资产按题目和类型缓存并支持用户反馈。
- 结构化变式题的正确答案只保存在服务端，首次判分结果锁定。

### 内容生产治理

- 用户投稿与正式题目使用独立状态和数据表。
- AI 提供质检、标注、难度和审核意见，但不自动发布。
- 正式题目保留来源、纠错、复审和版本记录。
- 疑似重复检测优先使用可解释规则。

### AI 运营与效果边界

- 从上游 usage 提取输入、输出和总 Token。
- 仅在配置真实模型价格时固化调用成本。
- 支持用户配额、调整审计、traceId、Prompt 指纹、模型配置指纹和运营提醒。
- 学习效果同时检查作答量和去重学习者，样本不足返回 `INSUFFICIENT_DATA`。
- 明确区分观察性关联与因果提升。

### 工程质量

- Flyway V1–V19 管理 29 张业务表及其演进。
- Maven `verify` 接入 Checkstyle、SpotBugs 和 JaCoCo。
- 前端使用 ESLint、Prettier、Vitest 和全源码覆盖率门槛。
- Testcontainers 验证真实 MySQL 迁移、唯一约束和事务。
- Playwright 在隔离 Docker Profile 中覆盖登录、练习错题、考试和投稿审核等流程。
- Compose 集成 MySQL、Redis、Nginx、Prometheus、Grafana 和 Loki。

## 面试问题

### 为什么没有使用微服务？

当前业务量和团队规模不需要分布式部署。模块化单体可以使用本地事务保证判分、考试和投稿入库一致性，同时减少服务治理和运维成本。只有出现独立扩缩容、团队边界或故障隔离需求时，才值得拆分。

### 为什么主要使用逻辑外键？

项目需要低成本迁移和本地演示，因此没有为所有关系建立物理外键。代价是 Service 必须明确处理删除保护和引用一致性；关键并发不变量仍使用唯一索引，并由真实 MySQL 集成测试验证。

### AI Provider 如何设计？

业务 Service 依赖 `AiProvider` 契约，`OpenAiProvider` 负责上游请求、SSE、usage 和错误归一化。配额、日志、成本和业务缓存位于统一业务层，避免每个 AI 功能重复实现治理逻辑。

### 如何防止 AI 泄露变式题答案？

后端要求结构化输出，完整校验题型、选项、答案和难度，然后把公开题面与私有答案分离保存。前端只收到题干、选项和难度，提交答案后由服务端判分并锁定首次结果。

### 为什么学习效果不能说是 AI 带来的提升？

当前数据来自自然使用，不是随机实验，用户基础、课程、查看意愿和多资产暴露都会造成混杂。系统只能报告阅读前后或对照组的观察性差异，并通过样本量、去重学习者和排除规则降低误判。

### 项目遇到的典型问题

- 修复前端 API 基础路径重复导致的 `/api/api` 请求。
- 使用数据库唯一约束和事务处理考试重复提交。
- 将 AI 流式最终 usage 传入统一调用日志。
- 拆分过大的学习诊断 Service 和管理端题目页面，同时保持接口不变。
- 识别“只统计被加载文件”的虚高覆盖率，并改为全源码覆盖基线。

## 不应声称

- 不声称已经实现 OCR、爬虫、自动入库、自动发布或复杂向量推荐。
- 不声称支持生产级多租户和无限水平扩展。
- 不把测试数量、代码行数或 AI 调用次数作为业务效果。
- 不把当前观察性指标描述为因果实验结果。
