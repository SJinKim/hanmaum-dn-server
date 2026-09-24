## Summary

<!-- 2–3 sentences: what changed and why -->

## Linked issue

Closes #

## MVP scope

- [ ] This is MVP-required
- [ ] This is post-MVP (labeled `post-mvp`)

## Changes

<!-- Bullet list derived from commits -->

## Testing

<!-- What was run, what was verified. Paste relevant output. -->

## Screenshots

<!-- UI changes only. Otherwise: n/a -->

## Breaking changes

- [ ] Yes — describe below
- [ ] No

## Checklist

- [ ] Branch follows `<type>/HDN-<id>-<slug>`
- [ ] Commits follow Conventional Commits
- [ ] Tests added or updated
- [ ] No silent lint suppressions
- [ ] No secrets, PII, or tokens in diff
- [ ] DB migrations are safe on live data (see `db-migrations` skill)
- [ ] API contract unchanged, or ran
  `OPS_DIR=/absolute/path/to/hanmaum-dn-ops OPENAPI_ENV_FILE=/absolute/path/to/.env make openapi-sync`,
  reviewed the generated diff, and linked the ops PR
