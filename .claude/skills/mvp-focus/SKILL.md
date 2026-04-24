---
name: mvp-focus
description: Use when planning, scoping, or implementing any new feature or change while the project is pre-MVP
---

# MVP scope rules (active until first production release)

The project has not yet shipped MVP. Every unit of work must pass a scope check.

## Before implementing
Ask: is this required for MVP?

- **Required**: minimum path for a user to sign in, view their profile,
  and interact with the core member feature set (see
  `hanmaum-dn-ops/MVP_SCOPE.md` for the definitive list).
- **Not required**: polish, edge cases, admin tooling, non-core features,
  performance optimization beyond baseline, analytics, advanced auth flows.

## If required
- Build the minimal version that satisfies acceptance criteria.
- Cut anything that would be nice-to-have.
- Ship it.

## If not required
- Do not implement.
- Create a follow-up issue labeled `post-mvp` linking the original ticket.
- Leave in backlog for later.

## Retiring this skill
When MVP ships (first `v1.0.0` tag):
1. Delete this skill folder.
2. Remove the "Current phase" line from CLAUDE.md.
3. Commit: `chore: retire mvp-focus skill, MVP shipped`.
