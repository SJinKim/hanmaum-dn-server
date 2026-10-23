# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased] — 2026-03-30

### Fixed
- HDN-355: Normalize Guide counting-area positions, atomically save complete versioned orders and remove inactive configuration without changing physical rooms or report history.
- HDN-331: Harden Guide review flows with explicit editor reads, safe room seeding, bounded detail queries, controlled matrix conflicts, explicit detail revisions and settings-specific validation errors; remove the legacy room code after snapshot backfill.
- HDN-311: Reject attendance check-ins from members that are not ACTIVE with 403 before any window, geofence or log is touched.
- HDN-135: Notify members once per course when an existing application is accepted as ENROLLED, after the replacement commits.
- HDN-133: Notify members after their pending membership is approved; respect push preferences and skip rolled-back changes.
- HDN-245: Restore a verified login email when the matching dashboard member was deleted before the first app login.
- HDN-284: Label members without a recorded gender as 미입력 in dashboard statistics.
- HDN-299: Reconcile application realm roles, nested groups and role mappings from both exports on existing Keycloak realms; add an access-only mode and repeat-apply checks.
- HDN-307: Preserve training flags during concurrent member edits, require ACTIVE church-group leaders and keep new group handlers admin-only by default.
- HDN-296: Hide published future bulletins from every member endpoint until Friday before their Sunday (Europe/Berlin); preserve older editions and accurate history pagination.
- HDN-172: Generate and sync OpenAPI safely from worktrees using a disposable database, dedicated port, and validated ops checkout.
- HDN-202: Align newcomer import fingerprint column types with Hibernate's VARCHAR(64) mapping so schema validation can start the app.

