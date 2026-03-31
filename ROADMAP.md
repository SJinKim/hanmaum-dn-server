# hanmaum D+N — Product Roadmap

> Architecture rules: CLAUDE.md · Feature specs & current status: MVP.md

---

## Ecosystem Overview

Three separate apps that share a single backend:

```
Private_Projects/
├── dn-app/               ← Backend (single source of truth)
├── dn-app-dashboard/     ← Admin web app (Angular 21) — does not exist yet
└── HanmaumDnApp/         ← Member mobile app (Kotlin Multiplatform)
```

---

## Current Codebase Audit

### Backend — systemic issues to fix feature-by-feature (not a separate sprint)

| Issue | Impact | Fix |
|-------|--------|-----|
| No `@PreAuthorize` on any endpoint | All APIs are effectively public | Add RBAC per MVP.md role table |
| Missing `/api/v1/` path prefix | Versioning not enforced | Add prefix to all controllers |
| Internal `id` exposed in `/members/{id}` path | Architecture violation | Migrate to `/{publicId}` |
| Many controllers return raw entities or untyped DTOs | Inconsistent clients | Standardize to `ApiResponse<T>` |
| No `@Valid` on request bodies | No input validation | Add to every `@RequestBody` |
| Zero test coverage | Unverifiable correctness | Add integration tests per feature |
| German/Korean mixed comments | Code quality | Clean up during feature completion |

These must be corrected **as part of completing each feature**, not as a separate cleanup sprint.

### Backend — what exists

| Feature | Domain | Repo | Service | Controller | Mapper | Migration | State |
|---------|--------|------|---------|------------|--------|-----------|-------|
| Member | ✅ | ✅ | ✅ | ✅ (v1+v2) | ✅ | ✅ | 🔄 needs fixes |
| Ministry | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | 🔲 domain only |
| MinistryRegistration | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | 🔲 domain only |
| Attendance | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | 🔄 needs fixes |
| Announcements | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 🔄 needs RBAC |
| Carpool | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | 🔄 out of scope v0.1 |
| Dishwashing | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | 🔄 out of scope v0.1 |
| Groups / 목장 | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | 🔄 out of scope v0.1 |
| Statistics | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | 🔄 out of scope v0.1 |

### Dashboard — what exists

| Area | State |
|------|-------|
| Project scaffold (Angular 21, routing, Tailwind) | ✅ |
| Member models (TypeScript interfaces) | ✅ |
| Dashboard overview component | 🔄 stub |
| Settings component | 🔄 stub |
| Keycloak auth integration | ❌ |
| Member list / detail / edit | ❌ |
| Ministry management | ❌ |
| Attendance views | ❌ |

### Mobile App (HanmaumDnApp)
Folder exists. Not assessed — not in current workspace mount. Assumed early-stage.

---

## What Is Genuinely Missing (not in any plan yet)

| Gap | Priority | Affects |
|-----|----------|---------|
| Image upload/storage (member photos, ministry images) | High | Backend, Dashboard, App |
| Keycloak PKCE auth flow in Dashboard | High | Dashboard v0.1.0 |
| Keycloak PKCE auth flow in App | High | App v0.1.0 |
| Pagination on all list endpoints | High | Backend v0.1.0 |
| API path prefix `/api/v1/` enforcement | High | Backend v0.1.0 |
| Input validation (`@Valid`) everywhere | High | Backend v0.1.0 |
| RBAC (`@PreAuthorize`) on all endpoints | High | Backend v0.1.0 |
| Integration tests | Medium | Backend |
| CI/CD pipeline (GitHub Actions) | Medium | All |
| Audit log (who changed what, when) | Medium | Backend v0.3+ |
| Push notifications (FCM) | Medium | App v0.2.0 |
| Error monitoring (Sentry or similar) | Medium | All — v1.0.0 |
| Rate limiting | Low | Backend |
| Multi-language support (Korean ↔ German) | Low | Dashboard, App v1.0.0 |
| App store deployment (iOS + Android) | Low | App v1.0.0 |

---

## Versioned Release Plan

### v0.1.0 — Core Foundation _(current sprint)_
**Goal:** Three features working end-to-end. Admins can manage the church, members can check in and register for ministries.

| # | Feature | Backend | Dashboard | App |
|---|---------|---------|-----------|-----|
| 1 | Member Management | 🔄 | 🔄 | 🔲 |
| 2 | Ministry + Registration | 🔄 | 🔲 | 🔲 |
| 3 | Attendance Tracking | 🔄 | 🔲 | 🔲 |

**Also required for release:**
- Auth flows (Keycloak PKCE) working in Dashboard and App
- All endpoints secured with RBAC
- All paths use `publicId`, all responses use `ApiResponse<T>`
- Pagination on all list endpoints
- Image upload for member profile photo and ministry image

**Release gate:** All 3 features ✅ across all 3 layers. Auth works end-to-end.

---

### v0.2.0 — Communication Layer
**Goal:** Admins can push announcements to members. Members get notified.

| Feature | Backend | Dashboard | App |
|---------|---------|-----------|-----|
| Announcements (fix + complete) | 🔄 exists | 🔲 | 🔲 |
| Push notifications (FCM) | 🔲 | 🔲 | 🔲 |
| Email notifications (registration confirm, etc.) | 🔲 | n/a | n/a |

