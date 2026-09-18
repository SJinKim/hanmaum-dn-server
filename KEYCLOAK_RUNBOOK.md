# Keycloak e-mail and newcomer RBAC runbook

This runbook is the deployment contract for server issues #150 and #180. Apply it to
the local `hanmaum` realm and to both deployed realms (`hanmaum-dn-st` and
`hanmaum-dn-prod`). Never copy credentials between environments.

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

Realm startup import only creates missing realms; it never updates an existing one.
For staging and production, apply the settings in the Admin Console and use **Test
connection** before enabling password reset. Then register a disposable account and
verify all of the following:

1. The registration endpoint succeeds.
2. Password login succeeds before the link is clicked.
3. The verification e-mail arrives (also inspect spam).
4. After clicking the link, a refreshed access token has `email_verified: true`.
5. Forgot-password sends a reset link to the disposable account.

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
