---
name: frontend-flow-test
description: Use this skill when Codex needs to perform temporary frontend browser flow verification for LearnPlatform, such as "simulate a user", "run through this page", "try the practice/exam/submission flow", "check the page in the browser", or manually verify a frontend change. It guides low-token, minimum-business-loop validation and when to escalate to formal Playwright E2E or debugging.
---

# Frontend Flow Test

Use this skill for temporary browser-based verification. It does not replace Vitest, backend tests, or Playwright E2E. Its job is to make ad hoc Agent browser checks small, relevant, and cheap. Use [testing policy](../../../docs/development/testing.md) for regression requirements and the isolated E2E environment.

## Core Rule

Run the smallest business loop that can verify the current task.

- If the user asks to test a specific feature, test only that feature's loop.
- If a code change affects one page, test that page plus the nearest save/submit/result path.
- Run multiple loops only when the change affects global layout, routing, auth, request infrastructure, shared backend rules, or the user explicitly asks for broad verification.
- For release, demo, or pre-commit confidence, prefer existing automated tests first, then add targeted browser smoke checks.

## Relationship To Existing Tests

- Prefer `npm test`, `npm run build`, backend tests, and existing Playwright E2E for durable regression protection.
- Use this skill when the user wants to see or sanity-check the running app, or when a visual/interaction issue is easier to verify in a real browser.
- If this browser check finds a real bug, decide whether to fix it and whether the fix needs a formal Vitest, backend, or Playwright regression test.

## Select The Running Environment

Follow the [service reuse rules](../../../docs/development/workflow.md#运行环境与服务复用) before opening a page.

- For ordinary development checks, reuse the running local learner at `http://localhost:5173` or admin at
  `http://localhost:5174/admin/`; confirm the actual address and working directory rather than assuming the defaults.
  Both normally proxy `/api` to the local backend on port 8080. If backend code changed, verify that process was restarted.
- Open admin routes on the admin origin; the learner development server does not host the separate admin application.
- Use the existing local environment for page changes and real API checks. Docker-hosted MySQL or Redis alone does not
  make this a full Docker verification task and does not require `app-up`.
- When the task requires container or Nginx behavior, follow the [Docker guide](../../../docs/getting-started/docker-development.md).
  Formal Playwright regression uses the isolated E2E lifecycle in the testing policy, not the daily development database.
- Missing credentials or unavailable Turnstile are configuration limitations, not reasons to rebuild the whole stack
  or bypass authentication. Report the unverified portion and continue independent checks where useful.

## Loop Selection

Choose one primary loop:

- Practice: login -> `/practice` -> start self-selected practice -> answer -> result dialog -> completion summary.
- Wrong questions: login -> answer one question incorrectly if needed -> `/wrong-questions` -> filter/update mastery/retry.
- Exam: login -> `/exams` -> start or continue exam -> answer -> submit -> result detail.
- Submission: normal user submits -> admin reviews -> approved question appears in question bank.
- Admin list: admin login -> target admin page -> filter/search/paginate/open edit dialog -> verify no layout or permission regression.
- Layout/navigation: login -> visit changed pages directly by URL -> verify desktop/mobile visibility, no overlap, and key actions visible.

Use `testuser / test123` for learner flows and `admin / admin123` for admin flows. If login CAPTCHA is present, ask for explicit permission before solving it, or let the user log in manually.

## Low-Token Browser Discipline

Default to targeted checks instead of full page dumps.

- Prefer direct URLs over menu clicking when the route is known.
- Read only the current URL, page title, key heading, selected button state, visible dialog title, or final numbers needed for the decision.
- Do not print full DOM snapshots unless the page state is unclear or a locator fails.
- Do not screenshot every step. Screenshot only for visual/layout verification or when reporting a UI defect.
- Avoid reading the whole body text. Scope to `main`, the dialog, or the changed component.
- Report concise checkpoints: route entered, action performed, result observed, and any anomaly.

## Escalation Ladder

When something fails, increase evidence gradually:

1. Check URL, title, key heading, and whether the expected button/dialog exists.
2. Read a small scoped DOM section around the target component.
3. Inspect browser console logs or relevant network/API status.
4. Take a screenshot only if visual evidence is needed.
5. Check backend/container logs when the UI shows API failure or stale data.
6. Recommend or add a formal regression test if the defect is real and repeatable.

## Output Format

Keep the final summary short:

- Scope: which loop was tested.
- Result: passed, failed, or partially verified.
- Key checkpoints: 3-6 concise observations.
- Issues: real bugs, likely data/setup problems, or test limitations.
- Follow-up: only suggest broader testing when the risk justifies it.

Identify whether verification used local development, full Docker, or isolated E2E, and note any existing login session,
mocked/intercepted data, or non-default port that affects interpretation. Preserve user-started services after the check.
