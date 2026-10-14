---
description: Review the current branch's diff against CLAUDE.md rules before opening a PR
allowed-tools: Bash(git diff:*), Bash(git log:*), Read
---

Act as a strict senior reviewer on this branch before it becomes a PR.

1. Run `git diff origin/main...HEAD` and read the full diff.
2. Check each non-negotiable from CLAUDE.md:
   - Silent lint suppressions (`@Suppress`, `// noqa`, etc.) — each one justified inline?
   - Implicit `var` where `val` would work?
   - `@Value` annotations without fallback defaults?
   - Docker/compose changes with dynamic ports?
   - Secrets, credentials, or `.env` contents in the diff?
   - Branch name and commit messages follow the HDN / Conventional Commits patterns?
3. General quality checks:
   - Tests added/updated for the changed behavior?
   - Error handling — not swallowed, not a generic `catch(Exception)`?
   - Public API breaking changes flagged?
   - DB migrations reversible and safe on a live DB?
   - Keycloak roles/groups/mappings: existing-realm provisioning and both exports updated
     together? Dry-run and repeat-apply behavior tested? Live rollout status explicit,
     with ST evidence rather than a claim based only on exports or a merged PR?
4. Produce a report with three sections:
   - **Must fix** — blocks PR
   - **Should fix** — before merge
   - **Nits** — optional polish

   Be specific: file path + line number for every item.

Do not fix anything. Report only.
