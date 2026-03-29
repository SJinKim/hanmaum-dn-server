# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added
- F1 Member Management backend: `Member` entity, CRUD endpoints (`/api/v1/members`), ADMIN role guards, soft delete, status-transition guard (ACTIVE↔INACTIVE→DELETED), `keycloakId` + `profileImageUrl` fields, `MemberMappers.kt`, Flyway migration, service + mapper unit tests
- F2 Ministry + Registration backend: `Ministry` + `MinistryRegistration` entities, full 8-endpoint REST API (`/api/v1/ministries`), `MinistryService` with 409 duplicate-name/registration guard, 403 unauthorized-withdraw guard, 400 inactive-ministry guard, `MinistryMappers.kt`, Flyway migration `V1774598535211` (schema alignment: `short_description`, `long_description`, FK constraint), 12 unit tests
- F3 Attendance dashboard (Session 4): `attendance.model.ts` (DefinitionDto, AttendanceLogDto, DayOfWeek types), `attendance.service.ts` (5 API methods via ApiService), `attendance-definitions` component (table + inline create form + edit dialog + deactivate confirmation), `attendance-logs` component (date-range filter + read-only log table), lazy-loaded routes `/attendance` + `/attendance/:id/logs`, nav link added to app shell
