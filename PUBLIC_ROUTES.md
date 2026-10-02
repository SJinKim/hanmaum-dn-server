# Public routes

Which backend paths answer without a token (#237, parent #234). The anonymous API routes are
listed once in `common/config/PublicApiRoutes.kt`: `SecurityConfig.filterChain` permits exactly
those, and `OpenApiConfig` marks exactly those with `security: []` in the spec (#239).
`PublicRouteSecurityTest` checks every row below. Staging and prod
run the same image and the same rules. Only Caddy sits in front of them.

## Matrix

| Path | Anonymous | Protection |
|---|---|---|
| `GET /actuator/health` | yes | — |
| `GET /actuator/prometheus` | yes in the backend, **403 at Caddy** | reachable only from the private observability network |
| `GET /actuator/*` (everything else) | no: 401 in the backend, 403 at Caddy | `management.endpoints.web.exposure` is `health,prometheus` |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | local dev only | springdoc is off in the `prod` profile, which ST and prod both run: 404 |
| `GET /api/v1/newcomer-forms/{token}` | yes | the token is the capability |
| `POST /api/v1/newcomer-forms/{token}/submissions` | yes | token, 10 per minute per token and address |
| `POST /api/v1/members/register` | yes | 10 per 10 minutes per address, size limits on every field |
| everything else | no | bearer token (`KEYCLOAK_ENVIRONMENTS.md`), roles via `@PreAuthorize` |

The neighbours of the anonymous rows stay protected, for example `GET /api/v1/newcomer-forms`
and `GET /api/v1/members/register`.

Announcements and albums need a token too, list and detail alike. Only the dashboard and the app
read them, and both call them after login. CORS does not keep other callers out (it binds only
browsers), and an app secret could be pulled out of the APK. The token with `aud=hanmaum-dn-api` is
the boundary that actually limits access to our clients.

## Self-registration

- `permitAll`, not `web.ignoring()`. The request goes through the filter chain, so CORS and the
  security headers apply. A request that sends an invalid bearer token gets 401; the apps send
  none on this call.
- `MemberService.registerMember` refuses an address after 10 calls in 10 minutes with 429,
  before it touches the database or Keycloak. The limit is
  `app.registration.rate-limit-per-ten-minutes`.

## Rate limits and the client address

Both limits use `SlidingWindowRateLimiter`: in memory, per backend instance, at most 10,000
addresses. When it is full and nothing has expired, new addresses get 429 until old entries
expire. A restart clears it.

The key is the client address. Caddy is the only ingress and sets `X-Forwarded-For` itself,
overwriting what the client sent. `server.forward-headers-strategy: native` lets Tomcat take the
address from that header only when the request comes from an internal proxy address (the Docker
network). Without it, every caller would share Caddy's address and one limit.

## Swagger UI with a token (local dev)

Swagger UI runs only with the dev profile. Every operation in the spec carries the `bearerAuth`
requirement (HTTP bearer, JWT) except the three anonymous rows above, which have `security: []`.

1. Open `http://localhost:8080/swagger-ui.html`.
2. Get an access token for the local realm `hanmaum`, for example from the dashboard's network tab.
   It needs `aud=hanmaum-dn-api` (see `KEYCLOAK_ENVIRONMENTS.md`).
3. Click "Authorize", paste the token without the `Bearer ` prefix.

No OAuth flow is declared, so Swagger UI never asks for a password or client secret, and
`persistAuthorization` is off, so the token is gone after a reload. Staging and prod have no
Swagger UI by decision (#237), so a staging or prod token is never pasted into it.

## Checking a deployment

Run against the staging API first, then prod. No token needed.

```bash
API=https://<api domain>
curl -s -o /dev/null -w '%{http_code}\n' $API/actuator/health        # 200
curl -s -o /dev/null -w '%{http_code}\n' $API/actuator/prometheus    # 403 (Caddy)
curl -s -o /dev/null -w '%{http_code}\n' $API/actuator/info          # 403 (Caddy)
curl -s -o /dev/null -w '%{http_code}\n' $API/swagger-ui.html        # 404
curl -s -o /dev/null -w '%{http_code}\n' $API/api/v1/announcements         # 401
curl -s -o /dev/null -w '%{http_code}\n' $API/api/v1/announcements/admin   # 401
curl -s -i -X OPTIONS $API/api/v1/members/register \
  -H 'Origin: https://evil.example' -H 'Access-Control-Request-Method: POST' | head -1   # 403
```
