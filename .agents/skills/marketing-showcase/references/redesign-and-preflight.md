# Redesign and Preflight

## Determine the redesign mode

Before editing an existing page, identify whether the request is:

- targeted evolution: preserve the established identity and information architecture while fixing specific weaknesses;
- visual overhaul: retain business truth and interaction contracts but reconsider the visual system;
- structural overhaul: change hierarchy or page narrative as part of an explicitly authorized product or design change.

Do not infer an overhaul from words such as “polish” or “modernize.” Conversely, do not use existing rules to block a requested overhaul.

## Audit before touching

Capture the current page and record:

- what already communicates identity or trust;
- weak hierarchy, awkward density, repetitive structures, and unclear actions;
- accessibility or responsive defects;
- reusable assets, tokens, components, and interaction contracts;
- claims, routes, and analytics hooks that must remain true;
- whether the problem is local composition or a shared-system defect.

Preserve what works because it works, not merely because it exists. Change shared rules only within the authorized scope and verify downstream effects.

## Modernization levers

Prefer the smallest set that produces a coherent result:

1. content hierarchy and sequence;
2. typography and measure;
3. layout rhythm and alignment;
4. imagery or product evidence;
5. palette and surface relationships;
6. interaction states and purposeful motion;
7. decorative detail.

If the page remains generic after adjusting decorative details, the issue is probably higher in the list.

## Rendered preflight

Before completion, check the actual page at the scoped viewport or viewports:

- the first view identifies the offering and a meaningful action;
- content and claims match authoritative project sources;
- visual hierarchy remains clear without reading every word;
- repeated cards, split sections, labels, radii, shadows, and animation patterns are intentional;
- navigation, headings, buttons, labels, and long text do not clip or wrap badly;
- imagery has correct rights and provenance, meaningful alt text, and stable dimensions;
- controls have loading, disabled, error, hover, active, and focus behavior where relevant;
- contrast, keyboard order, landmarks, headings, and reduced motion remain usable;
- sticky, fixed, and layered elements do not obscure content;
- the active desktop viewport has no unexpected overflow; existing out-of-scope responsive behavior was not broken;
- production copy contains no placeholder, fabricated proof, design commentary, or stale capability claim;
- page weight and animation work are proportionate to their value.

If browser rendering is unavailable, report that limitation rather than treating source review as visual verification.
