# Contributing to hanmaum-dn-server

## First-time setup

```bash
git clone git@github.com:HANMAUM_ORG/hanmaum-dn-server.git
cd hanmaum-dn-server
npm install          # installs git hooks via lefthook
./gradlew build      # verify the build
docker compose up -d # starts Postgres + Keycloak
./gradlew bootRun    # starts the server on :8080
```

## Branching

- Feature: `feat/HDN-<id>-<slug>`
- Bugfix: `fix/HDN-<id>-<slug>`
- Chore: `chore/HDN-<id>-<slug>`

Never push directly to `main`.

## Commits

Conventional Commits, enforced by commitlint. Scope is required.

```
feat(auth): add first-login member provisioning

Creates a `members` row on first JWT arrival when no existing
record matches the `sub` claim. Falls back to email matching.

Refs: #42
```

Use `/commit` from Claude Code to generate messages automatically.

## Pull requests

1. Open a PR with `/pr` or the template.
2. One approving review required.
3. All CI checks must pass.
4. Squash-merge into `main` — linear history only.

## Scrum / sprint workflow

- **Sprints** = GitHub Milestones in the ops repo.
- **Board** = a single GitHub Project v2 spanning all four repos.
- **Columns**: Backlog → Sprint Backlog → In Progress → In Review → Done.
- Pick up an issue by moving it to *In Progress* and assigning yourself.
- `/sprint-status` shows the current snapshot.

## MVP-first

Every piece of work is measured against MVP scope. Before implementing:

1. Ask: is this required to ship MVP?
2. If yes, do the minimal version that satisfies the acceptance criteria.
3. If no, create a `post-mvp` issue and leave it in backlog.
4. `/plan` invokes the `mvp-focus` skill which enforces this.

## Working with Claude Code

- Run `claude` in the repo root. `CLAUDE.md` auto-loads.
- Use `/plan` before any multi-file change.
- Use `/commit` after staging.
- Use `/pr` to open the pull request.
- Use `/review` for a self-review before PR.
- Use `/clear` between unrelated tasks to save context.

Custom slash commands live in `.claude/commands/`. Skills live in
`.claude/skills/` and load only when their description matches the task.

## Installing lefthook globally (alternative)

If you prefer not to run `npm install`, install lefthook globally once and
run `lefthook install` in the repo:

```bash
brew install lefthook       # macOS
scoop install lefthook      # Windows
# or see https://lefthook.dev for Linux
lefthook install
```

You still need Node for commitlint — either way, `node` must be on your PATH.
