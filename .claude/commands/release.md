---
description: Ship main to an environment, verify it, then tag it and write the release notes
argument-hint: [optional: environment — staging (default) or production]
allowed-tools: Bash(git:*), Bash(gh:*), Bash(curl:*)
---

Ship the current `main` to an environment and record what went out.

## What this file used to say, and why it was wrong

The previous version described feature branches cut from `dev`, release branches merged
into `dev` *and* `main`, and a version bump in `backend/build.gradle.kts` plus
`frontend/package.json`. None of it applies:

- `dev` was retired in #104 (`ci: retire dev branch, gate staging deploys to manual +
  main-only`, merged 2026-08-06) and the branch is deleted on the remote and locally.
  There is only `main`.
- Neither `backend/build.gradle.kts` nor `frontend/package.json` exists here. The build
  file is `build.gradle.kts` at the root; there is no frontend in this repository.

Do not reintroduce any of it.

## Versioning

| Environment | Tag | GitHub Release |
|---|---|---|
| Staging | `v0.MINOR.PATCH-st` | mark as prerelease |
| Production | `v1.MINOR.PATCH`, from the first production release onwards | full release |

`-st` is a SemVer prerelease identifier, so `v0.2.0-st` sorts *before* a plain `v0.2.0` —
which is what a staging build is. Staging stays inside `v0.x.y` until production ships for
the first time; `v1.x.y` is reserved for that moment.

The Gradle `version` (`0.0.1-SNAPSHOT` in `build.gradle.kts`) drives nothing that is
deployed: CI tags the image `ghcr.io/sjinkim/hanmaum-dn-server:sha-<short>` and the deploy
pins that tag. The git tag is the authoritative version. Do not treat a stale Gradle
version as a release blocker.

## Steps

### 1. Check the ground

- Everything meant to go out is merged into `main`, and CI on `main` is green.
- Working tree clean, local `main` equals `origin/main`.
- If any controller mapping or DTO changed since the last sync: `git fetch` in
  `hanmaum-dn-ops` **first**, then compare a freshly generated spec against
  `origin/main` there — never against a local checkout, which is stale more often than not.

### 2. Deploy staging

Manual only, `main` only — both pipelines refuse any other ref:

```bash
gh workflow run deploy-staging.yml --ref main
gh run list --workflow deploy-staging.yml --limit 1
```

Staging is always first. Production never goes out on an unverified staging build.

### 3. Verify, and read a failure carefully

A `failure` on the run does **not** by itself mean the application failed to deploy. The
deploy step continues past the container into infrastructure assertions, and one of them
is known to fail independently of the application:

```
Staging postgres_exporter cannot monitor PostgreSQL
```

That gate (`deploy-staging.yml`, the `pg_up` check) runs *after* the backend is up. When
it trips, the external smoke test behind it never runs. So on any failure, first find out
how far it got:

```bash
gh run view <run-id> --log-failed | tail -40
```

Look for `Container hanmaumApp-backend-staging  Started` followed by
`Staging backend is healthy`. If both are there, the application is live and the failure
is behind it. Then run the skipped smoke test by hand:

```bash
curl -fsS https://api.staging.graceops.de/actuator/health
curl -s -o /dev/null -w '%{http_code}\n' https://api.staging.graceops.de/api/v1/verses/weekly  # 401 = endpoint is there
```

Report the infrastructure failure separately instead of folding it into the release, and
do not silently mark the release as clean.

### 4. Tag and write the release notes

Tag the exact commit that was deployed, not whatever `main` points at by then:

```bash
SHA=$(git rev-parse origin/main)
gh release create v0.X.Y-st --target "$SHA" --title "v0.X.Y-st — <short subject>" \
  --notes-file <file> --prerelease
```

`--target` needs the full SHA; an abbreviated one is rejected as an invalid
`target_commitish`.

Structure the notes so someone who did not do the work can read them:

- **Auf einen Blick** — what a member notices, in plain words. Not the commit subject.
- **Added / Changed / Fixed** — grouped, each entry naming the issue or PR.
- **Kompatibilität** — migrations, new environment variables, client impact. Say "none"
  explicitly when there is none; that is the thing a reader is checking for.
- **Deployment & Verifikation** — what was deployed, what was verified and how. If the
  pipeline reported a failure, say so here and say what it was.
- **Bekannte Punkte** — what is broken, deferred or lagging, including how far behind the
  other environment is.
- Compare link: `<previous-sha>...<deployed-sha>`.

Compare against the previously deployed commit, which is the `headSha` of the last
successful run of the same pipeline — not the previous tag, which may be from a different
environment:

```bash
gh run list --workflow deploy-staging.yml --limit 3 --json createdAt,headSha,conclusion
```

Write them in the language the issues and PRs are written in.

### 5. Production, when it is time

```bash
gh workflow run deploy-prod.yml --ref main
```

The `production` environment carries a required-reviewer gate, so the run waits for
approval. Prerequisites: staging is verified and has been running the same commit,
`CHANGELOG.md`/notes are written, and the gap is understood — production has been known to
sit months behind staging, in which case the release notes cover *everything* since the
last production deploy, not just the newest change.

Tag production releases without `-st` and publish them as a full release, not a prerelease.

## After

- `git fetch --tags && git tag -l` — confirm the tag is on the remote.
- Board: move the shipped issues to Done if the merge did not already.
- No back-merge step. There is nothing to back-merge into.
