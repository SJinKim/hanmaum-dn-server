# HDN-331 — configurable Guide details

## Approved scope update: configurable counting areas backed by room master data

Supersedes the fixed room enum/12-row contract above, as explicitly requested by the user on 20 October 2026. Continue server #331 / PR337 and generated ops PR65; no new unrelated feature, UI, deployment or merge.

1. Add Guide counting-area configuration referencing existing `room`: UUID identity, label, display order, active flag and expected-version edits. GET/POST `/api/v1/guide/counting-areas`, PUT `/{publicId}`, GET `/rooms` for eligible room selection. Existing GUIDE_VIEWER reads, GUIDE_EDITOR writes; ADMIN/PASTOR hierarchy preserved. Missing/deleted room/floor rejected, version conflicts 409, invalid data 400 field errors.
2. Seed the four approved initial areas from the existing room table (A/B reference the same 1F sanctuary; C the 2F sanctuary; preschool its existing room). No new physical rooms or enum; 비전홀/소예배실 stay excluded initially but remain selectable existing rooms for intentional future additions.
3. At detail create or explicit summary conversion, persist a report-specific snapshot of active area membership, room identity/name, label and order. Later configuration changes affect new reports only. Existing detailed edits always retain their snapshot, including deactivated/renamed rooms/areas. An empty active configuration cannot create/convert a detail report (409).
4. Detail inputs use `areaPublicId` + serviceNumber, never internal IDs or enum codes. Normalize three services per snapshot area, any number of areas; omitted counts remain NULL. Preserve existing numeric/auth/conversion/parent-version/concurrency rules and canonical summary totals. Foreign keys + uniqueness enforce report/area/service ownership.
5. Forward-only migrations preserve any pre-revision detail reports and original totals/versions. Test dynamic fifth area, room reuse, activation/order/rename isolation, deleted-room handling, snapshot edits and old migration upgrade. Regenerate API contract, update both open PRs, run focused tests then full gates and exact-head CI. No merge/deploy/live import.

Progress: implementation and focused tests passed; migration upgrade preserves old detail counts/notes/version. Full default suite (1,179 tests), focused Guide suite (61 tests), lint, bootJar, 87-migration startup and generated contract checks passed. PR337 / ops65 updated for review; no merge or live rollout.
