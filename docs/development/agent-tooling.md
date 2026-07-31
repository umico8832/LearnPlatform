# Agent 与 Skills

本文档说明 LearnPlatform 长期规则、项目 Skills、上游安装包和 Codex 官方目录之间的边界。

## 层级

| 层级 | 位置 | 所有者 | 用途 |
|---|---|---|---|
| 长期规则 | `/AGENTS.md` | LearnPlatform | 每次任务都遵守的稳定规则和文档路由 |
| 项目 Skills | `/.agents/skills/` | LearnPlatform | 仓库级项目工作流 |
| 上游 Skills | `/.agents/skills/` | `nextlevelbuilder/ui-ux-pro-max-skill` | 通过上游 CLI 生成的通用设计能力 |
| 系统或插件 Skills | Codex 运行环境 | 平台或插件提供方 | 文档、浏览器、GitHub 等通用能力 |

OpenAI 当前文档把仓库级独立 Skill 标准目录定义为 `.agents/skills`。上游 `ui-ux-pro-max-skill` 已在 2026-07-27 将 Codex 默认安装目录从 `.codex/skills` 修正为 `.agents/skills`，因此两类 Skill 共享标准目录，通过所有权清单和维护方式区分。

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

## 上游 `ui-ux-pro-max-skill`

来源和安装基线：

- 上游仓库：`https://github.com/nextlevelbuilder/ui-ux-pro-max-skill`
- npm 包：`ui-ux-pro-max-cli`
- 当前生成版本：`2.11.3`
- Codex 项目安装命令：`uipro init --ai codex --force`

当前生成清单来自各 Skill frontmatter：

| Skill | 声明版本 | 主要用途 |
|---|---:|---|
| `banner-design` | 1.0.0 | 社交、广告和网站横幅 |
| `brand` | 1.0.0 | 品牌声音、视觉身份和资产一致性 |
| `design` | 2.1.0 | 品牌、图标、演示和综合设计路由 |
| `design-system` | 1.0.0 | Token、组件规范和系统设计 |
| `slides` | 1.0.0 | HTML 战略演示文稿 |
| `ui-styling` | 1.0.0 | shadcn/ui、Radix 和 Tailwind UI |
| `ui-ux-pro-max` | 未声明 | 框架无关的 UX、布局和视觉判断 |

上述 7 个目录均由上游 CLI 生成。不要在其中维护 LearnPlatform 专属规则；项目约束应写入 `AGENTS.md` 或项目自有 Skill。

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

- `banner-design`、`brand`、`design`、`design-system`、`slides` 和 `ui-styling` 的上游 frontmatter 含 `argument-hint`，当前系统 `skill-creator` 校验器不接受该字段；不要直接修改生成文件规避上游问题。
- `ui-ux-pro-max/scripts/design_system.py` 的上游生成版本含空白尾随字符，会让全量 `git diff --check` 报警；项目自有差异应单独通过检查，不直接格式化上游文件。
- `design` 和 `banner-design` 声明的部分辅助 Skill 当前不在项目安装清单中，不能假装已经调用。
- `brand` 和 `design-system` 的部分脚本需要项目先提供品牌指南或 Token 文件。
- `ui-styling` 默认技术栈与本项目不同，不能直接替换 Element Plus。
- 第三方 Skill 中残留的 `.claude/skills` 路径属于上游内容；使用相关脚本前必须解析真实文件位置。

## 维护

- 不直接修改、移动或格式化上游生成的 7 个 Skill。
- 升级前确认工作区没有无关改动，然后执行：

  ```bash
  npm install -g ui-ux-pro-max-cli@latest
  uipro init --ai codex --force
  ```

- 升级后核对上游变更、运行 `python3 scripts/check-docs.py`，分别记录项目 Skill、上游 Skill 和全量 `git diff --check` 的真实结果，并在 changelog 记录实际 CLI 版本。
- 不在 `.codex/skills` 或其他目录保留同名副本，避免 Codex 暴露重复 Skill。
- 修改项目 Skill 后运行系统 `skill-creator` 的 `quick_validate.py`。
- Skill 与用户要求或 `AGENTS.md` 冲突时，以用户要求和仓库规则为准。
