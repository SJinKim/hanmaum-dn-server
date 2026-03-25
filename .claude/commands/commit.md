# Commit & Push Convention

## Format
<type>(<scope>): <summary max 72 chars>

Types: feat | fix | refactor | test | chore | docs | perf | revert
- Imperative mood ("add" not "added"), no period
- Body: explain WHY, not WHAT
- Reference issues: closes #42

## Pre-Commit Checklist
1. ./gradlew test && ./gradlew ktlintCheck
2. ng test --watch=false --browsers=ChromeHeadless
3. Update CHANGELOG.md