### Added
- HDN-333: Add a consistent Guide Home snapshot with church-Sunday selection, confirmed partial drafts, historical detail snapshots, four recent reports, independently dated complete-report metrics and eight NULL-aware Sunday points.
- HDN-330: Provide Guide calendar-period statistics with complete-report cohorts, nullable comparisons and a shared table/chart snapshot.
- HDN-331: Add configurable room-backed Guide counting areas with per-report snapshots and versioned service entry, separate service-3 youth counts, NULL-aware canonical totals, explicit audited summary conversion and protection against direct summary overwrites.
- HDN-329: Add Guide summary reports with UUID CRUD, strict nullable integer counts, derived totals/week deltas, version conflicts, scoped Guide roles and an explicit audited historical import with dry-run, idempotent retry and guarded rollback. The one-shot importer requires a maintenance profile; list deltas use a bulk lookup and XLSX fractions are rejected.
- HDN-312: Count the day's 빠른 등록 visits in the 새가족 row of the 순별 attendance counts, once per day, without counting a linked newcomer who also checked in; expose `quickRecordCount` and `totalQuickRecordCount`.
- HDN-307: Add 양육-team roles and groups, a scoped ACTIVE-member matrix and 예비순장 flag API, and church-group leader management.
- HDN-306: General operations viewers read attendance, events, ministries, bulletins and announcements; editors also manage them across all ministries.
- HDN-170: Active members can apply to ministries with a private introduction; leaders receive an email, review pending team applications, and send a private rejection message before reapplication.
- HDN-218: Store an optional weekday per ministry schedule and expose it in create, update, and detail responses.
- HDN-216: Store an optional location per ministry schedule and expose it in create, update, and detail responses.
- HDN-214: Show ministry member counts, previews, roles, status, and history; manage individual assignments and leaders from the admin screens.
- HDN-212: Include training start dates in member detail and preserve imported training details when admins save the training set.
- HDN-208: Include the latest retained group-leader tenure in member detail, even after leadership ends.
- HDN-206: Show the number of active members still awaiting a response across open event RSVPs on the web Home dashboard.
- HDN-197: Admins can create, view, and update encrypted member occupation (직업) in the detail API.
- HDN-136: Members who have responded to an event RSVP are notified after its schedule changes, with the previous and revised period in the notification
- F3 Attendance App: Morning Service check-in card on home screen — shows today's active service definition, "출석하기" button enabled during check-in window, handles duplicate check-in (409) and outside-window (400) gracefully
- F2 App: Ministry list screen (active ministries with name, leader, short description)
- F2 App: Ministry detail screen (long description + registration button)
- F2 App: Registration bottom sheet with self-introduction note
- F2 App: Registration status display — 신청하기 / 신청되었습니다 / 멤버입니다 ✓
- F2 Backend: `RegistrationStatus` enum (PENDING/APPROVED/REJECTED) on `ministry_registrations`
- F2 Backend: `GET /ministries/{id}/registrations/me` — member own registration for current year
- F2 Backend: `PATCH /ministries/{id}/registrations/{regId}` — admin approve/reject
- F2 Backend: Re-apply logic — REJECTED record replaced with new PENDING on re-POST
- F1 Mobile App (Session 5): Profile screen (own profile view + inline edit for phone & profileImageUrl), persistent token storage (multiplatform-settings), token refresh in Ktor bearer plugin, bottom nav Profile tab, `MemberResponse`/`MemberStatus` aligned with backend
- F1 Mobile (Session 5): `phoneNumber` added to `GET /me` response; new `PATCH /me` endpoint for self-profile update (phone + profileImageUrl); SecurityConfig POST /me whitelist removed
- Shell: decomposed into `SidebarComponent` + `HeaderComponent` per DESIGN.md spec
- Shell: Manrope font, design tokens (#2B3A67 primary palette), CSS variables wired to Tailwind
- Shell: collapsible sidebar (256px ↔ 72px), localStorage-persisted, 3px left accent bar on active nav
- Home: Liturgical Dashboard — 4 stat cards (total/active members, ministry count, avg attendance), dual-line growth chart, demographics panel, recent activity table
- Members: restyled list — role/status filter dropdowns, PENDING amber banner, separate Approve/Edit columns, fit-content status badges, `updatedAt` Last Active column
- Members: restyled detail page — two-column card layout, avatar initials, role + status badges, section headings
- Members: restyled edit/create page — design token styling, section headings, styled action buttons
- HDN-YY: Member graduation tracked as a reversible event in `member_graduations` — a member may hold many historical graduations but at most one open, with an audit trail of who marked whom, when, and why (`MARRIAGE`/`RELOCATION`/`AGE_OUT`/`OTHER`)
- HDN-YY: Graduating snapshots the member's current status and sets them `INACTIVE`, which removes app access through the existing `MemberStatusInterceptor`; reinstating restores the snapshot rather than assuming `ACTIVE`
- HDN-YY: `graduated` / `graduatedOn` on the member detail and list responses, derived from the open graduation row rather than a stored column so the two can never disagree — the list batches one extra query per page
- HDN-YY: 양육 마스터 Excel import withdrawn — no member data is taken from the workbook (the training catalog and cohorts remain as seeded static data)

## [Unreleased]

### Added
- F1 Member Management backend: `Member` entity, CRUD endpoints (`/api/v1/members`), ADMIN role guards, soft delete, status-transition guard (ACTIVE↔INACTIVE→DELETED), `keycloakId` + `profileImageUrl` fields, `MemberMappers.kt`, Flyway migration, service + mapper unit tests
- F2 Ministry + Registration backend: `Ministry` + `MinistryRegistration` entities, full 8-endpoint REST API (`/api/v1/ministries`), `MinistryService` with 409 duplicate-name/registration guard, 403 unauthorized-withdraw guard, 400 inactive-ministry guard, `MinistryMappers.kt`, Flyway migration `V1774598535211` (schema alignment: `short_description`, `long_description`, FK constraint), 12 unit tests
- Dashboard Session 1 — Layout Shell: `shell.component` with top bar (branding + username + logout) and left sidebar nav (회원/부서/출석 links, `routerLinkActive` highlight, Tailwind-only); `app.component` reduced to bare `<router-outlet>`; all authenticated routes nested under shell; TypeScript upgraded 5.6.3 → 5.9.3; pre-existing `TableLazyLoadEvent` + form type errors fixed
- F3 Attendance dashboard (Session 4): `attendance.model.ts` (DefinitionDto, AttendanceLogDto, DayOfWeek types), `attendance.service.ts` (5 API methods via ApiService), `attendance-definitions` component (table + inline create form + edit dialog + deactivate confirmation), `attendance-logs` component (date-range filter + read-only log table), lazy-loaded routes `/attendance` + `/attendance/:id/logs`, nav link added to app shell
- Dashboard Session 3 — Ministry Feature (F2): `ministry.model.ts`, `ministry.service.ts`, `ministry.routes.ts`, ministry-list (table + active/inactive filter tabs + edit/deactivate actions), ministry-edit (create/update form with isActive toggle for edit), ministry-detail (info card + registrations table with period filter + remove registration); `/ministry` route added to app shell
- Dashboard Session 2 — Member Approval Workflow: status-filter tabs (전체/대기중/활성/비활성) in members-list; approve (✓) action for PENDING, reactivate (↻) for INACTIVE; `approveMember()` in `MemberService` (PATCH `{memberStatus:'ACTIVE'}`); Keycloak clientId corrected to `hanmaum-dashboard`; `isAdmin()` case-insensitive fix; `PENDING` restored as first active status in backend enum; `registerMember()` defaults to `PENDING`; `getMembers()` status filter param; `MemberStatusInterceptor` (403 for non-ACTIVE non-admin users); Flyway migration `V1775260000000` (`member_status` default → PENDING)
