# Agent 与 Skills

本文档维护 LearnPlatform Agent 入口、项目 Skills 与上游安装包的职责和使用方式。

## 层级

| 层级 | 位置 | 所有者 | 用途 |
|---|---|---|---|
| Agent 入口 | `/AGENTS.md` | LearnPlatform | 必要底线与任务读取路由 |
| 专项规则 | `/docs/` | LearnPlatform | 按领域唯一维护开发、测试、Git 与文档等详细规则 |
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

上述 7 个目录均由上游 CLI 生成。不要在其中维护 LearnPlatform 专属规则；详细项目约束写入对应 docs
或项目自有 Skill，根 AGENTS.md 只保留底线和读取路由。

## 使用方式

Codex 起初只读取 Skill 的名称和描述。以下情况才读取完整 `SKILL.md`：

1. 用户使用 `$skill-name` 显式调用；
2. 当前任务与 description 明确匹配；
3. `AGENTS.md` 针对项目任务显式路由。

这些 Skill 不参与 Vue、Spring Boot、Maven、Vite 或 Docker 的运行时构建。

### 前端任务

前端美化、布局或交互优化前按以下顺序读取：

1. [ui-ux-pro-max](../../.agents/skills/ui-ux-pro-max/SKILL.md)：UX、布局、可访问性和视觉判断。
2. [frontend-design](../../.agents/skills/frontend-design/SKILL.md)：LearnPlatform 落地约束；检查真实 Vue 3、Element Plus 和现有设计变量。
3. 只有用户明确要求 Tailwind、shadcn/ui 或 Radix 时才采用 [ui-styling](../../.agents/skills/ui-styling/SKILL.md) 的技术栈。
4. 临时浏览器验收读取 [frontend-flow-test](../../.agents/skills/frontend-flow-test/SKILL.md)，并结合[测试策略](testing.md)。

横幅、品牌、设计系统和演示 Skill 只在对应产物任务中触发，不参与普通业务开发。

## 依赖边界

- 使用不熟悉的 Skill 前完整阅读其 `SKILL.md`，并核对其引用的脚本、资源和辅助 Skill 是否真实存在。
- 第三方 Skill 所需依赖不存在时明确说明，不能声称完成未执行的步骤。
- `brand` 和 `design-system` 需要品牌指南或 Token 时，以项目已有资产为准；缺少输入时先说明。
- `ui-styling` 的默认技术栈与本项目不同，不能直接替换 Element Plus。
- 上游内容中的路径或命令必须按当前仓库解析，不能根据示例路径猜测。

## 维护

### Agent 入口与专项规则

- 修改前按[文档维护规则](../index.md#文档维护)确定权威来源，并搜索相邻文档中的重复或冲突规定。
- 根 AGENTS.md 只保留普遍适用、缺失会造成实质问题的底线；任务流程、命令细节和领域例外进入对应 docs。
  不在根文件与专项文档中维护两份完整定义。
- 每个路由写清任务触发条件、读取时机和明确目标；避免只堆链接，也不要求所有任务读取全部指南。
- 新增内容时同时检查周围是否有失效规则；优先删重、合并或移动，不通过压缩长行维持表面简短。
- 验证入口时选取实际任务，确认可以找到必需规则且不会加载无关指南；无需为调整措辞建立字符串断言。
- 领域尚不需要独立规则时，复用现有 docs，不为目录对称而新增 AGENTS.md 或 Skill。

### Skills

- 不直接修改、移动或格式化上游生成的 7 个 Skill。
- 升级前确认工作区没有无关改动，然后执行：

  ```bash
  npm install -g ui-ux-pro-max-cli@latest
  uipro init --ai codex --force
  ```

- 升级后核对生成清单并运行 `python3 scripts/check-docs.py`；版本和单次验证结果只记入 changelog。
- 修改项目 Skill 后运行系统 `skill-creator` 的 `quick_validate.py`。
- Skill 与用户要求或仓库规则冲突时，以用户要求和仓库规则为准；详细事实与规范按[文档权威来源](../index.md#权威来源)核对。
