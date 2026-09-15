# Sources and Adaptation Boundary

## Vercel web-design-guidelines

- Source: `https://github.com/vercel-labs/agent-skills/tree/main/skills/web-design-guidelines`
- Reviewed commit: `063bee94c3f4df8453406c830b0a7df0f2860278`
- Retained: broad web-interface audit coverage and concise, location-based findings.
- Changed: the project keeps a reviewed local matrix instead of fetching mutable remote rules on every run. React terminology, Vercel brand conventions, and rules that confuse native controls with custom controls are contextualized.

## Impeccable

- Source: `https://github.com/pbakaus/impeccable`
- Reviewed commit: `cb56ed6c19a07329a9fa0cd4e657bee040156593`.
- Purpose here: retain deterministic detectors, review commands, optional live-browser modes, and extensible critique workflows. Project docs remain authoritative for product and architecture.

## UI UX Pro Max and stack-oriented sources

The repository's existing UI UX Pro Max package supplies searchable design data. React best-practice, composition, shadcn/ui, and Tailwind guidance is used only for framework-neutral principles; package APIs and stack-specific architecture are not transplanted into Vue.
