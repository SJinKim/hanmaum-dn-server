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

### Respect repository scope
- **Mistake**: Interpreted a server change as an end-to-end request and modified the mobile repository without being asked.
- **Rule**: When the user asks to adjust behavior in a specific repository, change only that repository unless cross-repository work is explicitly requested.

### Model repeatable contacts as a collection
- **Mistake**: Modeled ministry contacts as fixed leader/coordinator fields even though contact roles can vary and grow.
- **Rule**: For repeatable role-to-person data, use an ordered collection of `{role, name}` objects instead of role-specific columns or DTO fields.
