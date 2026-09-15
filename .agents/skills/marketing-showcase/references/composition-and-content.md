# Composition and Content

## Give every section a job

Outline the narrative before choosing components. Common jobs include orientation, product proof, workflow explanation, differentiation, credibility, a concrete example, and action. Several jobs may share one section; a section with no job should be removed.

Vary structure when the information varies. Useful composition families include:

- typographic, visual, split, live-demo, or outcome-led hero;
- asymmetric grid, editorial column, annotated workflow, timeline, comparison, gallery, or focused full-width moment;
- featured item with supporting items, grouped evidence, layered media, or progressive disclosure;
- sticky narrative or horizontal progression only when the sequence and input method justify it.

Do not repeat a composition merely to fill the page. Repetition is useful for comparable items; forced variety is also a defect. Choose structure from content relationships.

## Hero discipline

The initial view should establish what the product is, who it helps, and the next meaningful action without relying on vague slogans. Fit is a rendered-layout question, not a universal word count or padding number.

- Keep one dominant message.
- Use at most one primary and one genuinely distinct secondary action.
- Ensure desktop navigation and calls to action do not wrap unintentionally.
- Move extended proof, feature lists, and supporting detail below the first moment when they compete with the message.
- Let a real workflow, course artifact, question, study result, or screenshot carry the hero when it is more characteristic than decorative imagery.

## Layout and material

- Use alignment changes, scale, whitespace, and grouping to express relationships before adding containers.
- Do not turn every text block into a card. Borders, shadows, tint, and radius should convey hierarchy or interactivity.
- Use CSS Grid for explicit two-dimensional relationships and Flexbox for one-dimensional distribution; avoid brittle percentage arithmetic.
- Verify real text, long titles, and asset aspect ratios. Declare deliberate fallbacks for every multi-column section within the active viewport scope.
- Keep z-index and sticky behavior bounded to named layers. Avoid scroll hijacking and inaccessible pointer-only navigation.

## Images and product evidence

Select media by communicative value:

1. real product UI or an actual interactive excerpt;
2. project-owned screenshots, diagrams, or brand assets;
3. specifically created, licensed, or user-provided imagery;
4. a transparent development placeholder when the asset is not yet available.

Do not use invented dashboards, fabricated company marks, or fake customer evidence. Do not require imagery when typography or real UI explains the product more honestly. When generated imagery is authorized, define its purpose, aspect ratio, and placement before generation and label it internally as an asset rather than product evidence.

## Content is visual material

- Use the vocabulary of learners and reviewers, not internal architecture names.
- Keep actions consistent through the whole flow: an action and its confirmation use the same verb.
- Prefer specific factual statements over inflated claims.
- Treat empty and failure states as directions for recovery.
- Do not invent numeric precision for visual rhythm.
- Keep one voice per page unless the content itself requires a deliberate contrast.
- Proofread every visible string, alt text, label, and metadata field as part of visual QA.

## Interaction states

Public pages still require keyboard access, visible focus, readable contrast, hover-capability guards, touch-safe targets, reduced motion, and usable forms. When a call to action enters the application, verify the real route and state rather than presenting a dead showcase control.
