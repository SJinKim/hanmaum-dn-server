# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased] — 2026-03-30

### Added
- Shell: decomposed into `SidebarComponent` + `HeaderComponent` per DESIGN.md spec
- Shell: Manrope font, design tokens (#2B3A67 primary palette), CSS variables wired to Tailwind
- Shell: collapsible sidebar (256px ↔ 72px), localStorage-persisted, 3px left accent bar on active nav
- Home: Liturgical Dashboard — 4 stat cards (total/active members, ministry count, avg attendance), dual-line growth chart, demographics panel, recent activity table
- Members: restyled list — role/status filter dropdowns, PENDING amber banner, separate Approve/Edit columns, fit-content status badges, `updatedAt` Last Active column
- Members: restyled detail page — two-column card layout, avatar initials, role + status badges, section headings
- Members: restyled edit/create page — design token styling, section headings, styled action buttons

## [Unreleased]

### Added
- F1 Member Management backend: `Member` entity, CRUD endpoints (`/api/v1/members`), ADMIN role guards, soft delete, status-transition guard (ACTIVE↔INACTIVE→DELETED), `keycloakId` + `profileImageUrl` fields, `MemberMappers.kt`, Flyway migration, service + mapper unit tests
- F2 Ministry + Registration backend: `Ministry` + `MinistryRegistration` entities, full 8-endpoint REST API (`/api/v1/ministries`), `MinistryService` with 409 duplicate-name/registration guard, 403 unauthorized-withdraw guard, 400 inactive-ministry guard, `MinistryMappers.kt`, Flyway migration `V1774598535211` (schema alignment: `short_description`, `long_description`, FK constraint), 12 unit tests
- Dashboard Session 1 — Layout Shell: `shell.component` with top bar (branding + username + logout) and left sidebar nav (회원/부서/출석 links, `routerLinkActive` highlight, Tailwind-only); `app.component` reduced to bare `<router-outlet>`; all authenticated routes nested under shell; TypeScript upgraded 5.6.3 → 5.9.3; pre-existing `TableLazyLoadEvent` + form type errors fixed
- F3 Attendance dashboard (Session 4): `attendance.model.ts` (DefinitionDto, AttendanceLogDto, DayOfWeek types), `attendance.service.ts` (5 API methods via ApiService), `attendance-definitions` component (table + inline create form + edit dialog + deactivate confirmation), `attendance-logs` component (date-range filter + read-only log table), lazy-loaded routes `/attendance` + `/attendance/:id/logs`, nav link added to app shell
- Dashboard Session 3 — Ministry Feature (F2): `ministry.model.ts`, `ministry.service.ts`, `ministry.routes.ts`, ministry-list (table + active/inactive filter tabs + edit/deactivate actions), ministry-edit (create/update form with isActive toggle for edit), ministry-detail (info card + registrations table with period filter + remove registration); `/ministry` route added to app shell
- Dashboard Session 2 — Member Approval Workflow: status-filter tabs (전체/대기중/활성/비활성) in members-list; approve (✓) action for PENDING, reactivate (↻) for INACTIVE; `approveMember()` in `MemberService` (PATCH `{memberStatus:'ACTIVE'}`); Keycloak clientId corrected to `hanmaum-dashboard`; `isAdmin()` case-insensitive fix; `PENDING` restored as first active status in backend enum; `registerMember()` defaults to `PENDING`; `getMembers()` status filter param; `MemberStatusInterceptor` (403 for non-ACTIVE non-admin users); Flyway migration `V1775260000000` (`member_status` default → PENDING)
