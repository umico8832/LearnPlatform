---
name: interaction-motion
description: Build, review, audit, name, or find purposeful interaction motion in LearnPlatform's Vue 3 and Element Plus frontend. Use for transitions, learning feedback, celebrations, reduced-motion behavior, or motion-quality review; use frontend-design for page redesign.
---

# LearnPlatform Interaction Motion

Make state changes easier to understand without slowing a learning or administration task. Motion is optional: an instant change is the right outcome when animation adds no useful signal.

## Read first

Inspect the target component and its actual trigger path. Read `../../../frontend/src/assets/styles/tokens.css` and `../../../docs/architecture/frontend.md`; also use [frontend-design](../frontend-design/SKILL.md) for page-level visual work. Reuse the current Vue 3, TypeScript, Element Plus, and CSS-variable stack. Simple motion stays in CSS/Vue transitions. Animation libraries are allowed for evidenced needs beyond those primitives; check official compatibility, lifecycle cleanup, accessibility, and bundle cost. Load infrequent celebration effects on demand rather than increasing the initial route bundle.

The current `--lp-duration-*` and `--lp-ease-*` variables are the baseline, not an unchangeable law. Reuse them where they fit. Propose a token addition or adjustment only when repeated, evidenced component needs cannot be represented by that scale; keep tokens semantic and avoid a parallel local motion system.

## Choose a mode

- **Build** — add a requested transition or interaction. Follow the build gate below, then implement the smallest viable motion.
- **Review** — inspect supplied motion code or a focused diff. Report actionable findings with `file:line`, ordered by user impact; do not alter unrelated code.
- **Audit** — survey a defined frontend area for existing motion quality and consistency. Establish the token and interaction baseline before judging it.
- **Opportunities** — look for a small number of missing, defensible motion moments. Include rejected candidates so decoration is not mistaken for a gap.
- **Vocabulary** — name a described effect concisely. Read [motion vocabulary](references/motion-vocabulary.md) only for this mode.

For build, review, audit, and opportunities work, read [motion decision guide](references/motion-decision-guide.md). It contains the operating checks and project implementation guidance. For attribution and the upstream scope, read [source note](references/source-and-license.md).

## Learning feedback

Experience, streaks, goals, and achievements come from persisted backend learning events. Motion may acknowledge an answer or reward but never calculate or approve it. Keep answer reading areas calm and encouragement elements expressive. During timed exams, suppress all correctness, combo, reward, celebration, and sound cues until submission.

Use one celebration queue, deduplicate events, and merge bursts without dropping achievements. Clear pending feedback on account change, logout, or entry into an exam. Completion and level-up may have a larger finite delight budget; users can dismiss and continue immediately. Reduced motion retains the result and removes travel, scale, confetti, and loops.

## Shared boundaries

- Preserve real data flow, focus management, keyboard behavior, and Element Plus semantics. Animation must not defer an action, conceal validation, trap focus, or make an exit unavailable.
- Default to CSS transitions, Vue `<Transition>`, `@starting-style`, or CSS keyframes when their lifecycle fits. Use WAAPI only for programmatic, cancelable sequences that CSS cannot express. A custom composable is justified only for real gesture or lifecycle coordination; clean up listeners, observers, timers, and animations on unmount.
- Name transitioned properties; never use `transition: all`. Prefer `transform` and `opacity`, but do not claim they are universally compositor-only. For a layout-dependent effect such as an accordion, first choose an instant state change or a tested layout-aware technique; only animate layout with a clear benefit and performance verification.
- Include an intentional `prefers-reduced-motion` variant whenever movement, auto-play, or scroll-linked motion is added. It may keep a short opacity or color transition when that preserves comprehension, but removes nonessential travel, scale, repetition, and long staggering.
- Put hover-only motion behind `@media (hover: hover) and (pointer: fine)`. Provide equivalent focus-visible and active state feedback where the control needs it; touch and keyboard users must not lose the state cue.
- Verify changed motion at the current project desktop scope, including rapid re-triggering, focus/keyboard flow, reduced motion, and loading/error/empty states it touches. Use browser flow verification when rendered behavior or performance is consequential.
