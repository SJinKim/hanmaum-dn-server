# HDN-331 implementation plan

## Implementation contract for #331

1. Add POST `/api/v1/guide/worship-counts/details`, GET/PUT `/api/v1/guide/worship-counts/{publicId}/details`. Reuse the existing UUID/date/notes/version and Guide permissions. `reportMode` stays the existing string field, with explicit `SUMMARY_ONLY` / `DETAILED` values in list/get/detail responses.
2. Stable room codes in display order: `MAIN_1F_A`, `MAIN_1F_B`, `MAIN_2F_C`, `PRESCHOOL` (본당1층A, 본당1층B, 본당2층C, 유아유치부). Entry keys are roomCode + serviceNumber (1–3). Omitted entries/counts become unknown, never zero; a full detail save replaces the matrix and normalizes it to 12 rows. Youth is permitted only for service 3, including rejecting explicit zero elsewhere.
3. Single values are JSON integer tokens or null, 0…2,147,483,647; strings, fractions, exponent tokens, booleans, arrays/objects and overflow are rejected with 400 and field errors in an ApiResponse payload. Known subtotals for each service must stay within int32 even in drafts. Canonical service sums remain int32; all other derived totals use int64, maximum 6,442,450,941 across the three services. Any missing required count makes that sum null. Compatible adult = adult + youth at service 3; youth is added exactly once.
4. PUT requires the expected report version and explicit notes (null clears). Converting SUMMARY_ONLY requires an `expectedSummary` object matching the three existing canonical counts, alongside the version; preserve the original date/counts/notes/version/actor in a conversion audit. Plain GET never changes a summary or invents detail rows. Detail reports reject the original direct-summary PATCH, and cannot be silently converted back. Every detail save advances the parent version, including same-total room redistribution.
5. Add forward-only schema migrations, meaningful aggregation/controller/PostgreSQL concurrency tests, generated OpenAPI sync + linked ops PR. Historical import remains unchanged. No deployment, live import or automatic merge.

Progress: implementation and pre-PR review complete; PR delivery pending.

Validation:
- Full default test suite, ktlintCheck and bootJar passed.
- All 52 focused Guide/Worship tests passed against disposable PostgreSQL, including persistence, concurrent summary/detail writes and existing historical-import regressions.
- Existing summary migration upgrade retained counts, date, notes and version; no detail rows were invented.
- OpenAPI regenerated from this checkout against disposable PostgreSQL. Existing paths are unchanged; operation IDs, references, nullable objects and success/error responses were checked.
- Backend-only change: frontend checks are not applicable. No secrets, PII, TODO/FIXME additions, deployment, live import or merge.
