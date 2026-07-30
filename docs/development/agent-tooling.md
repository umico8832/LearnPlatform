# Agent 与 Skills

本文档说明 LearnPlatform 长期规则、项目 Skills、第三方安装包和 Codex 官方目录之间的边界。

## 层级

| 层级 | 位置 | 所有者 | 用途 |
|---|---|---|---|
| 长期规则 | `/AGENTS.md` | LearnPlatform | 每次任务都遵守的稳定规则和文档路由 |
| 项目 Skills | `/.agents/skills/` | LearnPlatform | 仓库级可复用工作流 |
| 第三方安装包 | `/.codex/skills/` | 上游开源项目 | 通过该上游默认方式安装的通用设计能力 |
| 系统或插件 Skills | Codex 运行环境 | 平台或插件提供方 | 文档、浏览器、GitHub 等通用能力 |

OpenAI 当前文档把仓库级独立 Skill 标准目录定义为 `.agents/skills`。本项目的第三方开源包仍按其上游安装方式保存在 `.codex/skills`；当前 Codex Desktop 会话能够发现这些 Skill，但这项实测兼容行为不等于把 `.codex/skills` 声明为通用标准目录。

## 项目 Skills

| Skill | 触发条件 | 边界 |
|---|---|---|
| `frontend-design` | LearnPlatform 页面设计、重构、响应式或视觉审查 | 服从 Vue 3、Element Plus 和现有变量 |
| `frontend-flow-test` | 用户要求打开浏览器模拟或检查前端流程 | 只跑最小业务闭环，不替代自动测试 |
| `context-handoff` | 用户明确要求交接、续接提示或上下文转移 | 不用于普通进度总结 |

每个项目 Skill 使用：

```text
.agents/skills/<name>/
├── SKILL.md
└── agents/openai.yaml
```

`SKILL.md` 保存触发描述和工作流，`agents/openai.yaml` 保存 Codex UI 元数据。项目事实通过链接读取 `docs/`，不复制进 Skill。

## 第三方 `.codex/skills`

当前安装清单来自各 Skill frontmatter：

| Skill | 声明版本 | 主要用途 |
|---|---:|---|
| `banner-design` | 1.0.0 | 社交、广告和网站横幅 |
| `brand` | 1.0.0 | 品牌声音、视觉身份和资产一致性 |
| `design` | 2.1.0 | 品牌、图标、演示和综合设计路由 |
| `design-system` | 1.0.0 | Token、组件规范和系统设计 |
| `slides` | 1.0.0 | HTML 战略演示文稿 |
| `ui-styling` | 1.0.0 | shadcn/ui、Radix 和 Tailwind UI |
| `ui-ux-pro-max` | 未声明 | 框架无关的 UX、布局和视觉判断 |

仓库目前没有可验证的上游 URL、安装命令和版本锁，因此不补写猜测来源。获得用户真实安装记录后，再增加可复现安装清单。

## 使用方式

Codex 起初只读取 Skill 的名称和描述。以下情况才读取完整 `SKILL.md`：

1. 用户使用 `$skill-name` 显式调用；
2. 当前任务与 description 明确匹配；
3. `AGENTS.md` 针对项目任务显式路由。

这些 Skill 不参与 Vue、Spring Boot、Maven、Vite 或 Docker 的运行时构建。

前端任务的项目路由：

1. UX、布局、可访问性和视觉判断读取 `ui-ux-pro-max`；
2. LearnPlatform 实现读取项目 `frontend-design`；
3. 只有用户明确要求 Tailwind、shadcn/ui 或 Radix 时才采用 `ui-styling`；
4. 临时浏览器验收读取 `frontend-flow-test`。

横幅、品牌、设计系统和演示 Skill 只在对应产物任务中触发，不参与普通业务开发。

## 已知依赖边界

- `design` 和 `banner-design` 声明的部分辅助 Skill 当前不在项目安装清单中，不能假装已经调用。
- `brand` 和 `design-system` 的部分脚本需要项目先提供品牌指南或 Token 文件。
- `ui-styling` 默认技术栈与本项目不同，不能直接替换 Element Plus。
- 第三方 Skill 中残留的 `.claude/skills` 路径属于上游内容；使用相关脚本前必须解析真实文件位置。

## 维护

- 不直接修改、移动或格式化 `.codex/skills`。
- 第三方升级使用真实上游安装方式。
- 不同时保留 `.agents/skills` 和其他目录下的同名项目 Skill。
- 修改项目 Skill 后运行系统 `skill-creator` 的 `quick_validate.py`。
- Skill 与用户要求或 `AGENTS.md` 冲突时，以用户要求和仓库规则为准。
