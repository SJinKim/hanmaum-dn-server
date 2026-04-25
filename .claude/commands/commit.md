---
description: Create a Conventional Commit from staged changes
argument-hint: [optional: extra context]
allowed-tools: Bash(git status:*), Bash(git diff:*), Bash(git log:*), Bash(git add:*), Bash(git commit:*), Bash(git branch:*)
---

Create a single Conventional Commit from currently staged changes.

1. Run `git status` and `git diff --cached`. Summarize what's staged.
2. If nothing is staged, stop and ask what to stage — never `git add .` blindly.
3. Detect the branch (`git branch --show-current`). Extract the ticket ID
   matching `HDN-<number>`.
4. Generate the message:

   <type>(<scope>): <imperative subject, ≤72 chars>

   <body explaining *what* and *why*, not *how*; wrap at 80 chars>

   Refs: #<issue-number>

5. Types: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert.
   Scopes: auth, member, api, db, config, ci.
6. One logical change per commit. If the staged diff spans multiple concerns,
   stop and split first.
7. Never `--force`. Never push to `main`. Never amend an already-pushed commit.

Extra context from user: $ARGUMENTS
