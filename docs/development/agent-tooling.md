# Agent 与 Skills

本文档说明 LearnPlatform 长期规则、项目 Skills、上游安装包和 Codex 官方目录之间的边界。

## 层级

| 层级 | 位置 | 所有者 | 用途 |
|---|---|---|---|
| 长期规则 | `/AGENTS.md` | LearnPlatform | 每次任务都遵守的稳定规则和文档路由 |
| 项目 Skills | `/.agents/skills/` | LearnPlatform | 仓库级项目工作流 |
| 上游 Skills | `/.agents/skills/` | `nextlevelbuilder/ui-ux-pro-max-skill` | 通过上游 CLI 生成的通用设计能力 |
| 系统或插件 Skills | Codex 运行环境 | 平台或插件提供方 | 文档、浏览器、GitHub 等通用能力 |

仓库级 Skill 统一位于 `.agents/skills`。项目自有和上游生成内容通过所有权清单与维护方式区分，不通过重复目录区分。

## 项目 Skills

| Skill | 触发条件 | 边界 |
|---|---|---|
| `frontend-design` | LearnPlatform 页面设计、重构、响应式或视觉审查 | 服从 Vue 3、Element Plus 和现有变量 |
| `frontend-flow-test` | 用户要求打开浏览器模拟或检查前端流程 | 只跑最小业务闭环，不替代自动测试 |

每个项目 Skill 使用：

```text
.agents/skills/<name>/
├── SKILL.md
└── agents/openai.yaml
```

`SKILL.md` 保存触发描述和工作流，`agents/openai.yaml` 保存 Codex UI 元数据。项目事实通过链接读取 `docs/`，不复制进 Skill。

## 上游 `ui-ux-pro-max-skill`

来源与安装方式：

- 上游仓库：`https://github.com/nextlevelbuilder/ui-ux-pro-max-skill`
- npm 包：`ui-ux-pro-max-cli`
- Codex 项目安装命令：`uipro init --ai codex --force`

生成清单：

- `banner-design`、`brand`、`design`、`design-system`
- `slides`、`ui-styling`、`ui-ux-pro-max`

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

## 依赖边界

- 使用不熟悉的 Skill 前完整阅读其 `SKILL.md`，并核对其引用的脚本、资源和辅助 Skill 是否真实存在。
- `brand` 和 `design-system` 需要品牌指南或 Token 时，以项目已有资产为准；缺少输入时先说明。
- `ui-styling` 的默认技术栈与本项目不同，不能直接替换 Element Plus。
- 上游内容中的路径或命令必须按当前仓库解析，不能根据示例路径猜测。

## 维护

- 不直接修改、移动或格式化上游生成的 7 个 Skill。
- 升级前确认工作区没有无关改动，然后执行：

  ```bash
  npm install -g ui-ux-pro-max-cli@latest
  uipro init --ai codex --force
  ```

- 升级后核对生成清单并运行 `python3 scripts/check-docs.py`；版本和单次验证结果只记入 changelog。
- 修改项目 Skill 后运行系统 `skill-creator` 的 `quick_validate.py`。
- Skill 与用户要求或 `AGENTS.md` 冲突时，以用户要求和仓库规则为准。
