---
description: Show current sprint progress from GitHub milestones
allowed-tools: Bash(gh:*), Bash(date:*)
---

Summarize the current sprint.

1. List open milestones:
   `gh api repos/:owner/:repo/milestones --jq '.[] | {title, due_on, open_issues, closed_issues}'`
2. Identify the active sprint (open milestone with the nearest future `due_on`).
3. Fetch its issues:
   `gh issue list --milestone "<title>" --state all --json number,title,state,assignees,labels`
4. Produce a compact report:
   - Sprint name, days remaining until due date
   - Issue counts: total, done, in progress, todo
   - Breakdown by assignee
   - Blocked items (label: `blocked`)
   - At-risk items (still open, due in ≤2 days)
5. End with the Project board link.

Read-only. Do not modify any issues.
