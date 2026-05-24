# Lessons Learned

> Updated after every user correction. Reviewed at session start.

## Format
- **Mistake**: what went wrong
- **Rule**: what to do instead (permanent prevention)

## Lessons
<!-- Claude appends here after each correction -->

### Deploy order: staging before production
- **Mistake**: Pushing directly to the production-targeting branch without going through staging first.
- **Rule**: Always push to the `dev` branch first. Staging (st / `dn-admin-dashboard.st.graceops.de`) must deploy and be verified before promoting to production (`dn-admin-dashboard.graceops.de`). Never skip the staging step.
