# Contributing — hanmaum D+N (backend)

This repo is part of a three-repo project. Read `CLAUDE.md` before touching code, and respect the conventions below. They apply equally to human edits and Claude-assisted edits.

## Team size

Up to 3 developers. Every change goes through PR review — no direct pushes to `dev` or `main`.

## Branching

- `main` — production. Protected. Only release PRs land here.
- `dev` — integration branch. All feature work merges here first.
- `feature/<short-name>` — always branched from `dev`, PR back to `dev`.
- `hotfix/<short-name>` — only branch allowed to fork from `main`.

Before starting work:

```bash
git checkout dev && git pull origin dev
git checkout -b feature/<short-name>
```

Never start work on `main`. Never commit directly to `dev`.

## Pull requests

- One feature per PR. If it's too big to review, split it.
- Title uses the commit convention: `type(scope): summary`.
- Description: *why* the change is needed, what was done, how it was tested.
- Link the MVP.md row or issue.
- Run `/pr-review` before opening the PR — paste the PASS/FAIL output in the description.
- Require at least one approval before merge. The author does not merge their own PR.
- Rebase on `dev` before merging — no merge commits.

## Commit convention

`<type>(<scope>): <summary max 72 chars>`

Types: `feat | fix | refactor | test | chore | docs | perf | revert`

Imperative mood, no trailing period. Body explains *why*, not *what*.

## Claude Code conventions

This project uses Claude Code. To keep all three developers' Claude behavior consistent:

### Public (committed) vs. private (gitignored)

| File | Committed? |
|---|---|
| `.claude/settings.json` | ✅ team defaults |
| `.claude/settings.local.json` | 🚫 per-machine |
| `.claude/commands/*.md` | ✅ shared workflows |
| `.claude/agents/*.md` | ✅ shared subagents |
| `.claude/skills/*.md` | ✅ shared skills |
| `CLAUDE.md` | ✅ project AI instructions |
| `CLAUDE.local.md` | 🚫 personal notes |
| `dev-log.md` / `dev-log-*.md` | 🚫 personal journaling |

### Editing `CLAUDE.md` or `.claude/commands/`

These files change how everyone's Claude behaves. **PR review is required** — no solo edits that land directly on `dev`. Treat them like code.

### Personal vs. team commands

If a slash command is only useful to you, put it in `~/.claude/commands/` (user-scope), not the repo's `.claude/commands/`. Keep the repo's command palette clean and team-relevant.

### Memory discipline

Cowork / Claude's per-user memory (`~/.claude/…`) is personal. Do not paste its contents into repo docs — it goes stale and confuses teammates. Facts the team needs to know belong in `CLAUDE.md` or an MVP/spec doc.

## Design specs

- Dashboard design: `../dn-app-dashboard/design-specs/DESIGN.md` (source of truth).
- Mobile design: `../HanmaumDnApp/designs/dn_app/DESIGN.md` (source of truth).
- Backend has no design spec — follow the architecture rules in `CLAUDE.md`.

## `docs/superpowers/`

These are per-feature plans and specs. They are currently gitignored (see `.gitignore`). If we decide to version them, remove the `docs/` entry from `.gitignore` and move the review policy here.

## Session rules

- Run `/onboard` as the very first action in every Claude Code session.
- One feature per session — pick one, complete its layers, do not spread across features.
- If context/tokens run low: stop cleanly, commit what is done, wait for the next session. **Do not purchase extra tokens.**

## Communication

In PR descriptions, commit bodies, and design docs:

✅ Done / ⚠️ Found / 🔧 Fixed / 📋 Next / 🚫 Blocked

Be direct. File:line references. No filler.
