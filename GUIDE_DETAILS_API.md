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

## Configurable areas backed by room master data

The existing `room` table is the physical-room source of truth. Guide counting
areas reference rooms by foreign key and expose UUIDs. Multiple areas may
reference one room, so 본당1층A and 본당1층B use the same 1F sanctuary.
There is no room enum or fixed four-area/twelve-row limit in application code.

| Method | Route under `/api/v1/guide/counting-areas` | Purpose |
|---|---|---|
| GET | (root) | Current configuration, including inactive areas and versions |
| GET | `/rooms` | Existing active rooms on active floors, with room/floor UUIDs |
| POST | (root) | Add area for future reports; roomPublicId, label, displayOrder, active |
| PUT | `/{publicId}` | Replace those settings with expected version; 409 stale, 404 absent |

Existing Guide read/write roles apply. A label has 1–200 characters; displayOrder
is a nonnegative JSON integer. Room/floor must be active. Deactivating an existing
area without changing its room is also allowed after that room is deleted.
No physical rooms are created and no application deployment is needed to add an
area using the API. The configuration management UI belongs to subsequent web work.

Initial configuration:

| Label | Existing room |
|---|---|
| 본당1층A | 1-1 대예배실, floor 1 |
| 본당1층B | 1-1 대예배실, floor 1 |
| 본당2층C | 대예배실 2층, floor 2 |
| 유아유치부 | 1-3 유아유치부실, floor 1 |

비전홀 and 소예배실 are initially not configured for counting; they remain ordinary
room master records and may be intentionally selected later. No rooms are matched
by display name at runtime; the initial migration requires unambiguous existing
rooms rather than inventing or guessing physical locations.

At detail creation or explicit SUMMARY_ONLY conversion, the server snapshots the
currently active eligible areas: membership, room identity/name, area label and
order. Each contributes three service rows and seven numeric inputs. The initial
four areas therefore produce twelve rows / 28 inputs; five produce fifteen / 35.
No active eligible areas returns 409. A later activation, reassignment, room rename,
deactivation or reordering applies to new reports only. Existing reports always
read and edit their original snapshot, including archived rooms/areas. New areas
cannot be injected into an existing report by sending their UUIDs.

`rooms` in the detail response lists these counting-area snapshots (one item per
area, even when areas share a room) with areaPublicId, roomPublicId, roomName,
label, displayOrder and derived counts. Detail entries use **areaPublicId +
serviceNumber**, never a room enum or internal database ID.

POST permits omitted/empty `entries` for a draft. Subsets normalize to three
rows per snapshot area; omitted counts are NULL, never implicit zero. Each row
accepts adultCount/schoolCount; only service 3 accepts youthCount (explicit youth
zero on service 1/2 is rejected). PUT fully replaces the counts and requires
entries, explicit notes (null clears) and expected version. Unknown/read-only
properties, including old roomCode, reportMode, totals and response-shaped counts,
are rejected. A concurrent configuration addition can make a new report incomplete;
the returned snapshot is authoritative for completing that draft.

Synthetic example POST, using an area UUID returned by configuration:

```json
{
  "serviceDate": "2026-10-25",
  "notes": "synthetic example",
  "entries": [
    {"areaPublicId": "e3310000-0000-4000-8000-000000000001", "serviceNumber": 3, "adultCount": 40, "youthCount": 25, "schoolCount": 5}
  ]
}
```

This row's compatible adult count is 65, and its total is 70. Service/day totals
remain NULL until all required snapshot counts are explicitly entered.

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

Additive Flyway migrations add the mode, detail rows, conversion audit, room-backed
configuration and report snapshots. A forward migration backfills the original
four-area detail matrices without changing their counts, notes or parent version.
Existing reports default to SUMMARY_ONLY. Apply schema separately from any
historical import. Soft-deleted parents remain hidden; their detail/audit rows
are retained, with cascade cleanup only for a physical parent deletion.

The generated ops contract is the client handoff. Web entry/edit UI is tracked
in web#237/#235. Tests cover area/service totals, NULL/0, bounds/overflow, mode
protection, explicit conversion audit and real concurrent legacy/detail writers.
No deployment, live import or UI implementation is part of this server PR.
