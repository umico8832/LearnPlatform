---
name: context-handoff
description: Generate a concise, evidence-based LearnPlatform handoff when the user explicitly asks for a summary, handoff, continuation prompt, or context transfer. Do not trigger for ordinary progress updates or final task summaries.
---

# Context Handoff

Generate the minimum context another Agent needs to continue safely. Do not repeat the full project history or all long-term rules.

## Read first

1. Check `git status --short`.
2. Check the current task diff when changes exist.
3. Read `docs/project/status.md`.
4. Read only the task-relevant parts of `docs/project/changelog/index.md` and its linked month.
5. Use `AGENTS.md` as the long-term rule source.

Do not inspect or expose `.env`, credentials, tokens, cookies, database passwords, or private user data.

## Update project handoff

When the user requests a repository handoff artifact, update `docs/project/handoff.md` with:

- current objective;
- completed work;
- unfinished work;
- changed files;
- validation already run;
- known risks or blockers;
- next 1–3 actions;
- a minimal continuation prompt.

Link to `docs/project/status.md` instead of copying current phase, test counts, and all known issues. Link to the changelog instead of copying previous rounds.

## Conversation output

Use this structure:

```markdown
# 交接摘要

## 当前目标

## 已完成

## 未完成

## 修改文件

## 验证

## 风险与限制

## 下一步

## 续接提示词
```

Distinguish facts verified from files or commands from inferences. If no files changed, say so explicitly.

## Boundaries

- Do not modify business code while preparing a handoff.
- Do not commit, push, rebase, delete, install dependencies, or clean the worktree as part of summary generation.
- Do not include unrelated historical detail.
- Do not claim a test passed unless its command result is available.
