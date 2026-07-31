---
name: frontend-design
description: Design, restyle, or review LearnPlatform frontend pages and components using the existing Vue 3, TypeScript, Element Plus, ECharts, and CSS-variable system. Use for page beautification, layout changes, interaction polish, responsive behavior, or visual consistency in this repository. Do not use it to introduce React, shadcn/ui, Tailwind, or a new component library unless the user explicitly requests a stack change.
---

# LearnPlatform Frontend Design

Create usable, coherent learning and administration interfaces that fit the existing product instead of imposing an unrelated visual template.

## Read first

1. Inspect the target Vue component and nearby shared styles.
2. Read `docs/architecture/frontend.md` for frontend boundaries.
3. Read `.agents/skills/ui-ux-pro-max/SKILL.md` when the task needs UX, accessibility, layout, interaction, typography, color, motion, or chart guidance.
4. Reuse existing global CSS variables, Element Plus components, ECharts patterns, icons, and page conventions.

Do not use `.agents/skills/ui-styling/SKILL.md` as an implementation guide unless the user explicitly requests shadcn/ui, Radix UI, or Tailwind.

## Product direction

- Treat the product as a learner and administrator workbench, not a marketing landing page.
- Prefer clear hierarchy, predictable navigation, restrained decoration, readable data, and obvious next actions.
- Keep learner pages supportive and task-oriented.
- Keep administration pages dense enough for work without sacrificing scanability or accessibility.
- Preserve visual continuity with completed Phase 21 pages unless the task explicitly changes the design system.

## Implementation rules

- Stay within Vue 3, TypeScript, Element Plus, ECharts, and the current CSS architecture.
- Prefer existing tokens and semantic variables over new raw colors.
- Reuse established spacing, radius, surface, typography, table, form, dialog, empty-state, and responsive patterns.
- Keep one clear primary action per page or section.
- Add loading, empty, error, disabled, hover, focus, and mobile states when relevant.
- Do not replace working real-data flows with mocks.
- Do not change API contracts or business rules for visual convenience.
- Avoid decorative motion that delays or obscures learning tasks.

## Verification

Follow `.agents/skills/frontend-flow-test/SKILL.md` for targeted browser checks and `docs/development/testing.md` for durable regression protection.

At minimum verify:

- desktop and mobile layout;
- keyboard-visible focus for changed interactive controls;
- loading, empty, error, and populated states that the change touches;
- no horizontal overflow or obscured fixed content;
- existing real API and permission behavior remains intact.

Report the chosen direction, reused project patterns, verification performed, and any unverified state.
