---
name: api-contracts
description: Use when adding, changing, or removing REST endpoints, DTOs, or anything affecting the API surface consumed by web-app or mobile-app
---

# API contract management

## Source of truth
- OpenAPI spec lives in `hanmaum-dn-ops/api/openapi.yaml`.
- It is the contract between server, web-app, and mobile-app.
- Generated from Spring controllers via springdoc-openapi.

## Workflow for adding or changing an endpoint

1. **Design first.** Sketch the signature in the HDN ticket before coding:
   method, path, request DTO, response DTO, error responses, auth requirements.
2. **Check for breaking changes.** Breaking means:
   - A field removed or renamed
   - A field's type changed
   - A required field added to a request
   - An endpoint's path or method changed
   - An error code's meaning changed
3. **Implement** controller, service, DTO, and tests in `hanmaum-dn-server`.
4. **Regenerate the spec locally:** `./gradlew generateOpenApiDocs`.
5. **Sync manually to ops:** copy `build/openapi/openapi.yaml` into
   `hanmaum-dn-ops/api/openapi.yaml`, commit in the ops repo with the same
   HDN ticket ID.
6. **Notify the web/mobile devs** on the HDN ticket so they pull the latest
   spec when they pick up client-side work.
7. All three app PRs reference the same HDN ticket and merge in coordination.

*Future automation:* once we ship past MVP and have real consumers, replace
steps 4–6 with a CI workflow that opens cross-repo sync PRs automatically.

## DTO conventions
- Request DTOs: `<Feature>Request` (e.g., `CreateMemberRequest`).
- Response DTOs: `<Feature>Response` (e.g., `MemberResponse`).
- Never expose JPA entities directly. Map to a DTO in the controller layer.
- DTOs are Kotlin data classes with `val` properties.
- Nullable only when genuinely optional. Default to non-null.

## Versioning
- Unversioned during pre-1.0.
- After 1.0: breaking changes go under `/api/v2/…`. Never break `/api/v1/…`.

## Documentation
- Every controller method: KDoc block → OpenAPI `summary` and `description`.
- Every DTO field: KDoc → OpenAPI field description.
- `@Operation` and `@ApiResponse` annotations only when KDoc is insufficient
  (e.g., multiple distinct 4xx responses).

## Don't do
- Don't edit `openapi.yaml` by hand. It's generated.
- Don't return raw `Map` or `Any`. Define a DTO.
- Don't change a DTO field without checking web and mobile consumers first.
