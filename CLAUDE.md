# hanmaum D+N

Full-stack church management app (Korean community).

## Project Structure
Three separate repos, all siblings inside `Private_Projects/`:
| Layer | Folder | Stack |
|-------|--------|-------|
| Backend | `Private_Projects/dn-app/` | Kotlin + Spring Boot 4 + JPA |
| Dashboard | `Private_Projects/dn-app-dashboard/` | Angular 21 + PrimeNG + Tailwind |
| Mobile App | `Private_Projects/HanmaumDnApp/` | Kotlin Multiplatform |

**Workspace must be set to `Private_Projects/`** so all three are accessible.
The dashboard folder does not exist yet — create it with Angular CLI when first needed.

## Stack
- Backend: Kotlin + Spring Boot 4 + JPA + PostgreSQL (port 5433)
- Dashboard: Angular 21 + PrimeNG + Tailwind CSS
- Mobile App: Kotlin Multiplatform (KMP)
- Auth: Keycloak (port 8091) — OAuth2 JWT / PKCE
- Infra: Docker Compose (`dn-app/infrastructure/docker-compose.yml`)

## Dashboard Design
- All dashboard UI work must follow `dn-app-dashboard/design-specs/DESIGN.md` — read it before building any component or page
- Do not deviate from the design specs without explicit user approval

## Key Architectural Rules
- All entities extend `BaseEntity` (id, publicId, createdAt, updatedAt, deletedAt)
- REST APIs use `publicId` (UUID) — never expose internal `id`
- Soft delete only — never hard-delete; set `deletedAt` + `memberStatus=DELETED`
- Mappers: Kotlin extension functions in `api/SomeMappers.kt`
- All responses: `ApiResponse<T>(success, message, data)`
- Flyway: new file per migration — `V{timestamp}__{description}.sql` — never modify existing ones

## Session Rules
- Always run `/onboard` as the very first action — no exceptions
- One feature per session — pick one feature, complete all its layers, do not spread across features
- If context/tokens are running low: stop cleanly, write dev-log.md, commit what is done — **never purchase or spend money to get more tokens; just stop and wait for the next session**

## Communication
✅ Done / ⚠️ Found / 🔧 Fixed / 📋 Next / 🚫 Blocked
Be direct. File:line references. No filler.

## Branching
- Feature branches: always from `dev`, PR back to `dev`
- Never branch from `main` except hotfix
- Run `/release` for release workflow