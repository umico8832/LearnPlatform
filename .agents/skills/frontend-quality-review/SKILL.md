---
name: frontend-quality-review
description: Audit LearnPlatform Vue frontend code and rendered UI for accessibility, interaction states, responsive and layout defects, motion, typography, performance, maintainability, and product truth. Use when asked to review, critique, audit, inspect quality, check accessibility, or find UI and UX problems. Produce evidence-ranked findings rather than automatically redesigning or editing the code.
---

# Frontend Quality Review

Review the requested surface without silently implementing changes. Separate demonstrable defects and project-contract violations from evidence-based design concerns and subjective alternatives.

Route a review concerned only with animation, transitions, or motion opportunities to [interaction-motion](../interaction-motion/SKILL.md). Keep motion in this Skill only when it is one category within a broader frontend-quality review.

## Establish the review contract

1. Identify the requested files, route, flow, viewport, and whether the task is code review, rendered review, or both. If scope is absent, infer the narrowest relevant surface from the current task instead of expanding to the whole application.
2. Read [frontend-design](../frontend-design/SKILL.md), [current status](../../../docs/project/status.md), [frontend architecture](../../../docs/architecture/frontend.md), and [testing policy](../../../docs/development/testing.md).
3. Read [review matrix](references/review-matrix.md) and apply only categories relevant to the surface.
4. Read [framework-neutral principles](references/framework-neutral-principles.md) when reviewing component architecture or performance.
5. For a public homepage, marketing page, portfolio, resume showcase, or demo-facing surface, also read [marketing-showcase](../marketing-showcase/SKILL.md) and the product-fact sources it routes to.
6. Use [ui-ux-pro-max](../ui-ux-pro-max/SKILL.md) for targeted reference searches. Do not turn every database result into a requirement or run its design-system persistence during a review.
7. Use the vendored [Impeccable](../impeccable/SKILL.md) only when the user explicitly invokes it or authorizes its automated detector or specialized command. Inspect the selected command's setup and side effects first; an ordinary read-only review must not trigger a runtime download, Hook, `PRODUCT.md`, `DESIGN.md`, or surface-brief write. Treat detector output as leads that require contextual confirmation.
8. Use [frontend-flow-test](../frontend-flow-test/SKILL.md) when rendered or interaction evidence is required and a suitable local service is available.

## Evidence classes

Classify each observation before reporting:

- **Defect:** reproducible broken behavior, inaccessible interaction, invalid markup, hidden content, overflow, data or permission inconsistency, or another objective failure.
- **Project conflict:** contradiction with an authoritative repository contract, active viewport, stack boundary, token ownership, or product fact.
- **Evidence-based design issue:** hierarchy, consistency, comprehension, feedback, or density problem supported by the code, rendered UI, repeated pattern, or established usability guidance.
- **Suggestion:** a plausible alternative whose value depends on taste or a different design direction.
- **Unverified lead:** static detector or source-only observation requiring browser, assistive-technology, data-state, or performance confirmation.

Do not report a stylistic preference as a defect. Do not dismiss a real issue merely because it is consistent with the current design system.

## Review method

1. Inspect the implementation and its nearby shared rules, not only the named file in isolation.
2. Trace real interaction, data, error, and permission states when relevant.
3. Run deterministic checks where useful, then confirm each finding against Vue and Element Plus semantics and the actual component context.
4. Render representative states when the claim depends on layout, focus, animation, layering, or visual hierarchy.
5. Rank findings by user impact and confidence. Prefer a small set of actionable findings over a long unfiltered checklist.
6. For each defect, identify the smallest responsible layer: page, shared component, token, route, API contract, or content source.

## Output

Lead with findings in severity order. Include a precise file and line or route and state, the observed impact, the evidence class, and the smallest reasonable correction. Merge repeated symptoms that share one cause.

Then state:

- which code, routes, viewports, states, and automated checks were actually reviewed;
- what required browser, mobile, assistive-technology, real-data, or performance verification but was not run;
- whether the current design system itself appears to be the cause and therefore warrants a scoped shared change.

If no material findings remain, say so and name the meaningful coverage gaps. Do not invent issues to fill categories.

## Adapted sources

The audit categories and terse evidence style are informed by Vercel's `web-design-guidelines` reviewed at commit `063bee94c3f4df8453406c830b0a7df0f2860278`. Automated review capability is supplied by the vendored Impeccable Skill. General design references come from the existing UI UX Pro Max databases. Framework-specific React, Next.js, shadcn/ui, and Tailwind prescriptions are not applied directly to this Vue repository.
