---
name: marketing-showcase
description: Design or redesign LearnPlatform's public homepage, product or marketing landing pages, portfolio and resume showcase pages, campaign pages, and demo-facing presentation surfaces. Use only for outward-facing persuasion or showcase work, not dashboards, tables, authentication, or multi-step learner and admin workflows. Adapts Taste Skill and Anthropic frontend-design methods to the repository's Vue 3, TypeScript, Element Plus, and CSS-variable stack.
---

# Marketing and Showcase Design

Create outward-facing pages with a specific point of view, credible product claims, and enough visual variety to avoid a generic SaaS template. This Skill is contextual and never becomes a global product-UI rule.

## Read first

1. Read the project [frontend-design](../frontend-design/SKILL.md), [frontend architecture](../../../docs/architecture/frontend.md), and [current status](../../../docs/project/status.md).
2. For product claims and roadmap boundaries, read the [product requirements](../../../docs/product/prd.md), [roadmap](../../../docs/product/roadmap.md), and relevant [demo](../../../docs/showcase/demo.md) or [resume](../../../docs/showcase/resume.md) guidance. Never infer capabilities from a mockup.
3. Read [design read and dials](references/design-read-and-dials.md) before choosing a direction.
4. Read [composition and content](references/composition-and-content.md) when shaping the page or selecting media.
5. Read [pattern vocabulary](references/pattern-vocabulary.md) only when comparing directions, escaping a repetitive composition, or naming a requested pattern.
6. For an existing page, also read [redesign and preflight](references/redesign-and-preflight.md).
7. For substantial animation, read [interaction-motion](../interaction-motion/SKILL.md). For final audit, read [frontend-quality-review](../frontend-quality-review/SKILL.md).

If using UI UX Pro Max for a targeted reference search, this Skill still owns the design decision. Do not generate or persist a UI UX Pro Max design system unless the user explicitly asks for one.

## Scope gate

Use this Skill for a public homepage, landing page, marketing campaign, portfolio, resume showcase, case study, launch page, or demo-facing narrative page. Do not apply its novelty, hero, social-proof, media, or section-variety heuristics to product dashboards, data tables, forms, authentication, or multi-step learning flows.

If one route mixes marketing and product work, apply these rules only to the outward-facing sections and keep task UI governed by `frontend-design`.

## Working method

1. Make a one-line design read covering page kind, audience, desired tone, and the visual family suggested by the product, not by a memorized trend.
2. Set three descriptive dials: `DESIGN_VARIANCE`, `MOTION_INTENSITY`, and `VISUAL_DENSITY`, each from 1–10. Infer them from the brief; do not use a fixed universal preset.
3. Define a compact page system: hierarchy, palette roles, typography roles, content width, grid and alignment, surfaces, imagery, and motion intent.
4. Pick one memorable device that expresses the product: a real workflow, credible result, characteristic learning artifact, typographic composition, illustration, image, or purposeful interaction. Keep the rest controlled.
5. Map every section to a job in the narrative. Remove sections, cards, labels, and copy that do not advance comprehension, trust, or action.
6. Implement with Vue 3, TypeScript, existing routing, Element Plus where appropriate, and project CSS variables. Correct or extend shared tokens when evidence supports it; do not create a second hidden design system.
7. Render and review the page, then run the preflight. Fix the composition, not only isolated CSS symptoms.

## Non-negotiable truthfulness

- Use real product facts, routes, screenshots, and metrics from authoritative project sources. Do not invent customers, testimonials, logos, outcomes, integrations, precision statistics, or feature availability.
- If an image or screenshot is needed but unavailable, use an honest labeled placeholder only during development and report the missing asset; do not ship fake UI or fabricate a product screen.
- Treat a live product component as a valid hero visual when it communicates the product better than stock imagery. A real image is useful when the brief needs it, not a mandatory quota.
- Keep user-facing copy free of design-process commentary and self-description.

## Stack boundary

Do not copy React, Next.js, Tailwind, shadcn/ui, or Motion-specific implementation from upstream references. Translate transferable ideas into Vue composition, semantic HTML, CSS Grid or Flexbox, CSS variables, and existing dependencies. Check `package.json` before importing any library, and require explicit user authorization for a stack or component-system change.

## Adapted sources

This Skill adapts the contextual brief reading, three design dials, anti-default discipline, composition vocabulary, redesign protocol, and preflight capabilities of Taste Skill at commit `ccbc15639c97057cbfcf32ecebc38ef716e4bb37` (MIT), together with the subject-grounded direction and restraint of Anthropic `frontend-design` at commit `34040c9c568585f6929bedeaad110ad08f079624` (Apache-2.0). See [source decisions](references/sources.md) for retained and deliberately changed assumptions.
