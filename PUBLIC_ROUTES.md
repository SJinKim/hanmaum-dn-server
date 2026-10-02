# Public routes

Which backend paths answer without a token (#237, parent #234). The source is
`SecurityConfig.filterChain`; `PublicRouteSecurityTest` checks every row below. Staging and prod
run the same image and the same rules. Only Caddy sits in front of them.

## Matrix

| Path | Anonymous | Protection |
|---|---|---|
| `GET /actuator/health` | yes | — |
| `GET /actuator/prometheus` | yes in the backend, **403 at Caddy** | reachable only from the private observability network |
| `GET /actuator/*` (everything else) | no: 401 in the backend, 403 at Caddy | `management.endpoints.web.exposure` is `health,prometheus` |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | yes | none yet. Whether prod keeps them public is #239 |
| `GET /api/v1/announcements` | yes | read only |
| `GET /api/v1/albums` | yes | read only |
| `GET /api/v1/newcomer-forms/{token}` | yes | the token is the capability |
| `POST /api/v1/newcomer-forms/{token}/submissions` | yes | token, 10 per minute per token and address |
| `POST /api/v1/members/register` | yes | 10 per 10 minutes per address, size limits on every field |
| everything else | no | bearer token (`KEYCLOAK_ENVIRONMENTS.md`), roles via `@PreAuthorize` |

The neighbours of the anonymous rows stay protected, for example `GET /api/v1/announcements/{id}`,
`GET /api/v1/announcements/admin`, `POST /api/v1/albums` and `GET /api/v1/newcomer-forms`.

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

## Checking a deployment

Run against the staging API first, then prod. No token needed.

```bash
API=https://<api domain>
curl -s -o /dev/null -w '%{http_code}\n' $API/actuator/health        # 200
curl -s -o /dev/null -w '%{http_code}\n' $API/actuator/prometheus    # 403 (Caddy)
curl -s -o /dev/null -w '%{http_code}\n' $API/actuator/info          # 403 (Caddy)
curl -s -o /dev/null -w '%{http_code}\n' $API/swagger-ui.html        # see #239
curl -s -o /dev/null -w '%{http_code}\n' $API/api/v1/announcements/admin   # 401
curl -s -i -X OPTIONS $API/api/v1/members/register \
  -H 'Origin: https://evil.example' -H 'Access-Control-Request-Method: POST' | head -1   # 403
```
