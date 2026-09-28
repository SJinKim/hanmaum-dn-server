# Keycloak e-mail and newcomer RBAC runbook

This runbook is the deployment contract for server issues #150 and #180. Apply it to
the local `hanmaum` realm and to both deployed realms (`hanmaum-dn-st` and
`hanmaum-dn-prod`). Never copy credentials between environments.

Realms, clients, redirect URIs, the API audience and the backend service-account role
per environment are in [`KEYCLOAK_ENVIRONMENTS.md`](KEYCLOAK_ENVIRONMENTS.md) (#235).

## E-mail verification without a login gate

The required state is:

- **Realm settings → Login → Verify email:** off.
- **Authentication → Required actions → Verify Email:** enabled, but **Default
  Action** off.
- **Realm settings → Login → Forgot password:** on, after SMTP has passed its test.
- **Realm settings → Email:** configured with the environment's SMTP account.

`MemberService.registerMember` explicitly sends a verification e-mail after it has
created the Keycloak user. Delivery failure is logged and metered as
`keycloak/send_verify_email`; it does not roll back registration. Consequently, the
verification required action must not be assigned by default or it would again block
the password grant.

The checked-in realm exports contain SMTP placeholders, not credentials. Configure
these variables in the server-local environment used by the Keycloak container:

```text
KEYCLOAK_SMTP_HOST
KEYCLOAK_SMTP_PORT
KEYCLOAK_SMTP_FROM
KEYCLOAK_SMTP_FROM_DISPLAY_NAME
KEYCLOAK_SMTP_REPLY_TO
KEYCLOAK_SMTP_SSL
KEYCLOAK_SMTP_STARTTLS
KEYCLOAK_SMTP_AUTH
KEYCLOAK_SMTP_USER
KEYCLOAK_SMTP_PASSWORD
```

The backend also uses this SMTP connection and `KEYCLOAK_SMTP_FROM` for ministry
application emails to leaders. If these values are missing or delivery fails, the
application request returns 503 and does not save a pending application.

Realm startup import only creates missing realms; it never updates an existing one.
For staging and production, apply the settings in the Admin Console and use **Test
connection** before enabling password reset. Then register a disposable account and
verify all of the following:

1. The registration endpoint succeeds.
2. Password login succeeds before the link is clicked.
3. The verification e-mail arrives (also inspect spam).
4. After clicking the link, a refreshed access token has `email_verified: true`.
5. Forgot-password sends a reset link to the disposable account.

## Staff and leader roles and groups

`pastor` has the same rights as `admin`: the server declares the role hierarchy
`ROLE_PASTOR > ROLE_ADMIN` (`Roles.HIERARCHY`), so every `hasRole('ADMIN')` check
also passes for a pastor. `note_taker` reads 청년 (members), 순 (church groups) and
공지사항 (announcements) and writes 공지사항 only.

| Group | Realm-role mappings | Effective access |
|---|---|---|
| `/staff/admins` | `admin` | Everything |
| `/staff/pastors` | `pastor` | Everything (hierarchy, same as `admin`) |
| `/staff/note-takers` | `note_taker` | Read members and church groups; read and write announcements |
| `/leaders/groups` | `group_leader` | 순장 endpoints |
| `/ministries/<slug>/members` | `<SLUG>_VIEWER` | Read the ministry's data |
| `/ministries/<slug>/leaders` | `<SLUG>_VIEWER`, `<SLUG>_EDITOR`, `ministry_leader` | Read and write the ministry's data |

Both checked-in realm exports contain the roles and the `/staff` and `/leaders`
groups. `/ministries/newcomer` uses the older `viewers`/`editors` leaves described
below; new ministries follow the `members`/`leaders` pattern. On an existing realm:

1. Create realm roles `pastor` and `note_taker` (and `group_leader`,
   `ministry_leader` if absent).
2. Create the groups above and assign exactly the listed roles on each leaf
   group's **Role mapping** tab.
3. Add users to a leaf group, not roles directly to users.
4. Refresh the token and confirm the role names under `realm_access.roles`.

## Newcomer roles and groups

Authorization is based only on realm roles carried in `realm_access.roles`. Groups
are the operator-facing way to assign those roles.

| Group | Realm-role mappings | Effective access |
|---|---|---|
| `/ministries/newcomer/viewers` | `NEWCOMER_VIEWER` | Read newcomer data |
| `/ministries/newcomer/editors` | `NEWCOMER_VIEWER`, `NEWCOMER_EDITOR` | Read and write newcomer data |
| n/a | `ADMIN` | Read and write newcomer data |

The mappings are included in both checked-in realm exports. On an existing realm:

1. Create realm roles `NEWCOMER_VIEWER` and `NEWCOMER_EDITOR` if absent.
2. Create the nested groups shown above.
3. On each leaf group's **Role mapping** tab, assign exactly the roles in the table.
4. Add users only to a leaf group. Do not assign newcomer roles directly to users.
5. Sign out and in again (or refresh the token), then inspect the access token and
   confirm the expected role names appear under `realm_access.roles`.

Server controllers must use `@NewcomerReadAccess` for reads and
`@NewcomerWriteAccess` for writes. Do not reuse generic member endpoints for
newcomer PII; doing so would bypass these feature-specific checks. Responses and logs
must not include newcomer PII on authorization failures.

## Environment verification matrix

Run this matrix independently in local development, staging, and production with
non-production test identities:

| Identity | Read endpoint | Write endpoint |
|---|---:|---:|
| `MEMBER` only | 403 | 403 |
| newcomer viewer | 200 | 403 |
| newcomer editor | 200 | 2xx |
| `ADMIN` | 200 | 2xx |
| no token | 401 | 401 |

The server returns RFC 9457 `application/problem+json` bodies for 401 and 403. A
denial body contains only status metadata and a generic detail; it never echoes the
requested resource or person.
