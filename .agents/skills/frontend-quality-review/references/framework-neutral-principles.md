# Framework-Neutral Principles

Use these only when their premise applies to the Vue implementation. They are adaptations of useful ideas commonly expressed in React and composition, shadcn, Tailwind, and performance guides, not instructions to introduce those stacks.

## Component composition

- Prefer a small stable public API over many booleans that create invalid combinations.
- Model meaningful variants explicitly and keep shared structure shared.
- Use slots or child composition when the caller owns content; use props when the component owns semantics.
- Keep state close to the behavior that needs it. Lift it only for coordination or persistence.
- Separate data acquisition and business rules from presentational structure when doing so clarifies ownership or testing.
- Do not abstract a one-off composition until repetition or volatility proves a shared contract.

## Rendering and reactivity

- Avoid serial data dependencies that could begin together, while preserving required ordering and cancellation.
- Compute derived values rather than storing duplicate state.
- Keep high-frequency pointer, scroll, or animation values out of broad component re-render paths.
- Lazy-load expensive routes or assets only when the boundary improves user-perceived work and does not create a worse waterfall.
- Optimize measured bottlenecks or obvious algorithmic waste; do not cargo-cult memoization.

## Styling systems

- Utility classes, component-library props, scoped CSS, and global tokens are implementation mechanisms; judge them by ownership, reuse, output, and maintainability.
- Prefer semantic design tokens over raw repeated values. Add a token when it represents a stable role, not every isolated number.
- Keep responsive rules next to the component behavior they govern or in an established shared primitive.
- Do not copy a library's default appearance unchanged when it conflicts with the product direction; preserve its semantics and behavior while adapting presentation.

## Accessible primitives

- A mature primitive or component library can reduce accessibility risk, but styled wrappers can still break names, focus, state, and keyboard behavior.
- Prefer native controls for native behavior. Custom primitives need equivalent semantics, focus, and input support.
- One icon family and consistent sizing generally improve coherence, but an existing intentional exception is not automatically a defect.

## Performance claims

Treat universal slogans cautiously. CSS animation is not automatically cheap, a short duration is not automatically usable, and fewer DOM nodes are not automatically faster. Confirm properties being animated, paint and layout cost, event frequency, device constraints, content volume, and observed user impact.
