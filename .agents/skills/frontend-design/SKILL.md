---
name: frontend-design
description: "Design, restyle, or review LearnPlatform frontend pages and components in Vue 3 and TypeScript. Use for page layout, visual direction, component styling, responsive behavior, interaction polish, or design-system changes. Classify the surface before acting: route public websites, landing pages, portfolio or showcase pages to marketing-showcase; substantive motion work to interaction-motion; and formal UI audits to frontend-quality-review. Do not introduce React, shadcn/ui, Tailwind, or a new component library unless the user explicitly requests a stack change."
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

## Treat the existing system as a baseline

The current CSS variables, Element Plus components, icons, layouts, and page conventions are the first implementation candidates because they reduce inconsistency and maintenance cost. They are not immune from review.

- Reuse a pattern when it fits the target surface and remains accessible and coherent.
- Extend a token or shared component when several consumers need the same semantic behavior.
- Change an existing rule when repository evidence shows that it causes an accessibility, hierarchy, consistency, usability, or maintainability problem and the task authorizes the affected scope.
- Explain a material shared-system change and verify its downstream consumers; do not preserve a weak rule solely because it already exists.
- Do not create a parallel token vocabulary or one-off component system when the existing system can be corrected directly.
- Do not change the framework, API contracts, permissions, learning rules, or real-data flow for visual convenience.

## Product-surface priorities

For learner and authentication workflows, prioritize orientation, readable learning content, clear next actions, recovery from errors, and low-friction task completion. For administration workflows, support scanning and comparison at the necessary density without hiding controls or state.

Use decoration and motion only when they improve hierarchy, comprehension, feedback, or continuity. Product workflows do not inherit marketing-page composition rules by default.

## Work in two passes

First form a compact implementation plan covering hierarchy, type, color, spacing, layout, states, and any deliberate point of distinction. Compare it with the brief and current screen. Revise choices that could be transplanted unchanged into an unrelated product.

Then implement in the repository's Vue 3, TypeScript, Element Plus, ECharts, and CSS-variable stack. Check selector precedence and component boundaries so local styling does not accidentally cancel shared rules. Keep the result as the finished product UI; design commentary and production notes stay outside user-facing copy.

## Required states and constraints

When relevant to the touched surface, include and verify:

- loading, empty, error, populated, disabled, hover, active, and keyboard-focus states;
- readable contrast and visible focus without relying on color alone;
- labels, helper text, validation, and recovery for forms;
- text expansion, realistic content, and overflow behavior;
- the viewport range declared by project status, while preserving existing behavior outside the active scope;
- reduced-motion behavior for nonessential animation;
- stable real API, permission, routing, and data semantics.

## Self-review and verification

Review the rendered result when the environment permits; source inspection alone cannot prove visual quality. Ask whether the screen communicates the intended hierarchy, whether every decorative element earns its place, whether repeated structures flatten the page, and whether the UI still works with failure and empty states.

Use [frontend-flow-test](../frontend-flow-test/SKILL.md) for temporary browser verification and [testing policy](../../../docs/development/testing.md) for durable regression coverage. Use [frontend-quality-review](../frontend-quality-review/SKILL.md) for a formal audit rather than duplicating its checklist here.

Report the resulting direction, material shared-system changes, verification performed, and genuinely unverified states.

## Adapted source

The subject-specific direction, anti-template critique, two-pass process, restraint, typography, and interface-writing guidance are adapted for LearnPlatform from Anthropic's `frontend-design` Skill at commit `34040c9c568585f6929bedeaad110ad08f079624` under Apache-2.0. This project version changes the upstream stack and delivery assumptions and remains subordinate to the user's current request and repository architecture.
