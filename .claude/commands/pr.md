---
description: Push current branch and open a PR using the template
argument-hint: [optional: PR title override]
allowed-tools: Bash(git push origin:*), Bash(git log:*), Bash(git diff:*), Bash(gh pr:*), Bash(git branch:*)
---

Open a pull request for the current branch.

1. Confirm branch is not `main`. Stop if it is.
2. Confirm branch name matches `<type>/HDN-<id>-<slug>`. If not, warn and ask.
3. Push the branch: `git push -u origin <branch>`.
4. Extract the HDN issue number from the branch name.
5. Populate `.github/pull_request_template.md`:
   - **Summary** — 2–3 sentences on the change.
   - **Linked issue** — `Closes #<number>`.
   - **Changes** — bullets derived from `git log main..HEAD --oneline`.
   - **Testing** — what was run; paste output if available.
   - **Screenshots** — placeholder if UI, otherwise "n/a".
   - **Breaking changes** — explicit yes/no.
6. Open the PR: `gh pr create --base main --body-file -`.
7. Do not auto-merge. Do not enable auto-merge.

Title override (if provided): $ARGUMENTS
