# Keycloak environments: realms, clients and audience

Target state for the Keycloak side of each environment (#235, parent #234). This file
holds no secrets. Client secrets live only in the server's `.env` files and in Keycloak.

## Matrix

One Keycloak instance at `auth.graceops.de` hosts both deployed realms. The issuer and
the realm's signing keys separate staging from prod. The audience is the same in both.

| | Local dev | Staging | Prod |
|---|---|---|---|
| Realm | `hanmaum` | `hanmaum-dn-st` | `hanmaum-dn-prod` |
| Public issuer (`iss`) | `http://localhost:8091/realms/hanmaum` | `https://auth.graceops.de/realms/hanmaum-dn-st` | `https://auth.graceops.de/realms/hanmaum-dn-prod` |
| JWKS (backend, internal) | `http://localhost:8091/realms/hanmaum/...` | `http://hanmaumApp-keycloak:8090/realms/hanmaum-dn-st/protocol/openid-connect/certs` | `http://hanmaumApp-keycloak:8090/realms/hanmaum-dn-prod/protocol/openid-connect/certs` |
| API audience (`aud`) | `hanmaum-dn-api` | `hanmaum-dn-api` | `hanmaum-dn-api` |
| How the realm is set up | `--import-realm` from `infrastructure/docker/keycloak/export/` | `scripts/keycloak/configure-realm.sh` | `scripts/keycloak/configure-realm.sh` |

The backend variables per environment are `KEYCLOAK_REALM` and
`APP_SECURITY_KEYCLOAK_PUBLIC_ISSUER`. The issuer must equal the `iss` claim exactly.
Checking the audience on the server side is #236. Until #236 lands, the `aud` claim is
emitted but not yet enforced.

### Clients

| Client | Type | Flows | Redirect URIs | Web origins | Default scopes include |
|---|---|---|---|---|---|
| `hanmaum-dashboard` | public | authorization code + PKCE S256 | `<dashboard origin>/*` per origin | the dashboard origins, never `*` | `hanmaum-dn-api-audience` |
| `hanmaum-mobile` | public | authorization code + PKCE S256; password grant only until the mobile PKCE migration | `com.hanmaum.dn.mobile:/oauth2redirect` | none (native app) | `hanmaum-dn-api-audience` |
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
| Staging | **open** |
| Prod | **open** |

The Vercel URLs seen so far are `https://hanmaum-dn-web-app.vercel.app` and preview
deployments under `dn-admin-dashboard-*-sjinkims-projects.vercel.app`. Keycloak does
not accept wildcards inside a host name. List preview URLs explicitly, or leave them
out of prod.

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

Run everything on the Hetzner host, from the deploy directory. The prod deploy copies
`scripts/keycloak/` there. The script runs `kcadm` inside the Keycloak
container and logs in with that container's bootstrap admin, so no password is typed or
printed. It needs `jq` on the host. Without `APPLY=1` it only prints what it would change.

1. Dry run against staging:

   ```bash
   KC_REALM=hanmaum-dn-st DASHBOARD_URLS=https://<staging dashboard> \
     scripts/keycloak/configure-realm.sh
   ```

2. Apply it and check the tokens with a non-production test user:

   ```bash
   APPLY=1 KC_REALM=hanmaum-dn-st DASHBOARD_URLS=https://<staging dashboard> \
     VERIFY_USERNAME=<test user> scripts/keycloak/configure-realm.sh
   ```

   The last lines must say, for both user clients, `aud contains hanmaum-dn-api` and
   `iss https://auth.graceops.de/realms/hanmaum-dn-st`. After that, log in to the staging
   dashboard and the staging app, and run the matrix in `KEYCLOAK_RUNBOOK.md`.
3. Check that member creation, the verification mail and member purge still work in
   staging. These calls use the reduced backend role.
4. Repeat steps 1 to 3 with `KC_REALM=hanmaum-dn-prod` and the prod dashboard origin.
5. Delete the dev realm `hanmaum` on the prod instance in the Admin Console. Only do
   this after steps 1 to 4, and only after confirming that no backend `.env` still has
   `KEYCLOAK_REALM=hanmaum`.

## After the mobile PKCE migration

Once the app logs in through the browser with `com.hanmaum.dn.mobile:/oauth2redirect`:

```bash
APPLY=1 MOBILE_DIRECT_GRANTS=false KC_REALM=hanmaum-dn-st DASHBOARD_URLS=... \
  scripts/keycloak/configure-realm.sh
```

Run it for staging first and then for prod. This turns off the password grant, the last
open item of #235. In the local export, `hanmaum-mobile` keeps direct grants and the old
`com.hanmaum.app://login-callback` redirect until the app has migrated.
