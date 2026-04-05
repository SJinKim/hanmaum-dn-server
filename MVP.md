# MVP — hanmaum D+N v0.1.0

> Architecture rules, stack, branching: **CLAUDE.md** — not repeated here.
> Out of scope: carpool, dishwashing, groups, push notifications, multi-language.

---

## RBAC
| Role | Scope |
|------|-------|
| `ADMIN` | Full CRUD, management, reports |
| `MEMBER` | Read own data, check in, register for ministry |

---

## F1: Member Management
_Foundation — all other features depend on this._

### Domain: `Member`
| Field | Type | Constraint |
|-------|------|------------|
| name | String | NotBlank, max 100 |
| email | String | Unique, valid format |
| phone | String? | nullable |
| dateOfBirth | LocalDate? | nullable |
| gender | Gender | MALE \| FEMALE |
| baptism | Baptism | BAPTIZED \| CATECHUMEN \| NONE |
| memberStatus | MemberStatus | ACTIVE \| INACTIVE \| DELETED |
| role | Role | ADMIN \| MEMBER |
| profileImageUrl | String? | nullable |
| keycloakId | String | Unique, not null |

**Status transitions:** `ACTIVE ↔ INACTIVE → DELETED` (DELETED is terminal)

### API: `/api/v1/members`
| Method | Path | Role | Notes |
|--------|------|------|-------|
| POST | / | ADMIN | Create |
| GET | / | ADMIN | List; default page=0 size=20 sort=name asc |
| GET | /{publicId} | ADMIN | Detail |
| GET | /me | MEMBER | Own profile |
| PATCH | /{publicId} | ADMIN | Partial update; validate status transition |
| DELETE | /{publicId} | ADMIN | Soft delete → DELETED |

### DoD
- **Backend:** Entity, repo, service, controller, mapper (`MemberMappers.kt`), Flyway migration, `@Valid` on all inputs, 404 on not found, status-transition guard
- **Dashboard:** List with search (name/email) + pagination; detail view; edit form; status badge
- **App:** Own profile view; edit own phone + photo

---

## F2: Ministry + Registration
_Depends on: F1_

### Domain: `Ministry`
| Field | Type | Constraint |
|-------|------|------------|
| name | String | NotBlank, max 100 |
| shortDescription | String | NotBlank, max 200 |
| longDescription | String? | TEXT, nullable |
| imageUrl | String? | nullable |
| leader | Member? | FK, nullable |
| isActive | Boolean | default true |

### Domain: `MinistryRegistration`
| Field | Type | Constraint |
|-------|------|------------|
| ministry | Ministry | FK, NotNull |
| member | Member | FK, NotNull |
| period | String | NotBlank, format `"YYYY"` e.g. `"2026"` |
| note | String? | max 500, nullable |

**Unique constraint:** `(ministry_id, member_id, period)` — enforced at DB + service layer

### API: `/api/v1/ministries`
| Method | Path | Role | Notes |
|--------|------|------|-------|
| POST | / | ADMIN | Create ministry |
| GET | / | MEMBER | List; `?active=true\|false` |
| GET | /{publicId} | MEMBER | Detail with leader info |
| PATCH | /{publicId} | ADMIN | Update fields |
| DELETE | /{publicId} | ADMIN | Deactivate (isActive=false, NOT hard delete) |
| POST | /{publicId}/registrations | MEMBER | Register self; 409 on duplicate |
| GET | /{publicId}/registrations | ADMIN | List registrations; `?period=2026` |
| DELETE | /{publicId}/registrations/{regPublicId} | MEMBER | Withdraw own only; 403 on other's |

### DoD
- **Backend:** Both entities, repos, service, controller, mapper, migration, 409 on duplicate, 403 on unauthorized withdraw, tests
- **Dashboard:** Ministry list + create/edit + deactivate; registration list per ministry per period
- **App:** Browse active ministries; detail (photo, leader, short desc); register/withdraw current period

---

## F3: Attendance Tracking
_Depends on: F1_

### Domain: `AttendanceDefinition`
| Field | Type | Constraint |
|-------|------|------------|
| title | String | NotBlank, max 100 |
| dayOfWeek | DayOfWeek | MON–SUN |
| windowStart | LocalTime | NotNull |
| windowEnd | LocalTime | NotNull, must be after windowStart |
| isActive | Boolean | default true |

### Domain: `AttendanceLog`
| Field | Type | Constraint |
|-------|------|------------|
| definition | AttendanceDefinition | FK, NotNull |
| member | Member | FK, NotNull |
| attendanceDate | LocalDate | NotNull |
| status | AttendanceStatus | PRESENT \| LATE \| ONLINE |

**Unique constraint:** `(member_id, definition_id, attendance_date)`
**Check-in guard:** Server validates current time is within `windowStart`–`windowEnd` on the correct `dayOfWeek`. Never trust client-provided time.

### API: `/api/v1/attendance`
| Method | Path | Role | Notes |
|--------|------|------|-------|
| POST | /definitions | ADMIN | Create |
| GET | /definitions | MEMBER | List; `?active=true` |
| PATCH | /definitions/{publicId} | ADMIN | Update |
| DELETE | /definitions/{publicId} | ADMIN | Deactivate |
| POST | /check-in | MEMBER | Check in; 400 outside window; 409 duplicate |
| GET | /logs | ADMIN | List; `?memberId ?from ?to ?definitionId`; page=0 size=50 |
| GET | /logs/me | MEMBER | Own history |
| GET | /stats | ADMIN | Stats per member + date range |

### DoD
- **Backend:** Both entities, repos, service (time-window + duplicate guard), controller, mapper, migration, 409 on duplicate, 400 outside-window, tests
- **Dashboard:** Log table with filters (member / date range / definition); stats per member
- **App:** Check-in button active only during valid window; own attendance history

---

## Build Order & Status

| # | Feature | Backend | Dashboard | App |
|---|---------|---------|-----------|-----|
| 1 | Member Management | ✅ | ✅ | ✅ |
| 2 | Ministry + Registration | ✅ | ✅ | ✅ |
| 3 | Attendance Tracking | ✅ | ✅ | 🔲 |

🔲 not started · 🔄 in progress · ✅ done

**Rule:** Backend must be ✅ before starting Dashboard or App for that feature.
**Session target:** Complete at least one full layer per session.

### Seed Data
Before end-to-end testing the dashboard against a real backend, add database seed/mock data.
**When:** After F1 + F2 dashboard are both ✅ — seed enough data to exercise all views.

Minimum seed set:
- 15–20 members (mix of ACTIVE, INACTIVE, PENDING, at least 1 ADMIN)
- 4–5 ministries (mix of active/inactive, some with registrations)
- Member registrations across 2 periods (e.g. "2025", "2026")
- Attendance definitions + a few logs

Add as a Flyway migration: `V{timestamp}__seed_dev_data.sql` (dev profile only — guard with a `spring.profiles.active=dev` check or a separate `application-dev.yml` datasource override so it never runs in prod).

---

## Release Gate (v0.1.0-rc.1)
All 3 features ✅ across all 3 layers. Keycloak PKCE auth flows working end-to-end.
