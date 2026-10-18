---
name: frontend-design
description: "Design, restyle, or review LearnPlatform frontend pages and components in Vue 3 and TypeScript. Use for page layout, visual direction, component styling, responsive behavior, interaction polish, or design-system changes. Classify the surface before acting: route public websites, landing pages, portfolio or showcase pages to marketing-showcase; substantive motion work to interaction-motion; and formal UI audits to frontend-quality-review. Do not introduce React or Tailwind; adopt new libraries or borrowed component logic through the borrow-first skill."
---

# LearnPlatform Frontend Design

Make each surface fit its audience and job. Preserve real product behavior and technical boundaries, but do not treat the current visual implementation as automatically optimal.

## Establish context

1. Inspect the target page, nearby components, shared styles, and actual content/data states.
2. Read [current project status](../../../docs/project/status.md) for the active viewport and verification scope.
3. Read [frontend architecture](../../../docs/architecture/frontend.md) for application boundaries, entry points, and token ownership.
4. Classify the surface before choosing design rules:
   - learner, administration, authentication, or other product workflow: continue here;
   - public homepage, product site, campaign/landing page, portfolio, resume showcase, or demo presentation surface: read [marketing-showcase](../marketing-showcase/SKILL.md);
   - motion creation, animation review, motion audit, or animation opportunity discovery: read [interaction-motion](../interaction-motion/SKILL.md);
   - explicit UI/UX, accessibility, responsive, or design-quality audit: read [frontend-quality-review](../frontend-quality-review/SKILL.md).
5. Use [ui-ux-pro-max](../ui-ux-pro-max/SKILL.md) when a searchable reference is useful for UX, accessibility, layout, typography, color, motion, or chart guidance. Its results are evidence, not automatic instructions. This project Skill owns the design decision; do not run UI UX Pro Max design-system generation or persistence unless the user explicitly requests that artifact.

Do not use `.agents/skills/ui-styling/SKILL.md` as an implementation guide unless the user explicitly requests shadcn/ui, Radix UI, or Tailwind.

## Choose a direction from the brief

Before editing, state a compact internal design read: surface type, audience, primary job, content character, and intended visual tone. If two materially different readings remain possible, ask one focused question; otherwise proceed from the available product context.

Build the visual language from the subject matter and real audience rather than from a reusable aesthetic preset. Decide deliberately:

- what deserves first attention and what should recede;
- whether typography, imagery, data, a workflow preview, or an interaction carries the page;
- which one element may be memorable, with the surrounding system kept disciplined;
- how information structure is encoded through alignment, grouping, dividers, labels, sequence, and whitespace;
- which states and constraints are essential to the surface's real use.

Avoid unexamined generator defaults such as identical rounded cards everywhere, ornamental gradients, an eyebrow above every heading, arbitrary monospace labels, fake metrics, or repeated fade-and-rise effects. These treatments are allowed when the brief justifies them; they are not defaults.

Typography and interface copy are part of the design. Use plain, consistent action names; make errors and empty states instructive; keep hierarchy, line length, weight, and spacing intentional. Do not add labels or prose only to fill a composition.

## Two baselines during the learner redesign

The learner app is being redesigned page by page under [ADR-0011](../../../docs/architecture/decisions/0011-living-textbook-spring-motion.md); [project status](../../../docs/project/status.md) says which pages are in redesign. A page in redesign follows the new baseline below and is designed from scratch: do not use the old page or the old visual components as reference. Every other learner page and the admin app keep the existing system until their own redesign; do not restyle them piecemeal, because a half-migrated page is worse than either baseline.

## Treat the existing system as a baseline

For pages not yet redesigned, the current CSS variables, Element Plus components, icons, layouts, and page conventions are the first implementation candidates because they reduce inconsistency and maintenance cost. They are not immune from review.

