# Dashboard F1/F2/F3 Design Spec
**Date:** 2026-03-28
**Scope:** Admin dashboard (dn-app-dashboard) — Member Management, Ministry, Attendance

---

## Context

The dashboard is an admin-only web app (Angular 21 + PrimeNG + Tailwind). Its purpose:
- Approve/manage church members
- Manage ministries and view registrations
- Manage attendance definitions and review check-in logs

The mobile app (HanmaumDnApp) is the member-facing client. The dashboard is a prerequisite for app launch — members cannot be approved without it.

---

## Member Status Lifecycle

```
Register (Keycloak) → PENDING
    PENDING  ──── admin approves ──────→ ACTIVE
    ACTIVE   ──── 3 months no login ──→ INACTIVE
    INACTIVE ──── admin re-approves ──→ ACTIVE
    ACTIVE / INACTIVE ── admin deletes → DELETED  (terminal)
```

- **PENDING** — newly registered, cannot use app, waits for admin approval
- **ACTIVE** — approved, full app access
- **INACTIVE** — no login for 3+ months, blocked from app, needs re-approval
- **DELETED** — soft deleted, terminal state

### Post-login routing (mobile app)
After Keycloak login, the app calls `GET /api/v1/members/me`:
- `PENDING` → redirect to "승인 대기 중" screen
- `INACTIVE` → redirect to "계정 비활성" screen
- `ACTIVE` → enter app normally
- member not found → error / contact admin screen

---

## Backend Prerequisites (must complete before dashboard sessions)

These are backend changes required to support the dashboard design. They are not dashboard work — they belong to the backend repo.

| # | Change | Details |
|---|--------|---------|
| 1 | Restore `PENDING` in `MemberStatus` | Remove `@Deprecated` annotation from `PENDING` in `MemberStatus.kt` |
| 2 | New Flyway migration | Change `Member.memberStatus` column default to `PENDING` |
| 3 | Member registration default | Ensure `POST /api/v1/members` sets `memberStatus = PENDING` |
| 4 | Add `GET /api/v1/members/me` | Returns own member profile using JWT `sub` claim |
| 5 | 403 guard for PENDING/INACTIVE | All non-`/members/me` protected endpoints return `403` if member status is not `ACTIVE` |
| 6 | Update dashboard `MemberStatus` type | Add `PENDING` + Korean label `'대기중'` to `member.model.ts` |

---

## Architecture

### Stack
- Angular 21, standalone components, signals
- PrimeNG 19 + Aura theme
- Tailwind CSS
- Keycloak PKCE auth (already wired — do not change)
- ApiService pattern: all HTTP calls unwrap `ApiResponse<T>` centrally

### Existing foundation (do not rebuild)
- `AuthService` — Keycloak init, token refresh, `isAdmin()`, `logout()`
- `jwtInterceptor` — attaches Bearer token to every request
- `authGuard` / `adminGuard` — route protection
- `ApiService` — `get/post/patch/delete` with `ApiResponse<T>` unwrap
- `MemberService` — full CRUD + paginated list
- Member list, detail, edit components — functionally complete

---

## Session Plan

### Session 1 — Layout Shell

**Goal:** Sidebar navigation wrapping all authenticated routes.

**Changes:**
- `app.component.html` → bare `<router-outlet>` only
- New `shell/shell.component` — contains:
  - Top bar: branding ("한마음 D+N") + username + logout button (move from app.component)
  - Left sidebar: nav links — 회원 (`/members`), 부서 (`/ministry`), 출석 (`/attendance`)
  - `<router-outlet>` for main content
- `app.routes.ts` — authenticated routes load inside shell; Keycloak redirect bypasses shell
- Sidebar active link highlighted via Angular `RouterLinkActive`
- PrimeNG `p-panelMenu` or plain Tailwind nav links (prefer Tailwind — simpler, no extra component)

**Layout structure:**
```
┌─────────────────────────────────────────────┐
│  한마음 D+N             [user]  [logout]     │
├──────────┬──────────────────────────────────┤
│  회원     │                                  │
│  부서     │   <router-outlet>                │
│  출석     │                                  │
└──────────┴──────────────────────────────────┘
```

---

### Session 2 — Member Feature Completion (F1)

**Goal:** Approval workflow on top of existing member CRUD.

**Backend prerequisite:** Items 1–6 from prerequisites table above must be done first.

**Dashboard changes:**

