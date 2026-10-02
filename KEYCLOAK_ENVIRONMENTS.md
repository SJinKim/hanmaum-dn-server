# Keycloak environments: realms, clients and audience

Target state for the Keycloak side of each environment (#235, parent #234). This file
holds no secrets. Client secrets live only in the server's `.env` files and in Keycloak.

## Matrix

Staging and prod share one Keycloak instance at `auth.graceops.de`. Only the realm
separates them. This is intended, not a stopgap (#253). The audience is the same everywhere.

| | Local dev | Staging | Prod |
|---|---|---|---|
| Realm | `hanmaum` | `hanmaum-dn-st` | `hanmaum-dn-prod` |
| Public issuer (`iss`) | `http://localhost:8091/realms/hanmaum` | `https://auth.graceops.de/realms/hanmaum-dn-st` | `https://auth.graceops.de/realms/hanmaum-dn-prod` |
| JWKS (backend, internal) | `http://localhost:8091/realms/hanmaum/...` | `http://hanmaumApp-keycloak:8090/realms/hanmaum-dn-st/protocol/openid-connect/certs` | `http://hanmaumApp-keycloak:8090/realms/hanmaum-dn-prod/protocol/openid-connect/certs` |
| API audience (`aud`) | `hanmaum-dn-api` | `hanmaum-dn-api` | `hanmaum-dn-api` |
| Login theme, locales | `hanmaum`, `ko` (default) + `en` | `hanmaum`, `ko` (default) + `en` | `hanmaum`, `ko` (default) + `en` |
| Brute-force lockout | after 20 failures, 10 s up to 60 s | after 5 failures, 1 min up to 15 min | after 5 failures, 1 min up to 15 min |
| How the realm is set up | `--import-realm` from `infrastructure/docker/keycloak/export/` | `scripts/keycloak/configure-realm.sh` | `scripts/keycloak/configure-realm.sh` |

The backend variables per environment are `KEYCLOAK_REALM` and
`APP_SECURITY_KEYCLOAK_PUBLIC_ISSUER`. The issuer must equal the `iss` claim exactly.
Checking the audience on the server side is #236. Until #236 lands, the `aud` claim is
emitted but not yet enforced.

### Clients

| Client | Type | Flows | Redirect URIs | Web origins | Default scopes include |
|---|---|---|---|---|---|
| `hanmaum-dashboard` | public | authorization code + PKCE S256 | `<dashboard origin>/*` per origin | the dashboard origins, never `*` | `hanmaum-dn-api-audience` |
| `hanmaum-mobile` | public | password grant (the app's own login form); authorization code + PKCE S256 allowed | `com.hanmaum.dn.mobile:/oauth2redirect` | none (native app) | `hanmaum-dn-api-audience` |
| `dn-backend-admin` | confidential | client credentials only | none | none | — |

The service account `service-account-dn-backend-admin` holds only
`realm-management/manage-users`. The backend creates users, sends the verification
mail (`MemberService`) and deletes users (`MemberPurgeService`). It needs nothing more,
so it does not get `realm-admin`.

The client scope `hanmaum-dn-api-audience` has one `oidc-audience-mapper` with
`included.custom.audience=hanmaum-dn-api`. It is added to the access token and to
introspection, and not to the ID token.

### Dashboard origins

| Environment | Origins |
|---|---|
| Local dev | `http://localhost:4200` (in the export) |
| Staging | `https://dn-admin-dashboard.st.graceops.de` |
| Prod | none yet: run with `DASHBOARD_URLS=` (empty) so the dashboard client accepts no redirect |

Keycloak does not accept wildcards inside a host name. Vercel preview URLs are not
listed. Add one explicitly only when it is needed.

The script sets the dashboard client's base URL to the first origin plus `/`. The login
theme links there from its error and expired-link pages ("로그인 화면으로 이동"). With
`DASHBOARD_URLS=` empty, the base URL stays as it is.

### The shared Keycloak container

The container `hanmaumApp-keycloak` is defined only in `docker-compose.prod.yml`, compose
project `hanmaum-prod`. The staging stack has no Keycloak, so a staging deploy never
touches it.

- A change to the container itself (a mount, the image, the start command) reaches the
  host only with the prod deploy, which copies `docker-compose.prod.yml` and recreates
  what changed. Until then the host keeps the old compose file.
- Every recreate or restart takes Keycloak down for both realms at once, usually 30 to
  60 seconds. Staging and prod logins fail during that time.
- Realms, users and sessions live in Postgres (`hanmaum_keycloak_db`), so recreating the
  container loses nothing.

To apply a container change without a full prod deploy, put the current
`docker-compose.prod.yml` from `main` on the host, then recreate only Keycloak:

```bash
cd /opt/hanmaum-dn-server
grep -n "themes/hanmaum\|command:" docker-compose.prod.yml   # check it is the current file
set -a && source .env && set +a
docker compose --project-name hanmaum-prod -f docker-compose.prod.yml \
  up -d --no-deps hanmaumApp-keycloak
```

`--no-deps` leaves the backend and the database alone.

### Login theme

The browser pages (dashboard login, 비밀번호 찾기, 새 비밀번호 설정, 이메일 인증, expired link)
use the theme `hanmaum` (#243). Its design is the Figma file DN-Web. The files live in
`infrastructure/docker/keycloak/themes/hanmaum/` and are mounted read-only into
`/opt/keycloak/themes/hanmaum` by `infrastructure/docker-compose.yml` (local) and
`docker-compose.prod.yml` (the shared instance). Both deploys copy `infrastructure/` to the
host, so theme files travel with either one. The mount itself only exists once the container
was created from a compose file that has it (see the section above).

- Realm settings: `loginTheme=hanmaum`, internationalization on, locales `ko` and `en`,
  default `ko`, and `resetPasswordAllowed=true` for the 비밀번호 찾기 link. The reset mail
  needs the realm's SMTP settings. The local export has them; `configure-realm.sh` sets them elsewhere.
- The script checks that Keycloak actually offers the theme before it points a realm at it.
  If the mount is missing it reports that and changes nothing, because a realm with an
  unknown theme shows broken login pages.
- The app's own login form (password grant) does not render any Keycloak page, so the
  theme does not affect it.
- Theme files are cached by Keycloak. After changing them, restart the container:
  `docker restart hanmaumApp-keycloak`.
- Mails still use the default email theme. A branded email theme is a separate issue.

### Brute-force protection

Keycloak counts failed logins per user and locks the account for a while (#244). This
covers the dashboard login page and the app's password grant (`hanmaum-mobile`) alike.

| Setting | Local dev (export) | Staging, Prod (`configure-realm.sh`) |
|---|---|---|
| `bruteForceProtected` | `true` | `true` |
| `failureFactor` | 20 | 5 |
| `waitIncrementSeconds` | 10 | 60 |
| `maxFailureWaitSeconds` | 60 | 900 |
| `minimumQuickLoginWaitSeconds` | 10 | 60 |
| `permanentLockout`, `maxTemporaryLockouts` | `false`, 0 | `false`, 0 |

Strategy `MULTIPLE`, `quickLoginCheckMilliSeconds=1000` and `maxDeltaTimeSeconds=43200`
are the same everywhere. Local values are looser so that mistyping during development
does not stall work for long.

- The lock is never permanent: a member who forgot the password is not locked out for good.
- A locked user gets the same "invalid credentials" answer as a wrong password, so the
  response does not reveal whether the account exists or is locked.
- `--import-realm` does not overwrite an existing local realm. An older local realm keeps
  `bruteForceProtected=false` until it is set by hand or the realm is re-imported.
- Unlocking a user: [`KEYCLOAK_RUNBOOK.md`](KEYCLOAK_RUNBOOK.md#unlock-a-locked-user).

## State found on 2026-09-29

These were read-only probes against `auth.graceops.de`:

- `hanmaum-dn-prod` has no `hanmaum-mobile` client, so mobile login against prod fails.
- `hanmaum-dashboard` in staging and prod accepts none of the known dashboard redirect URIs.
- `hanmaum-mobile` in staging still allows `http://localhost:4200/` and
  `com.hanmaum.app://login-callback`, and does not enforce PKCE.
- The local dev realm `hanmaum` is live on the prod instance. Its private keys are in git.
  That is how prod ends up with it: `docker-compose.prod.yml` used to start Keycloak with
  `--import-realm`. The import is removed now. The existing realm still has to be deleted
  by hand (see step 5 below).

## Rollout: staging first, then prod

Run everything on the Hetzner host, from the deploy directory. Both deploys copy
`scripts/keycloak/` there. The script runs `kcadm` inside the Keycloak
container and logs in with that container's bootstrap admin, so no password is typed or
printed. It needs `jq` on the host. Without `APPLY=1` it only prints what it would change.

1. Dry run against staging:

   ```bash
   KC_REALM=hanmaum-dn-st DASHBOARD_URLS=https://dn-admin-dashboard.st.graceops.de \
     scripts/keycloak/configure-realm.sh
   ```

2. Apply it and check the tokens with a non-production test user:

   ```bash
   APPLY=1 KC_REALM=hanmaum-dn-st DASHBOARD_URLS=https://dn-admin-dashboard.st.graceops.de \
     VERIFY_USERNAME=<test user> scripts/keycloak/configure-realm.sh
   ```

   The last lines must say, for both user clients, `aud contains hanmaum-dn-api` and
   `iss https://auth.graceops.de/realms/hanmaum-dn-st`. After that, log in to the staging
   dashboard and the staging app, and run the matrix in `KEYCLOAK_RUNBOOK.md`.
3. Check that member creation, the verification mail and member purge still work in
   staging. These calls use the reduced backend role.
4. Repeat steps 1 to 3 with `KC_REALM=hanmaum-dn-prod` and `DASHBOARD_URLS=` (empty until
   prod has a dashboard). It is the same instance, so nothing else changes.
5. Delete the dev realm `hanmaum` on the shared instance in the Admin Console. Only do
   this after steps 1 to 4, and only after confirming that no backend `.env` still has
   `KEYCLOAK_REALM=hanmaum`.

## Mobile login with username and password

The app logs in with its own form and the password grant (`grant_type=password`,
`AuthRepositoryImpl`). This is a product decision from 2026-09-29: users type username and
password in the app, not in a browser. So `hanmaum-mobile` keeps direct access grants, and
the script's default `MOBILE_DIRECT_GRANTS=true` stays. The #235 criterion "direct grants
off after the mobile migration" is dropped.

What this costs: the app sees the password, and the password grant is not part of OAuth 2.1.
Keycloak's brute force detection on the realm is the counterweight and should stay on.
`MOBILE_DIRECT_GRANTS=false` remains in the script in case the app ever moves to a browser
login.
