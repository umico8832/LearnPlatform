# Motion decision guide

Use this guide for build, review, audit, and opportunity work. Treat its ranges and heuristics as starting points to test against the product context, not universal rules.

## Build gate

Decide in this order and state the result briefly when building:

1. **Necessity and frequency.** How often will a user encounter it, and is it user-initiated, keyboard-initiated, automatic, or scroll-linked? Frequently repeated learning actions, navigation, typing, shortcuts, timers, and data-reading surfaces usually need instant or nearly imperceptible feedback. Occasional dialogs, menus, and status messages may benefit from a short transition. Rare onboarding or completion moments have a larger delight budget, provided they remain skippable and do not delay work. Keyboard initiation alone is evidence to reduce or remove motion, not a substitute for inspecting the task and user expectation.
2. **Purpose.** Name one: feedback, state indication, spatial continuity, prevention of a jarring change, explanation, or rare-moment delight. If no purpose survives, do not animate. Do not decorate charts, answer content, countdowns, or dense administrative data merely to make them move.
3. **Tool and lifecycle.** Use a CSS transition for a controlled class/attribute state, Vue `<Transition>` for mount/unmount, `@starting-style` for a CSS entry where support fits the target browsers, and a keyframe for finite predetermined or status motion. Prefer a transition or cancelable WAAPI sequence for a toggle, toast, or other state that can change before its animation ends. Gesture momentum and complex measured layout changes require explicit evidence before introducing custom code.
4. **Properties and origin.** Use a small opacity plus translate/scale change where spatial motion helps. An anchored menu, popover, or tooltip should originate near its trigger; a centered modal need not pretend to have one. Do not use an extreme `scale(0)` entrance. Use percentage translation when the distance should follow the element's own size. Avoid permanent `will-change`; apply it only to a measured, short-lived need.
5. **Timing and easing.** Entering or responsive exits generally feel best with the existing `--lp-ease-out`; on-screen repositioning generally calls for `--lp-ease-in-out`; color and subtle hover feedback may use the project baseline that best matches the component. `linear` suits progress or other constant-rate status only. Select duration from the existing fast/normal/slow scale before inventing values: small feedback is commonly fastest, compact surfaces normal, and larger surfaces may require slow. Longer durations can be valid for an explanatory or deliberately paced interaction, but must not block input or contradict perceived responsiveness. Do not use a slow-start curve for a response unless its purpose requires it.
6. **Interruption and exit.** Rapid reversals must retarget smoothly from the current state; do not restart a keyframe from its first frame. An exit should maintain spatial logic with its entrance unless a documented interaction (such as a user drag) supplies another direction. A deliberate hold and the resulting system response may deserve different timing.

## Vue and CSS implementation shape

Use Vue's built-in transition classes before adding JavaScript hooks:

```vue
<Transition name="lp-fade-rise">
  <section v-if="open" class="panel">…</section>
</Transition>
```

```css
.lp-fade-rise-enter-active,
.lp-fade-rise-leave-active {
  transition:
    opacity var(--lp-duration-normal) var(--lp-ease-out),
    transform var(--lp-duration-normal) var(--lp-ease-out);
}

.lp-fade-rise-enter-from,
.lp-fade-rise-leave-to {
  opacity: 0;
  transform: translateY(0.5rem);
}

@media (prefers-reduced-motion: reduce) {
  .lp-fade-rise-enter-active,
  .lp-fade-rise-leave-active {
    transition: opacity var(--lp-duration-fast) linear;
  }

  .lp-fade-rise-enter-from,
  .lp-fade-rise-leave-to { transform: none; }
}
```

This is a pattern, not a copy-paste requirement: match names, state ownership, and Element Plus extension points already used by the target. Do not animate an Element Plus overlay in a way that conflicts with its visibility, focus, or close lifecycle.

## Review and audit checklist

For each motion-bearing interaction, establish trigger, frequency, purpose, states, properties, token usage, interruption behavior, origin, input modality, and reduced-motion behavior. Then look for:

- movement with no orientation or feedback value; long or staggered entry that delays scanning or interaction;
- unbounded `transition: all`, layout/paint churn, perpetual loops without a status meaning, or measurement/DOM writes that cause jank;
- hard-coded curves or durations that diverge from tokens without a component-level rationale;
- a response that starts perceptibly late, a trigger-anchored surface that grows from the wrong place, or an abrupt/restarting rapid toggle;
- hover that activates on touch, missing focus-visible feedback, or reduced-motion behavior that still travels or loops;
- focus, screen-reader announcement, validation, timer, or navigation changes that are coupled to animation completion.

Classify findings as blocking only when they materially impair task completion, accessibility, correctness, or measured performance. Otherwise distinguish a concrete inconsistency from a subjective feel-check. Recommend removal before adding complexity.

## Opportunity discovery

Search a bounded area for missing feedback on important pressable controls, instantaneous appearance/disappearance that disorients users, anchored surfaces with no spatial relationship, and rare completion or empty states that need a small state cue. For every candidate, apply the build gate. Return a compact table with location, current behavior, purpose, frequency, and proposed implementation; also list candidates rejected for high frequency, lack of purpose, information-reading interference, or accessibility risk.

## Verification

Check in DevTools or the rendered app rather than inferring feel from source alone. Exercise rapid open/close or repeat activation, keyboard focus and Escape/Enter where applicable, `prefers-reduced-motion`, coarse-pointer behavior, and the affected loading, empty, error, and populated states. Record a visual/performance observation as an observation, not a guarantee across all devices.
