# Agent 与 Skills 工具边界

本文档说明 LearnPlatform 中长期规则、项目 Skills 和第三方 Skills 的所有权与使用方式。

## 层级

| 层级 | 位置 | 所有权 | 用途 |
|---|---|---|---|
| 项目长期规则 | `/AGENTS.md` | LearnPlatform | 每次任务都必须遵守的规则和文档路由 |
| 项目 Skills | `/skills/` | LearnPlatform | 项目特有、跨 Agent 可复用的工作流 |
| 第三方 Skills | `/.codex/skills/` | 上游开源项目 | 通过上游默认安装方式进入项目的通用能力 |
| 系统 Skills | Codex/Agent 运行环境 | 平台提供方 | 平台内置的通用能力 |

## 第三方 `.codex/skills`

当前安装包含：

- `banner-design`
- `brand`
- `design`
- `design-system`
- `slides`
- `ui-styling`
- `ui-ux-pro-max`

管理规则：

- 保持上游目录、文件名和资源结构。
- 不为适配 LearnPlatform 直接修改上游文件。
- 安装和更新继续使用上游项目的默认方式。
- 本仓库目前没有保存可验证的安装清单、上游 URL 或版本锁；更新前应从用户提供的来源或真实安装记录确认，不能根据作者名猜测。
- 使用前完整阅读相关 `SKILL.md`；遇到它引用的其他 Skill、脚本或工具时核对真实可用性。
- 上游内容与项目规则冲突时，以用户当前要求和 `AGENTS.md` 为准。

当前已确认的依赖边界：

- `ui-styling` 明确面向 shadcn/ui、Radix UI 和 Tailwind，不用于默认 Vue 3 + Element Plus 实现。
- `ui-ux-pro-max` 可用于框架无关的 UX、可访问性、布局、动效和视觉质量判断；采用其中栈专属建议前需核对目标栈。
- `design` 与 `banner-design` 声明的 `ai-artist`、`ai-multimodal`、`chrome-devtools`、`assets-organizing`、`project-management` 当前未在仓库或 `~/.claude/skills` 找到；不得假装这些步骤已经执行。
- `brand` 与 `design-system` 的同步脚本期望品牌指南和 Design Token 文件；项目建立这些资产后再启用对应写入流程。

## 项目 `skills/`

| Skill | 用途 | 适用边界 |
|---|---|---|
| `frontend-design` | LearnPlatform 前端视觉方向与实现约束 | 必须服从 Vue 3、Element Plus 和现有设计变量 |
| `frontend-flow-test` | 临时浏览器最小业务闭环验收 | 不替代 Vitest、后端测试或 Playwright E2E |
| `context-handoff` | 用户明确要求交接时生成最小续接材料 | 不复制长期规则或完整历史 |

项目不复制平台已经提供且资源更完整的通用 `skill-creator`。

## UI 路由

1. UX、可访问性、布局或视觉判断：读取 `ui-ux-pro-max`。
2. LearnPlatform 页面落地：读取项目 `frontend-design`，并检查现有 Vue/Element Plus 代码。
3. 只有用户明确要求 shadcn/ui、Radix 或 Tailwind 时，才采用 `ui-styling` 的实现路径。
4. 浏览器临时验收：读取 `frontend-flow-test` 并结合[测试策略](testing.md)。

## 维护原则

- 不熟悉的 Skill 必须先阅读，不根据名称或目录猜测。
- Skill 只保存可重复工作流；项目事实放在 `docs/`，长期硬规则放在 `AGENTS.md`。
- Skill 引用正式文档，不复制大段规则。
- 修改项目 Skill 后验证 frontmatter、资源引用和应触发/不应触发边界。
