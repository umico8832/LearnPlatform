# 简历与面试材料

以下是基于真实实现的初稿。投递前对照[项目状态](../project/status.md)与目标提交核对，数字是工程证据，不作为学习效果或生产规模指标。

## 中文简历描述

### 简洁版

> **LearnPlatform AI 学习平台**：使用 Vue 3、TypeScript、Spring Boot 4、MySQL 构建“一道题从错到懂”的学习闭环：所选错项误区、三级提示、参数化算法动画、答案由服务端保管的变式题与 FSRS 间隔复习。追问在正解开放前隔离私有材料，并经规则与独立模型判断；仅独立作答更新复习排期。通过 Flyway、Testcontainers、Vitest 和隔离 Playwright 验证权限、判分、并发和学习事实的一致性。

### 详细版

> 实现以课程为组织入口的 AI 学习平台，连接题目理解、练习、错题、复习、阶段测评与试卷学习；学习与限时考试使用独立状态机，主观题由管理员按评分点批阅，客户端不决定判分和总分。
>
> 设计题目理解与导师协议：作答后只返回所选错项的误区，已审核提示按级领取，服务端控制正解开放；变式答案提交前保存在服务端。追问未解锁时只向生成模型提供已可见材料，回答经过规则与独立 JSON 判断，最多重生成一次后安全退回；使用同一 runId 计一次每日配额，各次生成和判断分别审计用量。
>
> 用确定性 TypeScript 模拟器驱动链表、出栈序列、二叉树动画，输入为场景描述与本题数据，支持执行轨迹和预测检查点，不执行模型代码。FSRS 评分区分独立与辅助作答，通过评分来源去重及事务保护避免重试重复计分；旧卡在首次 FSRS 评分前保留原到期日。
>
> 前端采用 Vue 3、TypeScript 与设计变量组织学习端和管理端，英文展示首页包含演示卡、路网与链表实验室；内容发布保留来源和审核边界。使用 Spring Security/JWT、MyBatis-Plus、Flyway、Docker Compose、JUnit/Testcontainers、Vitest/Playwright 建立可迁移和可验证的工程基线。

## English summary

> **LearnPlatform** is a course learning platform built with Vue 3, TypeScript, Spring Boot 4, and MySQL. It connects chosen-option misconception feedback, progressive hints, deterministic algorithm visualizations, server-graded variants, and FSRS review scheduling. Before solution unlock, question follow-ups isolate private reference material and check generated replies with rules and a separate model judgment. Only unaided answers update review schedules. Flyway, Testcontainers, Vitest, and isolated Playwright tests verify access control, grading, concurrency, and learning records.

## 可核对的规模与验证

截至 2026-10-17，以下数字有明确边界，投递前须重新核对：

| 项目 | 已记录事实 | 证据与边界 |
| --- | --- | --- |
| 正式可视化 | 3 类：链表、出栈序列、二叉树 | `frontend/src/viz/scenes/`；首页路网不算新增正式场景 |
| 已审核理解题 | 27 道 | 两道预置 + M5 25道；包含提示、误区与变式，历史审核记录为 AI 初稿 + Codex 冷读 + Claude 页面审核；后续遵循[独立 AI 审核口径](../product/explanation-conventions.md#教学内容审核) |
| 待审核扩展 | 树章节18道；图论及后续章节题目仅存草稿 | 树为PENDING/DRAFT，图论未入库且未挂动画；不并入27道 |
| 完整本地检查 | 六job通过，785.9秒；前端1093项，隔离E2E51项零重试 | 代码基线60a2a8cc；后续聚焦验证见项目状态，不能称当前HEAD全量通过 |
| 关键后端测试补强 | 同一相关套件47→65项，含40项真实MySQL集成 | 判断服务分支20/26→24/26，FSRS25/40→36/40；仅表示该套件覆盖 |
| 追问边界模拟 | 30条语料双模式60/60通过 | 确定性模拟用于边界回归，未完成真实模型教学质量评测 |

技术版本以主线锁文件和 Maven 为准。后端已迁移到 Spring Boot 4；精确版本以 `backend/pom.xml` 为准。前端依赖实验结果仍按各自分支与验证记录判断。

## 面试时可展开的取舍

### 为什么先隔离材料，再检查回答？

前端隐藏答案无法约束模型输入。未解锁时，生成模型拿不到正确答案、正解讲解和其他错项误区；独立判断使用私有参考检查结果泄露与材料口径。规则检查提供确定性拦截，模型判断补充推导和排除法等语义风险。生成内容完整缓冲后才发布，代价是增加首字延迟、调用与成本；真实泄露率和误拦率仍需真实评测。解锁后允许完整解释，继续检查材料口径。

### 为什么同一追问有多个调用，却只占一次额度？

生成、重生成和独立判断分别产生上游用量，审计必须完整；学习者发起的一次追问则共享服务端 runId。用户行锁保护每日准入，配额按分组首次调用计数，跨午夜的后续调用不占新一天额度。次数、成本和回答质量是不同指标。

### 为什么辅助作答不更新 FSRS？

看到提示后的正确答案不能作为独立记忆证据。服务端记录提示与追问辅助状态，辅助变式保留判分事实但不评分；独立评分通过来源唯一约束与事务去重。这样保留真实过程，也避免反复请求改变卡片。具体语义见[题目理解 API](../reference/api/question-understanding.md)。

### 为什么用模拟器，而不是运行模型生成的动画代码？

场景只接收白名单操作和参数，由确定性模拟器生成步骤、变量和检查点，可在内容审核时重放。代价是支持范围限于已实现的场景；图算法候选与不适配的题目保持草稿或无动画。首页展示脚本与正式学习场景的职责分开。

### 为什么采用模块化单体？

当前团队和部署规模适合本地事务，能直接保护判分、考试提交、内容入库与评分去重，减少分布式运维成本。独立扩缩容或团队职责成为实际需求时再评估拆分。当前课程/知识点引用主要由服务校验保护，极端并发竞态仍是已知债务，不能声称所有关系都由物理外键保证。

### 考试、内容治理和 AI 运营还有哪些证据？

- 考试提交校验归属、题目集合和时限，使用记录锁与唯一约束；主观题待批阅状态与结果固化保持明确。
- 投稿和正式题目独立存储，AI 提供质检建议，管理员显式审核与入库；最新内容版本、题目指纹和访问权限持续重检。
- AI Provider 隔离上游协议，调用治理保存Token、价格快照、trace与配额审计；没有真实模型价格时不虚构成本。
- 学习效果报告使用样本量与去重学习者门槛，结论保持观察性，不能归因于AI教学。

## 表述边界

不声称完整覆盖408课程、已完成所有页面重做、具备生产用户规模或已经证明学习提升。OCR、自动发布、图算法舞台、真实模型教学评测、真实OIDC与Turnstile凭据闭环均按实际完成状态描述。旧截图与历史CI通过记录不能替代当前版本验证。长期事实入口为[项目状态](../project/status.md)、[系统架构](../architecture/overview.md)与[API参考](../reference/api/index.md)。
