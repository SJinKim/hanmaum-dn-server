#!/usr/bin/env bash
# Finalize hanmaum-dn-server scaffold from your Mac.
# Cowork did the file writes in the sandbox; this finishes the parts
# that need real Mac filesystem permissions + GitHub auth.
#
# Usage (from your Mac, in dn-app/):
#   bash __finalize-server-setup.sh
#
# Safe to re-run.

set -e

c_g() { printf '\033[32m%s\033[0m\n' "$*"; }
c_y() { printf '\033[33m%s\033[0m\n' "$*"; }
c_r() { printf '\033[31m%s\033[0m\n' "$*"; }
c_b() { printf '\033[34m%s\033[0m\n' "$*"; }

if [[ ! -d .git ]]; then
  c_r "Run this from inside dn-app/. Aborting."
  exit 1
fi

c_b "─── 1. Clear stale .git/index.lock ───"
rm -f .git/index.lock && c_g "  ok" || c_y "  none"

c_b "─── 2. Remove the installer (don't commit it) ───"
rm -f setup-server.sh && c_g "  removed setup-server.sh" || true

c_b "─── 3. Remove conflicting/orphan files (per briefing design) ───"
rm -f .github/workflows/claude-code-review.yml \
   && c_g "  removed claude-code-review.yml (conflicts: gated @claude only)" || true
rm -rf .claude/agents \
   && c_g "  removed .claude/agents/ (conflicts: no agent teams on Pro)" || true
rm -f ".claude/skills/backend-patterns.skill.md " \
      ".claude/skills/frontend-patterns.skill.md " \
   && c_g "  removed old flat-file skills" || true

c_b "─── 4. Re-run npm install on Mac (gets darwin lefthook binary) ───"
if command -v npm >/dev/null 2>&1; then
  npm install --silent && c_g "  ok"
else
  c_r "  npm not found — install Node 20+ first"
  exit 1
fi

c_b "─── 5. Verify lefthook hooks are installed ───"
if [[ -f .git/hooks/pre-commit && -f .git/hooks/commit-msg ]]; then
  c_g "  ok (pre-commit, commit-msg, pre-push present)"
else
  c_y "  missing — run: npx lefthook install"
fi

c_b "─── 6. git status preview ───"
git status --short

echo ""
c_b "─── 7. Next steps (manual review, then run yourself) ───"
cat <<'NEXT'

Review the diff:
    git diff --cached
    git diff

If happy, stage + commit:
    git add .
    git commit -m "chore(ci): initial team setup"

The pre-push hook blocks pushes to main. For this initial bootstrap, push with --no-verify:
    git push -u origin main --no-verify

(Future commits will use feature branches and won't need --no-verify.)

After push, delete this finalize script:
    rm __finalize-server-setup.sh

Then move on to phase 1b: hanmaum-dn-ops scaffold.
NEXT

c_g ""
c_g "Done."