**New backend work:**
- FCM integration (Firebase Cloud Messaging)
- Device token registration endpoint
- Notification service (triggered on key events: new announcement, attendance window opens)
- Email via SMTP (Spring Mail) for registration confirmation

---

### v0.3.0 — Logistics
**Goal:** Carpool and dishwashing scheduling managed in app and dashboard.

| Feature | Backend | Dashboard | App |
|---------|---------|-----------|-----|
| Carpool coordination | 🔄 exists | 🔲 | 🔲 |
| Dishwashing schedule | 🔄 exists | 🔲 | 🔲 |

**New backend work:**
- Complete carpool: RBAC, `ApiResponse<T>`, `publicId` paths, pagination
- Complete dishwashing: same fixes + rotation algorithm
- Recurring schedule generation logic

---

### v0.4.0 — Community / 목장
**Goal:** Small groups can track their own meetings and attendance.

| Feature | Backend | Dashboard | App |
|---------|---------|-----------|-----|
| Church Groups / 목장 | 🔄 exists | 🔲 | 🔲 |
| Group meeting scheduling | 🔄 exists | 🔲 | 🔲 |
| Group-level attendance | 🔲 | 🔲 | 🔲 |
| Prayer requests | 🔲 | 🔲 | 🔲 |

**New backend work:**
- Complete groups: RBAC, response standardization, pagination
- Group leader role (separate from ADMIN — can manage own group only)
- Prayer request feature (create, view, mark answered)

---

### v0.5.0 — Analytics & Admin Intelligence
**Goal:** Admins have full visibility into church health metrics.

| Feature | Backend | Dashboard | App |
|---------|---------|-----------|-----|
| Attendance statistics & trends | 🔄 exists | 🔲 | n/a |
| Ministry participation report | 🔲 | 🔲 | n/a |
| Member growth tracking | 🔲 | 🔲 | n/a |
| Audit log (who changed what) | 🔲 | 🔲 | n/a |
| Export to CSV/Excel | 🔲 | 🔲 | n/a |

**New backend work:**
- Statistics service (refine existing, add ministry + member growth queries)
- Audit log entity + interceptor (log all write operations with actor + timestamp)
- CSV/Excel export endpoint (per report type)

---

### v1.0.0 — Production Release
**Goal:** Fully polished, production-grade, deployable to real users.

| Area | Work Required |
|------|---------------|
| Multi-language (Korean ↔ German) | Dashboard and App UI — i18n setup, translation files |
| Performance | DB indexes audit, N+1 query elimination, caching (Redis) for static lists |
| Error monitoring | Sentry integration in all three apps |
| CI/CD | GitHub Actions: build → test → lint → deploy per repo |
| API documentation | OpenAPI/Swagger complete with examples per endpoint |
| Rate limiting | Spring Boot rate limiter on public endpoints |
| Mobile store | iOS App Store + Google Play deployment pipeline |
| Infrastructure | Production Docker Compose / Kubernetes manifests, secret management |

---

## Build Order (global, cross-version)

```
v0.1.0
  └── Backend fixes (RBAC, paths, response format) → run in parallel with features
  └── F1: Member Management    [Backend → Dashboard → App]
  └── F2: Ministry             [Backend → Dashboard → App]
  └── F3: Attendance           [Backend → Dashboard → App]
  └── Cross-cutting: auth flows, image upload, pagination

v0.2.0
  └── Announcements complete   [Backend fixes → Dashboard → App]
  └── FCM push notifications   [Backend → App]
  └── Email                    [Backend only]

v0.3.0
  └── Carpool complete         [Backend fixes → Dashboard → App]
  └── Dishwashing complete     [Backend fixes → Dashboard → App]

v0.4.0
  └── Groups / 목장 complete   [Backend fixes → Dashboard → App]
  └── Prayer requests          [Backend → App]

v0.5.0
  └── Statistics & analytics   [Backend → Dashboard]
  └── Audit log                [Backend → Dashboard]
  └── CSV/Excel export         [Backend → Dashboard]

v1.0.0
  └── i18n, performance, monitoring, CI/CD, store deployment
```

---

## Session Assignment Guide

Each autonomous dev session = one feature + one layer. Suggested order:

| Session | Feature | Layer | Est. complexity |
|---------|---------|-------|-----------------|
| 1 | F1: Member Management | Backend (fix + complete) | High — refactor existing |
| 2 | F1: Member Management | Dashboard | Medium |
| 3 | F1: Member Management | App | Medium |
| 4 | F2: Ministry + Registration | Backend | High — build from scratch |
| 5 | F2: Ministry + Registration | Dashboard | Medium |
| 6 | F2: Ministry + Registration | App | Medium |
| 7 | F3: Attendance | Backend (fix + complete) | High — time-window logic |
| 8 | F3: Attendance | Dashboard | Medium |
| 9 | F3: Attendance | App | Medium |
| 10 | Cross-cutting: auth + image upload | All layers | High |
| 11 | v0.2.0: Announcements | Backend fix + Dashboard + App | Medium |
| 12 | v0.2.0: Push notifications | Backend + App | High |
