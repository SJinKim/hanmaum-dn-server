# hanmaum-dn-server

Spring Boot 3 + Kotlin backend for the Hanmaum DN platform.
Serves the Angular web dashboard and the Kotlin Multiplatform mobile app.

## Stack
- Kotlin 2.x, Spring Boot 3.x, Gradle KTS
- PostgreSQL + Flyway migrations
- Keycloak for auth (JWT `sub` claim stored as `keycloak_id` in members)
- Docker Compose for local dev

## Current phase
Pre-MVP. See `.claude/skills/mvp-focus/` for scope rules.

## Non-negotiable rules
1. Plan before coding on any change touching 3+ files. Use `/plan` or Shift+Tab×2.
2. No silent lint suppression. If a rule must be disabled, justify inline.
3. No implicit `var` in Kotlin. Prefer `val`; `var` only when mutation is genuine.
4. Every `@Value` has a fallback default: `@Value("\${app.foo:defaultValue}")`.
5. Explicit port mapping in Docker. Never rely on dynamic ports.
6. Conventional Commits via `/commit`. Branch pattern: `<type>/HDN-<id>-<slug>`.
7. Never push directly to `main`. Feature branches only.
8. No secrets in code, commits, or Claude conversations. Use `.env` + Spring profiles.
9. Tests: unit tests for services, integration tests for controllers. No merge without a
   meaningful assertion — a passing test that asserts nothing is worse than no test.
10. Logging: SLF4J, structured kv pairs (`log.info("Provisioned member kcId={}", sub)`).
    No PII, no tokens, no full JWT contents — ever.
11. Errors: never swallow. Domain errors → `ProblemDetail` response. Infra errors →
    wrap with context and re-throw. No generic `catch(Exception)`.

## Layout
- `src/main/kotlin/.../controller` — REST endpoints (thin)
- `src/main/kotlin/.../service` — business logic
- `src/main/kotlin/.../repository` — Spring Data JPA
- `src/main/kotlin/.../domain` — entities, value objects
- `src/main/kotlin/.../config` — Spring config, security
- `src/main/resources/db/migration` — Flyway SQL

## Cross-repo references
- API contract: `hanmaum-dn-ops/api/openapi.yaml` (source of truth)
- Definition of Done: `hanmaum-dn-ops/DEFINITION_OF_DONE.md`
- Sprint notes: `hanmaum-dn-ops/sprints/`

## Custom commands (`.claude/commands/`)
- `/plan` — plan mode with backend context loaded
- `/commit` — staged changes → Conventional Commit
- `/pr` — open PR from current branch using our template
- `/review` — self-review diff before opening PR
- `/sprint-status` — current milestone snapshot

## Skills (`.claude/skills/`, loaded on demand)
- `auth-keycloak` — JWT, `keycloak_id` linking, first-login member provisioning
- `db-migrations` — Flyway conventions, safe migration patterns
- `api-contracts` — reading/updating the shared OpenAPI spec
- `mvp-focus` — MVP scope rules (retired when v1.0.0 ships)

## Workflow Orchestration

### Plan Mode
- Rule 1 already requires planning for 3+ file changes — also enter plan mode for architectural decisions and verification steps.
- If something goes sideways: STOP immediately, re-plan. Don't push through a broken approach.

### Subagent Strategy
Delegate only when it *shrinks* the main context. The test: would the work generate a lot
of tool output I don't need to keep?

- **Delegate**: executing one task of a written plan (the plan is the subagent's context,
  so it starts warm); broad searches where only the conclusion matters; anything whose
  verification is a command exit code rather than a judgment call.
- **Do inline**: design decisions, anything touching shared infrastructure (the local
  Postgres volume is shared across worktrees), and work where I'd have to read the whole
  diff to trust it — then I've paid for the context twice.
- One task per subagent, and run them **sequentially**. See the token rules below.

### Self-Improvement Loop
- After ANY user correction: append the pattern to `tasks/lessons.md`.
- Write a rule that prevents the same mistake from recurring.
- Review `tasks/lessons.md` at session start.

### Verification Gate
- Never mark a task complete without proving it works.
- Ask: "Would a staff engineer approve this PR?"
- Run tests, check logs, demonstrate correctness before marking Done.

### Bug Fixing
- When given a bug report: just fix it — no hand-holding requests.
- Point at logs, errors, failing tests and resolve autonomously.

### Core Principles
- Simplicity First: minimal code impact per change.
- No Laziness: find root causes, no temporary fixes.
- Minimal Impact: only touch what's necessary.

## Token-efficient workflow (Pro plan)
- `/clear` between unrelated tasks.
- `/compact` at ~50% context.
- Reference files as `@path/to/File.kt`, not "look at the codebase".
- No Agent Teams / parallel sub-agent fan-out on Pro — several agents starting cold on the
  same problem re-derive the same context and multiply tokens 3–7×.
- Sequential subagents are the exception and are often *cheaper* than working inline: the
  subagent's file reads and tool output stay in its context and are discarded, while inline
  reads compound in the main context and are re-sent every turn. Use one per task, only for
  tasks specified well enough that verification is running a command, not reviewing a design.
