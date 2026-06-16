# Agent Git 规则

本文档记录 LearnPlatform 项目中 AI Agent 处理 Git、commit、分支、回滚和历史操作时必须遵守的规则。适用于 Codex、Cline、Mimo、GPT 或其他 Agent。

---

## 1. 基本原则

- Agent 不允许在用户未明确要求时自行创建 commit。
- Agent 不允许在用户未确认时 push、force push、rebase、reset、清理历史或改写远端分支。
- 遇到冲突、历史改写、回滚、分支合并异常或不确定的 Git 状态时，应暂停并让用户确认。
- 不要为了“工作区干净”而删除、回滚或覆盖用户未明确授权的改动。
- 一次提交只描述一件事，避免把无关功能、修复和文档混在同一个 commit。

---

## 2. Commit 前检查

准备 commit 前必须确认：

- 用户已经明确要求提交；
- 修改范围符合本轮用户要求；
- 必要测试、构建或文档检查已执行，或已说明未执行原因；
- 相关文档已同步；
- `git status` 中没有不应提交的文件；
- 未验证项、遗留问题和风险已在总结中说明。

禁止提交：

- `.env`；
- 真实 API Key、Token、Cookie、数据库密码或个人隐私数据；
- 本地 IDE 配置、机器私有配置；
- `node_modules/`、构建产物、缓存、日志；
- 与本轮任务无关的文件。

---

## 3. Commit Message 规范

格式：

```text
<type>(<可选作用域>): <中文简短描述>

<可选：详细说明>
<可选：Closes #42>
```

大多数提交一行即可。`type` 只能使用：

- `feat`：新功能
- `fix`：修 bug
- `docs`：文档
- `style`：代码格式，不影响逻辑
- `refactor`：重构
- `perf`：性能优化
- `test`：测试
- `chore`：构建、工具、依赖
- `ci`：CI/CD
- `security`：安全

要求：

- 提交信息必须使用中文描述；
- 修改涉及特定模块时优先添加作用域；
- 禁止使用 `fix bug`、`wip`、`修改了一些文件`；
- 禁止写成 `feat: 添加A顺便修了B` 这类混合事项描述。

示例：

```text
feat(auth): 完成 JWT 登录注册流程
```

```text
docs(roadmap): 更新项目阶段进度
```

```text
fix(question): 修复题目选项保存失败问题
```

本轮文档规则整理建议 commit message：

```text
docs(agent): 重构 Agent 协作规则文档
```

---

## 4. 分支与远端操作

- 创建新分支前应确认当前分支、工作区状态和用户意图。
- 合并、变基、挑选提交、回滚提交前，应说明影响范围并等待确认。
- `git reset --hard`、`git clean`、`push --force`、删除分支、删除 tag 等危险操作必须获得用户明确确认。
- 如果用户只要求给出建议 commit message，不要实际 commit。

---

## 5. 冲突与异常处理

- 遇到 merge/rebase conflict 时，先说明冲突文件和冲突性质，不要随意选择一边覆盖。
- 发现工作区中有用户改动时，应保留并绕开；如果影响本轮任务，先说明情况再继续。
- 命令失败时先分析原因，不要盲目重复执行同一个失败命令。
