---
description: Produce an implementation plan, then execute in batches of 3 with checkpoints
argument-hint: <task description>
---

**Phase 1: Plan.** Read-only. No file edits. No destructive commands.

Task: $ARGUMENTS

Produce a plan with these sections:
1. **Scope** — which files/modules change, which stay untouched
2. **Approach** — ordered task list, each task atomic and verifiable
3. **Risks** — migrations, breaking changes, auth/security touchpoints
4. **Verification** — how we'll know each task works
5. **Out of scope** — what we are explicitly *not* doing

Follow the non-negotiables in CLAUDE.md. If the task touches auth or DB
migrations, invoke the relevant skill (`auth-keycloak`, `db-migrations`).

Wait for plan approval before continuing.

**Phase 2: Execute in batches of 3.** After approval:

- Pick the next 3 tasks. Mark them in progress.
- Implement each. Run its verification step.
- After 3 tasks, stop. Report:
  - ✅ Done: [tasks completed]
  - ⚠️ Findings: [anything unexpected]
  - 🔧 Changed: [files + one-line rationale]
  - 📋 Next batch: [next 3 tasks]
- Wait for "continue" before the next batch.

Never skip a failing verification silently. Surface it, propose a fix, wait.
