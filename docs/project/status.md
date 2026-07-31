# 项目状态

> 当前项目状态的唯一权威来源。阶段历史见[开发日志](changelog/index.md)，长期规划见[产品路线图](../product/roadmap.md)。

## 当前阶段

- Phase 20“演示验收与 AI 运营治理”：已完成。
- Phase 21“前端信息架构与视觉体验优化”：已完成。
- Phase 22“AI 学习效果验证”：持续推进。

当前主线是积累真实学习样本，并在样本量和去重学习者覆盖达到门槛后评估课程或用户基础分层。不优先增加复杂模型。

## 已具备能力

- 用户注册登录、JWT 鉴权、课程与知识点管理。
- 题库、练习判分、错题本、收藏、评论和练习记录。
- 试卷、考试作答、自动判分和结果分析。
- AI 流式解析、学习资产、结构化变式题和私有答案判分。
- 学习诊断、个性化建议、间隔重复和全局搜索。
- 题目投稿、AI 质检、正式题目复审、纠错和版本记录。
- AI 调用、Token、成本、配额、审计、提醒和学习效果观察。
- 用户端与管理端关键流程的真实 Docker Playwright E2E。
- 按产品、架构、开发、参考、项目状态和展示材料分类的文档体系，以及项目 Skill 与上游 Skill 的明确维护边界。

## 最新验证基线

业务代码基线（Round 166）：

- 后端 `mvn clean verify -B`：410 个默认测试通过。
- Checkstyle：0 违规。
- SpotBugs：0 问题。
- JaCoCo：行覆盖率 50%、分支覆盖率 35% 门槛通过。
- 前端 `npm run coverage`：28 个测试文件、222 个测试通过。
- 前端全源码覆盖率：语句 12.5%、分支 8.11%、函数 10.27%、行 12.9%。
- 真实 MySQL Testcontainers 历史全量基线：5 个测试类、55 个用例。
- `docker compose config --quiet`：通过。
- 核心用户与管理员流程：5 条真实 Docker Playwright E2E。

文档与 Agent 工具基线（Round 169）：

- 正式文档按产品、架构、开发、参考、项目和展示职责组织。
- API 参考按 7 个业务域拆分，并自动核对 26 个 Controller 的 151 个映射。
- 数据库参考按领域拆分，并自动核对 Flyway V1–V19 创建的 29 张表。
- 架构拆分为总览、前端、后端、AI、数据流和部署。
- 3 个 LearnPlatform 自有 Skill 与 7 个 `ui-ux-pro-max-cli` 上游 Skill 统一位于 Codex 标准目录 `.agents/skills`。
- 上游 Skill 由 `ui-ux-pro-max-cli@2.11.3` 使用 `uipro init --ai codex --force` 生成；项目文档校验会检查所有权清单、名称和遗留目录。
- 3 个项目自有 Skill 与上游 `ui-ux-pro-max` 通过系统 `quick_validate.py`；另外 6 个上游子 Skill 因原始 frontmatter 含 `argument-hint` 未通过当前系统校验器。

以上数字是最近一次已记录验证，不保证未执行新验证时自动更新。

## 当前遗留问题

- 前端 ESLint 无阻断错误，但仍有存量显式 `any` 警告，应按修改范围逐步收紧。
- 生产依赖审计仍有 ECharts 中危公告，升级存在破坏性变化，应配合可视化页面回归单独处理。
- Phase 22 的真实样本尚不足以支持课程或用户基础分层结论。
- 6 个上游子 Skill 的 `argument-hint` frontmatter 与当前系统校验器不兼容；不直接修改生成内容，等待上游修正或升级后重新验证。
- 上游 `ui-ux-pro-max/scripts/design_system.py` 含空白尾随字符，导致包含该生成文件的全量 `git diff --check` 报警；项目自有差异仍需单独通过检查。
- 部分上游设计工作流仍声明当前未安装的辅助 Skill，或保留 `.claude/skills` 示例路径；使用相关能力时必须先核对真实依赖和当前 `.agents/skills` 路径。

## 下一步

1. 继续积累 `ai_asset_view`、`ai_variant_training`、`ai_variant_question` 与 `practice_record` 的真实样本。
2. 至少两个难度档分别达到 5 条首次判分且覆盖 3 位学习者后，再评估分层观察。
3. 样本充分后比较课程分层与用户基础分层的价值和复杂度。

## 暂不优先

- PDF 或图片 OCR。
- 爬虫。
- 用户上传题库自动入库。
- AI 自动审核发布题目。
- 复杂向量推荐系统。

最后整理日期：2026-07-31。
