# Skills 目录

> 本目录用于存放 Cline/Claude skills。
> 仅本地使用，不提交到仓库。

## 使用说明

本目录供开发者手动添加自定义 skill 文件，扩展 Cline 的辅助能力。

**除非用户要求，否则不要自动生成任何 SKILL.md 文件。**

## 目录结构

每个 skill 占一个独立子目录，包含 `SKILL.md` 作为主文件，可选附带 `assets/`、`references/`、`scripts/` 等资源。

```
skills/
├── README.md
├── context-handoff/
│   └── SKILL.md
├── frontend-design/
│   └── SKILL.md
└── skill-creator/
    ├── SKILL.md
    ├── assets/
    ├── references/
    └── scripts/
```

## 已导入的 Skill

| Skill 名称 | 目录 | 用途 |
|------------|------|------|
| `frontend-design` | `frontend-design/` | 创建独特、生产级的前端界面，避免通用 AI 美学，注重排版、色彩、动效和空间构图 |
| `skill-creator` | `skill-creator/` | 创建新 skill、修改和改进现有 skill、运行评估和基准测试以衡量 skill 性能 |
| `context-handoff` | `context-handoff/` | 当用户说"请生成摘要"时，生成新 Task 交接摘要和可复制的新任务启动提示词 |

## 建议后续可导入的 Skill 类型

| Skill 名称 | 用途 |
|------------|------|
| `bugfix-minimal-change` | Bug 最小改动修复流程 |
| `frontend-ux-check` | 前端交互体验检查清单 |

## 与项目规则的关系

- 所有 skills 必须结合项目根目录 `AGENTS.md` 使用
- Skills 不能覆盖或冲突于 `AGENTS.md`
- 如果 skill 内容与 `AGENTS.md` 不一致，以 `AGENTS.md` 和用户当前明确要求为准

## 本地规则

- 本目录中的项目级 skills 已纳入 Git，用于不同 Agent 共享一致工作流
