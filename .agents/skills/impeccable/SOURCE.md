# Upstream provenance

- Upstream: [pbakaus/impeccable](https://github.com/pbakaus/impeccable)
- Pinned commit: `cb56ed6c19a07329a9fa0cd4e657bee040156593` (2026-09-10 review)
- Canonical source: `skill/SKILL.src.md`
- Local artifact: generated from the upstream `agents` provider distribution (`.agents/skills/impeccable`) with `node scripts/build.js --skip-root-sync`
- License: Apache-2.0; see `LICENSE` and `NOTICE.md`.

This vendored skill intentionally excludes project-level hooks and does not install or manage root `PRODUCT.md` or `DESIGN.md`. Its optional command workflows may create or update those project-context artifacts only when explicitly invoked by a user.

The vendored body, references, agents, and scripts match the generated upstream distribution. LearnPlatform changes only the `SKILL.md` discovery description so ordinary frontend requests route through the project's stack-aware Skills; explicit Impeccable invocation still exposes every upstream command.