**Member list (`members-list.component`):**
- Add status filter tabs: 전체 / 대기중 (PENDING) / 활성 (ACTIVE) / 비활성 (INACTIVE)
- Pass selected status as query param to `MemberService.getMembers()`
- PENDING rows: show green **승인** button
- INACTIVE rows: show blue **재활성** button
- Both actions: PrimeNG `p-confirmDialog` before firing `PATCH {memberStatus: ACTIVE}`

**Member model (`member.model.ts`):**
- Add `PENDING` to `MemberStatus` type
- Add `PENDING_LABELS: { PENDING: '대기중' }`
- Add `'warn'` severity mapping for `PENDING` status tag

**MemberService:**
- Add `approveMember(publicId)` convenience method → `PATCH {memberStatus: 'ACTIVE'}`
- (Or reuse existing `updateMember` — either is acceptable)

**No changes** to member-detail or member-edit components.

---

### Session 3 — Ministry Feature (F2)

**Goal:** Admin can create/manage ministries and view registrations.

**New files:**
```
features/ministry/
  ministry.model.ts          — MinistrySummary, Ministry, LeaderDto, RegistrationDto, request types
  ministry.service.ts        — API calls for all ministry endpoints
  ministry.routes.ts         — lazy-loaded routes
  ministry-list/             — list component
  ministry-edit/             — create + edit form component
  ministry-detail/           — detail + registrations component
```

**Backend endpoints used:**
| Endpoint | Used by |
|----------|---------|
| `GET /api/v1/ministries` | ministry-list |
| `POST /api/v1/ministries` | ministry-edit (create) |
| `GET /api/v1/ministries/:id` | ministry-detail |
| `PATCH /api/v1/ministries/:id` | ministry-edit (update) |
| `DELETE /api/v1/ministries/:id` | ministry-list (deactivate action) |
| `GET /api/v1/ministries/:id/registrations` | ministry-detail |
| `DELETE /api/v1/ministries/:id/registrations/:regId` | ministry-detail (remove registration) |

**Routes:** `/ministry`, `/ministry/new`, `/ministry/:id`, `/ministry/:id/edit`

**ministry-list:** Table with columns: name, short description, leader, active status. Filter tabs: 전체 / 활성 / 비활성. Actions: edit, deactivate (confirmation-gated).

**ministry-edit:** Form fields: name (required), shortDescription (required), longDescription (textarea), imageUrl, leaderPublicId (text input — member publicId; image upload deferred to v0.2.0), isActive (edit only).

**ministry-detail:** Ministry info card + registrations table. Columns: member name, period, note. Action: remove registration (confirmation-gated).

---

### Session 4 — Attendance Feature (F3)

**Goal:** Admin can manage attendance definitions and review check-in logs.

**New files:**
```
features/attendance/
  attendance.model.ts        — DefinitionDto, AttendanceLogDto, request types
  attendance.service.ts      — API calls
  attendance.routes.ts       — lazy-loaded routes
  attendance-definitions/    — definitions list + inline create/edit
  attendance-logs/           — log view per definition
```

**Backend endpoints used:**
| Endpoint | Used by |
|----------|---------|
| `GET /api/v1/attendance/definitions` | attendance-definitions |
| `POST /api/v1/attendance/definitions` | attendance-definitions (create) |
| `PATCH /api/v1/attendance/definitions/:id` | attendance-definitions (edit) |
| `DELETE /api/v1/attendance/definitions/:id` | attendance-definitions (deactivate) |
| `GET /api/v1/attendance/logs?definitionId=:id` | attendance-logs |

**Routes:** `/attendance` → definitions list, `/attendance/:id/logs` → log view

**attendance-definitions:** Table of definitions (name, active status). Inline create form at top. Edit in place or via dialog. Deactivate action (confirmation-gated).

**attendance-logs:** Date range filter (PrimeNG DatePicker). Table: member name, check-in time. Read-only. Stats (total count, unique members) deferred to v0.5.0.

---

## What Is Explicitly Out of Scope

| Item | Deferred to |
|------|-------------|
| Image upload (member photo, ministry image) | v0.2.0 |
| Attendance statistics / analytics | v0.5.0 |
| Multi-role admin (ministry leader role) | Post v0.1.0 |
| Dashboard for carpool, dishwashing, groups | v0.3.0 / v0.4.0 |
| Mobile app screens | Separate session series |

---

## Definition of Done (per session)

- All components compile with no TypeScript errors
- All routes load and are protected by `adminGuard`
- All API calls use `ApiService` (no raw `HttpClient`)
- All list views are paginated where the backend supports it
- All destructive actions (approve, deactivate, delete) are confirmation-gated
- Korean labels used throughout (matching existing member components)
- `make build-backend` run after any backend change; `ng serve` for dashboard dev
