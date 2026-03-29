# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added
- F1 Member Management backend: `Member` entity, CRUD endpoints (`/api/v1/members`), ADMIN role guards, soft delete, status-transition guard (ACTIVE↔INACTIVE→DELETED), `keycloakId` + `profileImageUrl` fields, `MemberMappers.kt`, Flyway migration, service + mapper unit tests
- F2 Ministry + Registration backend: `Ministry` + `MinistryRegistration` entities, full 8-endpoint REST API (`/api/v1/ministries`), `MinistryService` with 409 duplicate-name/registration guard, 403 unauthorized-withdraw guard, 400 inactive-ministry guard, `MinistryMappers.kt`, Flyway migration `V1774598535211` (schema alignment: `short_description`, `long_description`, FK constraint), 12 unit tests
- Dashboard Session 1 — Layout Shell: `shell.component` with top bar (branding + username + logout) and left sidebar nav (회원/부서/출석 links, `routerLinkActive` highlight, Tailwind-only); `app.component` reduced to bare `<router-outlet>`; all authenticated routes nested under shell; TypeScript upgraded 5.6.3 → 5.9.3; pre-existing `TableLazyLoadEvent` + form type errors fixed
- F3 Attendance dashboard (Session 4): `attendance.model.ts` (DefinitionDto, AttendanceLogDto, DayOfWeek types), `attendance.service.ts` (5 API methods via ApiService), `attendance-definitions` component (table + inline create form + edit dialog + deactivate confirmation), `attendance-logs` component (date-range filter + read-only log table), lazy-loaded routes `/attendance` + `/attendance/:id/logs`, nav link added to app shell
- Dashboard Session 3 — Ministry Feature (F2): `ministry.model.ts`, `ministry.service.ts`, `ministry.routes.ts`, ministry-list (table + active/inactive filter tabs + edit/deactivate actions), ministry-edit (create/update form with isActive toggle for edit), ministry-detail (info card + registrations table with period filter + remove registration); `/ministry` route added to app shell
- Dashboard Session 2 — Member Approval Workflow: status-filter tabs (전체/대기중/활성/비활성) in members-list; approve (✓) action for PENDING, reactivate (↻) for INACTIVE; `approveMember()` in `MemberService` (PATCH `{memberStatus:'ACTIVE'}`); Keycloak clientId corrected to `hanmaum-dashboard`; `isAdmin()` case-insensitive fix; `PENDING` restored as first active status in backend enum; `registerMember()` defaults to `PENDING`; `getMembers()` status filter param; `MemberStatusInterceptor` (403 for non-ACTIVE non-admin users); Flyway migration `V1775260000000` (`member_status` default → PENDING)
