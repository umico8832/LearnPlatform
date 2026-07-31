# Agent 交接

本文件只保存跨对话继续工作所需的最小上下文。当前阶段、验证和遗留问题以[项目状态](status.md)为准，历史见[开发日志](changelog/index.md)。

## 当前目标

Phase 22 持续积累真实学习样本。在同题、跨题、资产类型和难度样本满足作答量与去重学习者门槛后，再评估课程或用户基础分层。

## 当前事实

- Phase 20 和 Phase 21 已完成。
- Phase 22 已具备真实资产查看、结构化变式首次判分、变式训练事件、同题/跨题观察、资产类型拆分、难度样本和独立学习者门槛。
- 当前不应直接把观察性结果解释为因果效果。
- 不优先做 OCR、爬虫、自动入库、自动发布或复杂向量推荐。

## 接手顺序

1. 阅读 `/AGENTS.md`。
2. 阅读 `docs/project/status.md`。
3. 阅读 `docs/product/roadmap.md`。
4. 根据任务读取 `docs/index.md` 路由到专项文档。
5. 检查真实代码和 `git status`，不要只依赖文档。

不需要默认全文读取历史 changelog。

## 运行

- 本地：[本地开发](../getting-started/local-development.md)
- Docker：[Docker 开发](../getting-started/docker-development.md)
- 测试：[测试策略](../development/testing.md)

## 注意事项

- 不读取或覆盖真实 `.env`。
- 项目 Skills 与上游安装的通用 Skills 均位于 `/.agents/skills`，所有权见[Agent 与 Skills](../development/agent-tooling.md)。
- 文档或状态与代码冲突时，先以真实代码验证，再修正文档。

## 续接提示词

```text
请接手 LearnPlatform。

先阅读：
1. AGENTS.md
2. docs/project/status.md
3. docs/product/roadmap.md
4. README.md

再根据 docs/index.md 读取与当前任务相关的最小文档集合，并检查真实代码与 git status。

当前主线是 Phase 22 AI 学习效果验证：继续积累真实样本，满足作答量和去重学习者门槛后再评估课程或用户基础分层。不要把观察性数据解释为因果结果，也不要回到 OCR、爬虫、自动入库或复杂向量推荐。
```
