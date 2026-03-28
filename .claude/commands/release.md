# Release Workflow

## Branch Strategy
- Feature branches: from `dev` → back to `dev`
- Release branches: from `dev` → merged to `main` AND `dev`
- Hotfix branches: from `main` → merged to `main` AND `dev`
- Never branch features from `main`
- Never commit directly to `main` or `dev`

## Pre-Release Checklist
Before creating a release branch:
- [ ] All target features are merged into `dev`
- [ ] MVP.md Build Order — all target rows fully ✅
- [ ] `./gradlew build` — no warnings
- [ ] `ng build --configuration production` — no errors
- [ ] All tests passing

## Release Steps

### 1. Create release branch from dev
```bash
git checkout dev
git pull origin dev
git checkout -b release/0.1.0-rc.1
```

### 2. Sync versions
- `backend/build.gradle.kts` → `version = "0.1.0-rc.1"`
- `frontend/package.json` → `"version": "0.1.0-rc.1"`
- `CHANGELOG.md` → rename `[Unreleased]` to `[0.1.0-rc.1] - YYYY-MM-DD`

### 3. Commit version bump
```bash
git add .
git commit -m "chore(release): bump version to 0.1.0-rc.1"
```

### 4. Merge to main + tag
```bash
git checkout main
git merge --no-ff release/0.1.0-rc.1
git tag -a v0.1.0-rc.1 -m "chore(release): v0.1.0-rc.1"
git push origin main
git push origin v0.1.0-rc.1
```

### 5. Back-merge to dev
```bash
git checkout dev
git merge --no-ff release/0.1.0-rc.1
git push origin dev
```

### 6. Delete release branch
```bash
git branch -d release/0.1.0-rc.1
git push origin --delete release/0.1.0-rc.1
```

## Pre-Release Labels
SemVer: `MAJOR.MINOR.PATCH[-label.N]`

| Label | Meaning |
|---|---|
| `0.1.0-alpha.1` | Early internal — expect breakage |
| `0.1.0-beta.1` | Feature-complete, bug fixing phase |
| `0.1.0-rc.1` | Release candidate — critical fixes only |
| `0.1.0` | Stable production release |

Every RC and stable release must be tagged.

## After Release
- Verify tag exists: `git tag -l "v0.1*"`
- Switch back to dev for next feature cycle:
```bash
git checkout dev
```
