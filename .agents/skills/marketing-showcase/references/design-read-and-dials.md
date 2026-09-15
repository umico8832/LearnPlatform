# Design Read and Dials

## Read the room

Identify these signals before designing:

- page kind: product homepage, campaign landing page, portfolio, resume showcase, case study, demo narrative, or redesign;
- audience: learner, recruiter, technical reviewer, educator, administrator, hiring manager, or general visitor;
- primary job: explain, establish trust, demonstrate a workflow, prove implementation depth, or drive one action;
- reference signals: user-provided words, screenshots, sites, brand assets, and competitive context;
- content character: editorial learning material, product UI, metrics, diagrams, personal work, or photography;
- quiet constraints: accessibility, credibility, regulatory or privacy concerns, actual asset availability, and current product scope.

If these signals point to materially different designs, ask one focused question. Otherwise state the inferred direction and proceed.

## Three descriptive dials

Use integer values as a shared design shorthand, not as numerical truth:

- `DESIGN_VARIANCE`: 1 is regular and symmetric; 10 is highly asymmetric or experimental.
- `MOTION_INTENSITY`: 1 is effectively static; 10 is cinematic or highly interactive.
- `VISUAL_DENSITY`: 1 is gallery-like and sparse; 10 is information-dense.

Infer values from audience and purpose. Accessibility-critical and trust-first pages usually warrant lower variance and motion. Creative portfolios may support more variance. A technical showcase may need greater density than a consumer landing page. A redesign in preservation mode starts near the current values; an overhaul may move further only with authorization.

The numbers do not trigger effects automatically. A motion value is not a promise to animate every section, and a high variance value does not excuse weak hierarchy.

## Anti-default comparison

Before implementation, ask whether the same plan would emerge for an unrelated AI product. Reconsider any choice that exists only because it is familiar:

- centered hero over a decorative mesh;
- three equal feature cards followed by another equal card grid;
- identical rounded surfaces and shadows at every hierarchy;
- purple or neon accent without a brand reason;
- uppercase tracked eyebrow above every heading;
- arbitrary numbers, terminal fragments, arrows, or monospace labels used as decoration;
- serif-on-cream, dark-with-acid-accent, or newspaper layout chosen only to appear designed;
- scroll reveal or hover animation repeated mechanically.

These forms are not banned. Keep one when the subject and content make it the strongest choice; otherwise replace it with a more specific structure.

## Brief to system

Use the existing LearnPlatform system unless the task explicitly changes the brand or component foundation. A named aesthetic is not automatically an official design system. Do not import a third-party system merely because a visual reference resembles it.

Translate the design read into:

- 4–6 named palette roles grounded in current tokens or an authorized token revision;
- one or two deliberate type roles with scale, line length, weights, and spacing;
- an alignment and content-width concept;
- a surface and material rule explaining borders, radii, elevation, and imagery;
- one sentence describing the memorable device;
- a motion intent tied to hierarchy, feedback, continuity, or storytelling.
