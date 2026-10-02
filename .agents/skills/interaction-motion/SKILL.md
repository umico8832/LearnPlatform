---
name: interaction-motion
description: Build, review, audit, name, or find purposeful interaction motion in LearnPlatform's Vue 3 and Element Plus frontend. Use for transitions, restrained learning-state feedback, reduced-motion behavior, or motion-quality review; use frontend-design for page redesign.
---

# LearnPlatform Interaction Motion

Make state changes easier to understand without slowing a learning or administration task. Motion is optional: an instant change is the right outcome when animation adds no useful signal.

## Read first

Inspect the target component and its actual trigger path. Read `../../../frontend/src/assets/styles/tokens.css`, `../../../frontend/src/assets/styles/motion.css`, and `../../../docs/architecture/frontend.md`; also use [frontend-design](../frontend-design/SKILL.md) for page-level visual work. Reuse the current Vue 3, TypeScript, Element Plus, and CSS-variable stack. Simple motion stays in CSS/Vue transitions. A new animation dependency requires an evidenced need plus compatibility, lifecycle, accessibility, and bundle review.

The current `--lp-duration-*` and `--lp-ease-*` variables are the baseline, not an unchangeable law. Reuse them where they fit. Propose a token addition or adjustment only when repeated, evidenced component needs cannot be represented by that scale; keep tokens semantic and avoid a parallel local motion system.

## Current implementation boundary

Use Vue `<Transition>` for one entering or leaving root, and `<TransitionGroup>` only when keyed list insertion, removal, or movement needs continuity. The shared names are `lp-content` for short enter-only content, `lp-disclosure` for conditional panels, and `lp-list` / `lp-list-move` for keyed list updates. Their CSS names, opacity, translation distance, duration, and easing come from `motion.css` and tokens; do not duplicate a near-equivalent local transition.

Numeric changes use the cancellable `useAnimatedNumber` RAF helper. Its first received value renders directly; later finite updates may transition briefly. Component unmount cancels outstanding RAF work; a reduced-motion preference change cancels and shows the current target. Account-scoped feedback cleanup belongs to its store, not this composable. It displays supplied facts only and never derives scores, rewards, or mastery.

The global `prefers-reduced-motion` treatment makes CSS transitions effectively immediate (`0.01ms`). The shared `motion.css` transitions also remove travel; do not assume that rule resets unrelated component transforms. JavaScript-driven numeric motion must observe `useReducedMotion` and stop at its target. Do not retain a decorative opacity, scale, or layout animation merely because it is short.

Motion for Vue's `MotionConfig` and `LazyMotion` are evaluated references, not installed project dependencies. Do not add them without a concrete timeline, gesture, or physics need plus compatibility, lifecycle, reduced-motion, and bundle evidence. Native `Document.startViewTransition()` remains a later candidate; it is not part of the current routing or state transition implementation.

## Choose a mode

- **Build** — add a requested transition or interaction. Follow the build gate below, then implement the smallest viable motion.
- **Review** — inspect supplied motion code or a focused diff. Report actionable findings with `file:line`, ordered by user impact; do not alter unrelated code.
- **Audit** — survey a defined frontend area for existing motion quality and consistency. Establish the token and interaction baseline before judging it.
- **Opportunities** — look for a small number of missing, defensible motion moments. Include rejected candidates so decoration is not mistaken for a gap.
- **Vocabulary** — name a described effect concisely. Read [motion vocabulary](references/motion-vocabulary.md) only for this mode.

For build, review, audit, and opportunities work, read [motion decision guide](references/motion-decision-guide.md). It contains the operating checks and project implementation guidance. For attribution and the upstream scope, read [source note](references/source-and-license.md).

## Learning feedback

Experience, streaks, goals, and achievements come from persisted backend learning events. Motion never calculates or approves them. Display those facts quietly in the header, profile, and summary rather than with reward floats, full-screen celebrations, confetti, combo flames, achievement dialogs, or persistent pulses. During timed exams, suppress correctness, combo, reward, and sound cues until submission.

An answer or completion may use the shared short state transition only when it improves continuity and never delays the next action. Account-scoped feedback cleanup belongs to the feedback store. Reduced motion retains the information while making shared transitions immediate and removing their travel.

## Shared boundaries

- Preserve real data flow, focus management, keyboard behavior, and Element Plus semantics. Animation must not defer an action, conceal validation, trap focus, or make an exit unavailable.
- Default to CSS transitions, Vue `<Transition>`, `@starting-style`, or CSS keyframes when their lifecycle fits. Use WAAPI only for programmatic, cancelable sequences that CSS cannot express. A custom composable is justified only for real gesture or lifecycle coordination; clean up listeners, observers, timers, and animations on unmount.
- Name transitioned properties; never use `transition: all`. Prefer `transform` and `opacity`, but do not claim they are universally compositor-only. For a layout-dependent effect such as an accordion, first choose an instant state change or a tested layout-aware technique; only animate layout with a clear benefit and performance verification.
- Include an intentional `prefers-reduced-motion` variant whenever movement, auto-play, or scroll-linked motion is added. It may keep a short opacity or color transition when that preserves comprehension, but removes nonessential travel, scale, repetition, and long staggering.
- Put hover-only motion behind `@media (hover: hover) and (pointer: fine)`. Provide equivalent focus-visible and active state feedback where the control needs it; touch and keyboard users must not lose the state cue.
- Verify changed motion at the current project desktop scope, including rapid re-triggering, focus/keyboard flow, reduced motion, and loading/error/empty states it touches. Use browser flow verification when rendered behavior or performance is consequential.