- Reuse a pattern when it fits the target surface and remains accessible and coherent.
- Extend a token or shared component when several consumers need the same semantic behavior.
- Change an existing rule when repository evidence shows that it causes an accessibility, hierarchy, consistency, usability, or maintainability problem and the task authorizes the affected scope.
- Explain a material shared-system change and verify its downstream consumers; do not preserve a weak rule solely because it already exists.
- Do not create a parallel token vocabulary or one-off component system when the existing system can be corrected directly.
- Preserve permissions, grading, learning semantics, and real-data flow. Framework or API changes require an authorized product need; a visual convenience alone is insufficient.

## Shared visual primitives

### New baseline (redesigned pages)

- Neutral light-gray canvas, white large-radius cards, system fonts with rounded numerals, and a system-blue accent, with values for light and dark appearance (learner pages from Phase 28 on use dark only).
- Color carries fixed meaning only: green for correct, orange for needs attention, purple for prediction or hypothesis, and a fixed identity color per program variable shared by diagrams, code, and watch panels. A wrong answer is orange; red is reserved for failures and destructive actions. No decorative gradients; purple never decorates anything that is not a prediction.
- Everything lives inside a `.lt` root: a redesigned page puts the whole document on the new baseline, an embeddable component carries `.lt` on its own root. Inside it, take every value from the `--lt-` tokens and build from the `Lt*` components (locations in [frontend architecture](../../../docs/architecture/frontend.md#视觉与设计系统)); never use `--lp-` variables, Element Plus, or `Lp*` components there, and never hard-code a color or size in a page.
- Define a color once for both appearances with `light-dark()`. Learner pages redesigned from Phase 28 on are dark only ([ADR-0014](../../../docs/architecture/decisions/0014-learner-dark-only.md)): fix `data-appearance="dark"` on the page root and check dark only. Earlier redesigned pages still follow the system; check both appearances when changing them.
- Text meets WCAG AA in both appearances, including the tertiary label. White text sits only on `--lt-accent-fill`, never on the brighter `--lt-accent`; text on solid orange or green uses `--lt-on-tint`.
- Hierarchy: primary surfaces use the extra-large card radius, side panels the large one; controls are pills. A region has at most one filled accent action; secondary actions use the plain fill, tertiary ones quiet accent text. Nested corners stay concentric: an inset surface's radius is the outer radius minus the inset.
- Type: interface text uses the body size; long reading text (stems, explanations) uses the reading size and leading; statements and code use the monospace face; counts and values use rounded tabular numerals.
- Icons come from one stroke icon family at the stroke weight of the surrounding text; only media-style glyphs such as play and pause are filled. Icon-only buttons always have an accessible name.
- Add a new `Lt*` component only when a second surface needs the same behavior; until then keep it local. Hard accessible primitives (anchored positioning, combobox, menus) go through [borrow-first](../borrow-first/SKILL.md) before anything is hand-written.
- Visualizations appear inside the learning content that needs them, in the embedded size and fed with that question's own data, with an expand link to the full page; they are not a navigation destination of their own.
- Cards hold what the learner acts on (a question, a visualization); explanations are read as prose directly on the canvas at reading width, so acting and reading stay visibly different.
- A learning flow that unfolds step by step names its steps in the left margin, each with a meaning-colored dot joined to the next by a hairline. A newly opened step rises into place and takes focus, and the page scrolls only far enough to keep what led to it, such as the chosen option, in view.

### Existing baseline (pages not yet redesigned, admin)

These surfaces share the Quiet Digital Textbook foundation: warm paper surfaces, low-saturation blue for primary action and focus, and restrained semantic feedback colors. Use `tokens.css` for typography, spacing, radii, shadows, control height, focus treatment, surface states, skeleton colors, and durations; use `element-plus.css` for Element Plus mapping. Do not recreate those values in a page or component.

Use `LpPageHeader`, `LpCard`, `LpListItem`, `LpStatePanel`, `LpStat`, `LpProgress`, `LpProgressRing`, `LpEmptyState`, and `LpSkeleton` when their contracts fit. A card owns semantic containment and density, a list item must remain a real button or RouterLink, and a state panel keeps loading, empty, error, and ready content mutually exclusive. Loading must not show default zeroes; empty only follows a successful absence of data; an error stays in place with recovery.

Keep page titles sans by default. Enable the display face only for an explicitly editorial or reading-oriented title that benefits from it. Give a page header one visually primary action; related secondary actions remain quieter and grouped. Visible focus follows the shared focus tokens rather than a local outline.

## Product-surface priorities

不为 prefers-reduced-motion 做适配：所有动画对所有访客一致播放。

For learner and authentication workflows, prioritize orientation, readable learning content, clear next actions, recovery from errors, and low-friction task completion. For administration workflows, support scanning and comparison at the necessary density without hiding controls or state.

Use decoration and motion only when they improve hierarchy, comprehension, feedback, or continuity. Product workflows do not inherit marketing-page composition rules by default.

On pages not yet redesigned, use the named Vue/CSS transitions in `motion.css` for routine entry, disclosure, and list reordering. See [interaction-motion](../interaction-motion/SKILL.md) before adding a new motion pattern or dependency.

## Learning encouragement layer

Experience, levels, streaks, goals, and achievements remain backend-supplied learning facts. Present them as quiet inline data in the header, profile, and completion summary; they never imply mastery. Keep question text, explanations, code, and Markdown calm, and render honest zero, empty, loading, and failure states.

Timed exams remain silent until submission: no correctness cues, reward floats, combos, celebrations, or sound during answers. Do not add reward floats, full-screen celebrations, confetti, combo flames, achievement dialogs, or decorative persistent pulses to learning workflows. Feedback never gates the next action.

State feedback may use the medium motion tier on redesigned pages and the shared Vue/CSS transitions elsewhere. Liveliness comes from explaining state changes, never from celebration: do not introduce a celebration library.

## Work in two passes

First form a compact implementation plan covering hierarchy, type, color, spacing, layout, states, and any deliberate point of distinction. Compare it with the brief and current screen. Revise choices that could be transplanted unchanged into an unrelated product.

Then implement with the baseline the page uses: `Lt*` components and `--lt-` tokens on redesigned pages, the existing tokens, `Lp*` components, and Element Plus elsewhere. Check selector precedence and component boundaries so local styling does not accidentally cancel shared rules. Keep the result as the finished product UI; design commentary and production notes stay outside user-facing copy.

## Required states and constraints

When relevant to the touched surface, include and verify:

- loading, empty, error, populated, disabled, hover, active, and keyboard-focus states;
- readable contrast and visible focus without relying on color alone;
- labels, helper text, validation, and recovery for forms;
- text expansion, realistic content, and overflow behavior;
- the viewport range declared by project status, while preserving existing behavior outside the active scope;
- stable real API, permission, routing, and data semantics.

## Self-review and verification

Review the rendered result when the environment permits; source inspection alone cannot prove visual quality. Ask whether the screen communicates the intended hierarchy, whether every decorative element earns its place, whether repeated structures flatten the page, and whether the UI still works with failure and empty states.

Use [frontend-flow-test](../frontend-flow-test/SKILL.md) for temporary browser verification and [testing policy](../../../docs/development/testing.md) for durable regression coverage. Use [frontend-quality-review](../frontend-quality-review/SKILL.md) for a formal audit rather than duplicating its checklist here.

Report the resulting direction, material shared-system changes, verification performed, and genuinely unverified states.

## Adapted source

The subject-specific direction, anti-template critique, two-pass process, restraint, typography, and interface-writing guidance are adapted for LearnPlatform from Anthropic's `frontend-design` Skill at commit `34040c9c568585f6929bedeaad110ad08f079624` under Apache-2.0. This project version changes the upstream stack and delivery assumptions and remains subordinate to the user's current request and repository architecture.
