---
name: interaction-motion
description: Build, review, audit, name, or find interaction motion in LearnPlatform's Vue 3 frontend. Use for spring-based transitions, visualization motion, learning-state feedback, reduced-motion behavior, or motion-quality review; use frontend-design for page redesign.
---

# LearnPlatform Interaction Motion

Motion is part of how the product explains things. Under [ADR-0011](../../../docs/architecture/decisions/0011-living-textbook-spring-motion.md) any surface may move; what changes is the degree. Reading stays calm, understanding moves.

## Read first

Inspect the target component and its trigger path, then [frontend-design](../frontend-design/SKILL.md) for which baseline the page uses. Pages not yet redesigned keep the existing token and `motion.css` system described in [frontend architecture](../../../docs/architecture/frontend.md); do not add spring motion to them piecemeal. Redesigned pages use the spring system below.

## Intensity tiers

Pick the tier from the surface, then keep the motion inside it:

- **Light** — question stems, explanations, code, Markdown, and timed exams. Content may fade or slide in once; nothing moves while it is being read. During an exam, motion never hints at correctness.
- **Medium** — controls, navigation, panels, sheets, lists, and answer feedback. Selection thumbs travel and stretch, sheets open from their trigger, lists reorder with continuity.
- **Strong** — visualization courseware and key moments of understanding. Pointers bend to new targets, values fly from their source, the camera reframes, spring overshoot is welcome.

Frequency lowers intensity: an action repeated many times per minute gets the light end of its tier.

## Spring system

- Every animated value is a spring described by response (seconds) and damping fraction, using the closed-form solution so behavior is frame-rate independent.
- Use the named presets — layout, pointer, bend, snappy, fade, camera — before tuning a new one. A new preset needs a repeated need, not a single component's taste.
- Springs are interruptible: retarget from the current value and keep velocity; never restart an animation from its first frame.
- CSS transitions and WAAPI sequences use the `linear()` easing generated from the same spring so that DOM and canvas motion feel identical: the `--lt-ease-*` and `--lt-dur-*` tokens in CSS, `springAnimate` in script. No hand-tuned cubic-bezier on redesigned pages.
- Things appear from where they come from: sheets and popovers scale out of their trigger, copies fly out of their source node, removed items shrink back toward where they belong.
- A dependency is allowed for what the core does not cover well (gesture momentum, cross-page shared elements, native View Transitions); align its parameters with the presets.
- A shared-element page transition names the element on both pages and captures the destination only once it has rendered its real content; otherwise the morph lands on an empty placeholder.

## Visualization motion

The stage animates declarative frames keyed by id; a simulator decides every state and the stage only interpolates between them. Never animate a state the simulator did not produce, and never let motion imply a result the learner has not reached. Hypothetical states (a learner's prediction) are drawn as dashed purple ghosts, distinct from real state.

## Shared boundaries

- Motion for scores, rewards, and mastery only expresses results the backend has returned; it never calculates or approves them. Visualization steps and prediction feedback come from the deterministic scene simulator and are exempt.
- No reward floats, full-screen celebrations, confetti, combo flames, achievement dialogs, or loops without a status meaning. A loop that signals a pending state (for example a question waiting for an answer) is allowed and stops when the state ends.
- Animation must not delay an action, conceal validation, trap focus, or make an exit unavailable; focus, announcements, and navigation never wait for an animation to finish.
- Name transitioned properties; never use `transition: all`. Prefer `transform` and `opacity`; measure before animating layout.
- `prefers-reduced-motion`: springs jump to their target, shared CSS transitions become immediate, travel and repetition are removed, results stay visible.
- Hover-only motion sits behind `@media (hover: hover) and (pointer: fine)` and has an equivalent focus-visible and active state.

## Choose a mode

- **Build** — add motion. Follow the build gate in the [motion decision guide](references/motion-decision-guide.md), then implement within the chosen tier.
- **Review** — inspect motion code or a focused diff; report actionable findings with `file:line`, ordered by user impact.
- **Audit** — survey an area for motion quality and consistency against its tier and presets.
- **Opportunities** — find a small number of missing, defensible motion moments; list rejected candidates too.
- **Vocabulary** — name a described effect; read [motion vocabulary](references/motion-vocabulary.md) only for this mode.

For attribution and the upstream scope, read [source note](references/source-and-license.md).

## Verification

Check the rendered result, not the source: rapid re-triggering and reversal, keyboard flow, reduced motion, dark appearance, and the loading, empty, error, and populated states the motion touches. When the browser pane is hidden, animation frames may be throttled; verify motion in a visible or headless browser instead.
