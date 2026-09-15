# Pattern Vocabulary

This is a vocabulary for exploring compositions, not a block library or a requirement to use fashionable effects. Choose a pattern only when its information model, assets, input behavior, and performance cost fit the page.

## First-view patterns

- **Typographic hero:** type and concise copy carry the identity; useful when language itself is distinctive.
- **Artifact-first hero:** a real course, question, review, diagram, or product surface leads; useful when proof matters more than atmosphere.
- **Outcome-led hero:** one supported outcome or before-and-after relationship leads; requires credible evidence.
- **Editorial split:** narrative and media form unequal columns; useful for a strong visual with explanatory text.
- **Layered media hero:** foreground artifact, supporting image, and depth cues form one composition; avoid if assets are weak.
- **Interactive proof:** a small real interaction demonstrates the value; it must remain usable and must not simulate nonexistent capability.
- **Kinetic type:** wording changes or responds as the main expressive device; use only when motion improves the message and has a reduced-motion equivalent.

## Page structures

- **Asymmetric grid:** unequal areas express priority rather than merely breaking symmetry.
- **Bento grouping:** mixed-size cells organize genuinely different content weights; do not manufacture empty or filler cells.
- **Editorial column:** controlled reading measure with marginal notes, figures, or pull evidence.
- **Annotated workflow:** a real screenshot or diagram with connected explanations.
- **Sticky narrative:** one visual persists while adjacent steps change; useful for a meaningful sequence and safe only when scroll remains native.
- **Horizontal progression:** items advance laterally; reserve for ordered visual narratives with keyboard, touch, and reduced-motion behavior.
- **Comparison field:** alternatives share aligned dimensions; better than decorative cards when users need a decision.
- **Timeline or process:** numbered structure is appropriate only when order is real.
- **Featured and supporting:** one proof point dominates while smaller items deepen it.
- **Gallery, reel, or case-study index:** artifacts lead and metadata recedes; filtering or sequencing should match the viewing task.
- **Full-width pause:** a quote, result, visual, or statement changes rhythm between denser sections; use verified content only.
- **Progressive disclosure:** dense evidence is grouped behind tabs, accordions, or detail routes when a full scan is not required.

## Navigation and calls to action

- **Conventional top navigation:** strongest default when route discovery matters.
- **Compact command strip:** suitable for a small focused portfolio or campaign, not for hiding a broad information architecture.
- **Section index:** useful on long case studies or documentation-like showcases.
- **Contextual action:** place the next action after the evidence that justifies it.
- **Persistent action:** use only when the action remains relevant throughout the page and does not obscure content.

Keep one label for one intent. A primary and secondary action must lead to materially different outcomes.

## Media treatments

- **Framed product capture:** real UI shown with minimal chrome and readable scale.
- **Detail crop:** magnifies a meaningful implementation or content detail rather than using an unreadable full-page screenshot.
- **Before and after:** uses equivalent states and honest framing.
- **Process contact sheet:** several artifacts reveal iteration or breadth without pretending to be a carousel.
- **Editorial figure:** image or diagram paired with a concise caption and source.
- **Material texture or generated atmosphere:** supports identity but is not evidence of product capability.

## Micro-interaction families

- **State morph:** one control or surface visibly changes state while retaining identity.
- **Shared-element continuity:** a selected artifact expands into its detail view.
- **Spotlight or cursor response:** points attention to an inspectable area; never make pointer position essential.
- **Magnetic or spring response:** rare expressive feedback for hover-capable pointers; avoid on frequent task controls.
- **Reveal sequence:** communicates order in a small number of meaningful items; do not reveal every section mechanically.
- **Marquee or ambient loop:** acceptable for nonessential breadth or atmosphere only when pausable, non-distracting, and removed for reduced motion.

Use [interaction-motion](../../interaction-motion/SKILL.md) before implementing any substantive pattern in this section.

## Selection test

For each candidate, answer:

1. What content relationship does it express?
2. Does the project have real assets and content for it?
3. Is it still usable with keyboard, touch, zoom, reduced motion, and the scoped viewport?
4. Does it introduce a new dependency or complex lifecycle?
5. Is a simpler pattern equally clear?

Reject a pattern when its main justification is that it looks advanced.
