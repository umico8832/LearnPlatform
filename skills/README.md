# LearnPlatform 项目 Skills

本目录保存由 LearnPlatform 维护或显式依赖的跨 Agent 工作流。第三方开源 Skill 包位于 `.codex/skills/`，两者所有权和更新方式不同。

## 项目 Skills

| Skill | 用途 |
|---|---|
| `frontend-design` | 将通用设计判断落到 LearnPlatform 的 Vue 3、Element Plus 和现有设计变量 |
| `frontend-flow-test` | 临时浏览器最小业务闭环验收 |
| `context-handoff` | 用户明确要求交接时生成最小、可验证的续接材料 |

## 第三方 Skills

`.codex/skills/` 由上游开源项目按默认安装方式维护。不要把其中内容复制、移动或手工改造成项目 Skill；具体边界见 [`docs/development/agent-tooling.md`](../docs/development/agent-tooling.md)。

## 规则

- 使用前完整阅读对应 `SKILL.md`。
- 项目事实引用 `docs/` 权威文档，不在 Skill 中复制。
- 长期硬规则写入根目录 `AGENTS.md`。
- Skill 与用户要求或 `AGENTS.md` 冲突时，以用户要求和 `AGENTS.md` 为准。
- 创建或维护 Skill 使用当前 Agent 平台提供的系统 `skill-creator`，项目不保存不完整副本。
