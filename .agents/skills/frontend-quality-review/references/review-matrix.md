# Review Matrix

Apply categories selectively. A category title is not evidence that a problem exists.

## Semantics and accessibility

- landmarks, heading order, language, document title, and meaningful page structure;
- native elements before custom roles; accessible names for icon-only controls;
- label and control association, grouped inputs, instructions, validation, and error recovery;
- keyboard reachability, logical focus order, visible focus, focus restoration, and escape behavior;
- dialogs, menus, disclosures, tabs, tables, live regions, and disabled or read-only semantics;
- text, control, focus, and status contrast; information not conveyed by color alone;
- alternative text that matches the image's purpose; decorative images excluded from accessibility output.

Do not demand redundant JavaScript keyboard handlers on native buttons or links. Review the semantic element and actual behavior first.

## Navigation and interaction

- controls look and behave consistently with their role;
- links navigate and buttons act; destructive and irreversible actions are distinguishable;
- loading prevents duplicate work without trapping the user;
- success, empty, failure, offline, permission-denied, and partial-data states give a next step;
- hit targets, pointer affordances, hover-only information, selection, and active states remain usable;
- route changes, dialogs, menus, and validation move or preserve focus intentionally.

## Forms

- visible persistent labels rather than placeholder-only labels;
- suitable input types, autocomplete, inputmode, formatting, and paste behavior;
- validation timing does not punish incomplete input; field and summary errors agree;
- submitted state, retry, duplicate submission, server errors, and retained values are handled;
- password, verification, and authentication flows preserve security and browser assistance.

## Layout and responsive behavior

- no accidental horizontal overflow, clipping, overlap, or obscured fixed content;
- grids, flex items, tables, long tokens, localization, and user content can shrink or wrap intentionally;
- viewport units, safe areas, sticky or fixed layers, scroll containers, and z-index form a stable system;
- responsive behavior matches current project scope while existing out-of-scope behavior is not broken;
- content order remains meaningful when columns collapse or visual order changes.

## Typography and content

- hierarchy is discernible by more than size alone and maps to semantic headings;
- readable measure, line height, font loading, fallback, weight, and numeric alignment;
- action names, confirmations, labels, and errors use consistent user vocabulary;
- no fabricated data, stale product claims, unexplained abbreviations, placeholder content, or production-facing design notes;
- truncation preserves access to essential content.

## Motion

- each animation supports feedback, continuity, hierarchy, orientation, or explanation;
- reduced-motion behavior is present for nonessential motion;
- animation can be interrupted or reversed without stale state;
- transforms, opacity, origin, easing, and duration fit the distance and interaction frequency;
- hover motion is gated to hover-capable pointers; scroll effects do not hijack navigation;
- layout, focus, pointer targets, and reading order remain correct during transitions.

## Visual system

- color, spacing, type, radius, border, elevation, and motion tokens express semantic roles;
- shared patterns are consistent where content relationships match and intentionally differ where they do not;
- current tokens and components are checked for root cause rather than assumed correct;
- decorative patterns do not overwhelm task hierarchy or make unrelated sections look identical.

## Performance and robustness

- images reserve dimensions and use suitable formats, sizes, loading priority, and lazy loading;
- unnecessary dependencies, icons, charts, or animation libraries are not added for small effects;
- repeated measurement, synchronous layout reads and writes, high-frequency reactive updates, and unbounded listeners are avoided;
- long lists and expensive visualizations use an appropriate rendering strategy;
- hydration assumptions, event cleanup, timers, observers, and async state cannot update a destroyed component;
- loading UI avoids disruptive layout shifts.

## Vue and Element Plus hygiene

- templates use stable keys and valid semantic nesting;
- props, emits, v-model contracts, slots, refs, computed state, watchers, and lifecycle cleanup are correctly scoped;
- derived state is computed rather than synchronized through avoidable watchers;
- shared component behavior is not duplicated by page-local CSS or ad hoc DOM manipulation;
- Element Plus accessibility and state behavior is preserved when styling wrappers or replacing internals;
- `v-html`, external URLs, and rich content follow the project's security boundaries.

## Product truth

- UI labels, counts, permissions, answer visibility, grading state, and course or exam behavior match real backend contracts;
- demos and marketing surfaces distinguish implemented capability from roadmap intent;
- visual polish does not replace real data with fixtures or hide meaningful error and permission states.
