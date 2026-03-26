# Definition of Done

## Functional
- [ ] Feature works end-to-end (backend → frontend → auth)
- [ ] Unit + integration tests passing
- [ ] No console.log, dead code, hardcoded secrets
- [ ] New endpoints have @PreAuthorize
- [ ] Migration scripts idempotent + named `V{timestamp}__{description}.sql`

## Build
- [ ] ./gradlew build — no warnings
- [ ] ./gradlew ktlintCheck — clean
- [ ] ng build --configuration production — no errors

## Before Commit
- [ ] CHANGELOG.md: append entry under [Unreleased] (Added/Changed/Fixed/Removed/Security)
  Plain English, no commit hashes — never rewrite the full file
- [ ] Commit message follows convention (type(scope): summary)
- [ ] Branch rebased on main — no merge commits

## After completing a feature slice
Update MVP.md:
- Mark the completed layer (backend / dashboard / app) with ✅
- Change [ ] to [~] when first layer is done
- Change [~] to [x] only when ALL three layers are complete
- Never delete entries — status updates only
- Never rewrite the full file — edit only the affected line(s)
