# hanmaum D+N

Full-stack church management app (Korean community).

## Stack
- Backend: Kotlin + Spring Boot 4 + JPA + PostgreSQL (port 5433)
- Frontend: Angular 21 + PrimeNG + Tailwind CSS
- Auth: Keycloak (port 8091) — OAuth2 JWT / PKCE
- Infra: Docker Compose (`infrastructure/docker-compose.yml`)

## Key Architectural Rules
- All entities extend `BaseEntity` (id, publicId, createdAt, updatedAt, deletedAt)
- REST APIs use `publicId` (UUID) — never expose internal `id`
- Soft delete only — never hard-delete; set `deletedAt` + `memberStatus=DELETED`
- Mappers: Kotlin extension functions in `api/SomeMappers.kt`
- All responses: `ApiResponse<T>(success, message, data)`
- Flyway: new file per migration — `V{timestamp}__{description}.sql` — never modify existing ones

## Session Start
Always run `/onboard` as the very first action — no exceptions.

## Communication
✅ Done / ⚠️ Found / 🔧 Fixed / 📋 Next / 🚫 Blocked
Be direct. File:line references. No filler.
