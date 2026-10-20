# Guide detailed attendance — HDN-331

This adds future room entry to the same Guide report model from #329. It does
not migrate historical room tables, change the approved import mapping or infer
an adult/youth split from older combined counts. Examples below are synthetic.

## Routes and access

| Method | Route under `/api/v1` | Purpose |
|---|---|---|
| POST | `/guide/worship-counts/details` | Create a detailed report |
| GET | `/guide/worship-counts/{publicId}/details` | Read explicit mode, canonical report and available breakdown |
| PUT | `/guide/worship-counts/{publicId}/details` | Replace details atomically with expected version |

Guide viewers read; Guide editors write and inherit read access. Admin/pastor
retain access. Ordinary members cannot read/write Guide reports. Existing roles
and the existing-realm provisioning path from #329 are reused, without new role
assignments. This implementation does not perform a live Keycloak rollout.

All successful responses use `ApiResponse<GuideDetailsResponse>`. Its `report`
contains the same public UUID, date, notes, version and three canonical counts
used by history/statistics. `reportMode` is returned in **all** parent/list/detail
responses: `SUMMARY_ONLY` or `DETAILED`. Do not infer it from dates or counts.

## Stable matrix

| Code / display order | Label |
|---|---|
| `MAIN_1F_A` | 본당1층A |
| `MAIN_1F_B` | 본당1층B |
| `MAIN_2F_C` | 본당2층C |
| `PRESCHOOL` | 유아유치부 |

Each room has serviceNumber 1, 2 and 3: twelve unique rows and **28** active
numeric fields. Each row accepts adultCount and schoolCount. Only service 3
accepts youthCount; explicit zero for youth in service 1/2 is also rejected.
비전홀 and 소예배실 are not room codes.

POST permits an omitted/empty `entries` array for a draft. A submitted subset
is normalized to all twelve rows with omitted counts/rows **NULL**, never zero.
PUT is a full replacement: `entries`, `notes` and the expected `version` are
required; omitted rows become unknown. Explicit `notes: null` clears notes.
Unknown/read-only JSON properties are rejected, including reportMode,
totalCount and response-shaped `counts` objects. This prevents silent loss of
base counts if a read DTO is accidentally sent as a write request.

Example POST (only one populated row; every other base count remains unknown):

```json
{
  "serviceDate": "2026-10-25",
  "notes": "synthetic example",
  "entries": [
    {"roomCode": "MAIN_1F_A", "serviceNumber": 3, "adultCount": 40, "youthCount": 25, "schoolCount": 5}
  ]
}
```

This row's compatible adult count is 65, and its total is 70. Its service/day
total is still NULL until all other required base counts are explicitly entered.
Enter real zero for a counted empty cell; an unused/unknown cell stays NULL.

## Arithmetic and numeric contract

Base counts accept **JSON integer tokens or null**, range 0–2,147,483,647.
Strings, Boolean/array/object, fractions (including `1.0`), exponent tokens
(including `1e3`), negatives and overflow are rejected. No rounding, truncation
or string conversion. Expected versions similarly require nonnegative int64
integer tokens. Client input filtering is additional UX, never server validation.

Compatible adult count = adult count for services 1/2; service 3 adds youth once.
Total = compatible adult + school. Room/day adult totals sum adults across all
three services; room/day youth totals use service 3 only. All aggregates become
NULL if any required contributing count is unknown; completeness is explicit.
Independent known categories remain available even when the overall total is NULL.

Each service's known subtotal must stay within int32 even in a partial draft.
The same bound applies to its final canonical parent count. All derived counts
use int64; room/day totals are bounded by 3 × int32 max = **6,442,450,941**.
Canonical total/week-delta semantics from #329 remain unchanged. Responses contain
ordered entries, room totals with Korean labels, service totals and day totals.
Youth is null when not applicable, and is never added twice.

## Historical correction and explicit conversion

GET of a SUMMARY_ONLY report returns the original parent and empty breakdown
arrays with `totals: null`. It performs no initialization/write and invents no
room or youth values. Keep the existing summary correction UI for that mode.
Do not interpret a failed GET as absence of details.

To intentionally convert an old report, show the original counts beside the
new detail totals. Send PUT with the current `version`, explicit `notes`, the
desired matrix, and `expectedSummary` containing **all three** old nullable
canonical counts. They must match the currently locked parent. Example:

```json
{
  "serviceDate": "2026-10-25",
  "notes": "existing note retained explicitly",
  "version": 0,
  "entries": [],
  "expectedSummary": {"service1Count": 10, "service2Count": 20, "service3Count": 30}
}
```

An empty matrix in this example intentionally converts to an incomplete draft;
it does not distribute the old 10/20/30 counts. The original date, version,
three counts, notes, actor and conversion time are retained in an internal
conversion audit. No note/snapshot values are logged. The UUID is unchanged.

For existing DETAILED reports, omit expectedSummary. Direct summary PATCH is
rejected with 409, and conversion back to SUMMARY_ONLY is unavailable. PUT
locks the parent, validates the expected version and atomically saves all rows
and canonical counts. Its audit touch advances the parent version even for
same-total room redistribution. Read transactions use a consistent snapshot
so parent and detail rows cannot be mixed across concurrent commits.

## Errors and rollout

400 validation responses contain `success: false`, a safe message and
`data.fieldErrors` (for example `entries[0].adultCount`, `entries[0].youthCount`
or `services[3].totalCount`). Malformed JSON without a usable path names `body`.
No rejected values or notes are logged. Conflicts such as stale versions,
date collisions, missing/mismatched conversion confirmation and direct summary
writes to detailed reports return 409 ProblemDetail. Missing/soft-deleted
reports return 404; read/load failures are not converted into empty responses.

Three additive Flyway migrations add the mode, detail rows and conversion audit.
Existing reports default to SUMMARY_ONLY. Apply schema separately from any
historical import. Soft-deleted parents remain hidden; their detail/audit rows
are retained, with cascade cleanup only for a physical parent deletion.

The generated ops contract is the client handoff. Web entry/edit UI is tracked
in web#237/#235. Tests cover room/service totals, NULL/0, bounds/overflow, mode
protection, explicit conversion audit and real concurrent legacy/detail writers.
No deployment, live import or UI implementation is part of this server PR.
