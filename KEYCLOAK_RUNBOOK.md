# Keycloak e-mail and newcomer RBAC runbook

This runbook is the deployment contract for server issues #150 and #180. Apply it to
the local `hanmaum` realm and to both deployed realms (`hanmaum-dn-st` and
`hanmaum-dn-prod`). Never copy credentials between environments.

Realms, clients, redirect URIs, the API audience and the backend service-account role
per environment are in [`KEYCLOAK_ENVIRONMENTS.md`](KEYCLOAK_ENVIRONMENTS.md) (#235).

## Unlock a locked user

Brute-force protection (#244) locks an account temporarily after repeated failed logins.
The lock ends by itself (see [`KEYCLOAK_ENVIRONMENTS.md`](KEYCLOAK_ENVIRONMENTS.md#brute-force-protection)).
To lift it earlier, for example after a member called in:

1. Admin console: realm → Users → the user. A locked user shows a "Temporarily locked"
   toggle. Turn it off.
2. Or with kcadm inside the container, after `kcadm.sh config credentials` for the master realm:

   ```bash
   ID=$(kcadm.sh get users -r "$REALM" -q username="$USERNAME" -q exact=true --fields id --format csv --noquotes)
   kcadm.sh get attack-detection/brute-force/users/$ID -r "$REALM"     # disabled, numFailures
   kcadm.sh delete attack-detection/brute-force/users/$ID -r "$REALM"  # unlock
   ```

The unlock also resets the failure count. Check first that the failures came from the
member and not from someone guessing (`lastIPFailure` in the `get` output).

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
also passes for a pastor. `note_taker` reads and writes 공지사항 (announcements),
출석 (attendance), 이벤트 (event RSVPs) and 주보 (bulletins). It does not see
청년 (members) or 순 (church groups); those stay with `admin` and `pastor`. For
the home counters it reads only `GET /members/count`.

| Group | Realm-role mappings | Effective access |
|---|---|---|
| `/staff/admins` | `admin` | Everything |
| `/staff/pastors` | `pastor` | Everything (hierarchy, same as `admin`) |
| `/staff/note-takers` | `note_taker` | Read and write announcements, attendance, events and bulletins; member counts only |
| `/leaders/groups` | `group_leader` | 순장 endpoints |
| `/leaders/ministries` | `ministry_leader` | 사역 leader endpoints, limited to the 사역 the user leads |
| `/ministries/general_operations/viewers` | `GENERAL_OPERATIONS_VIEWER` | Read attendance (including the roster), events, ministries (including applications), bulletins and announcements |
| `/ministries/general_operations/editors` | `GENERAL_OPERATIONS_VIEWER`, `GENERAL_OPERATIONS_EDITOR` | Read and write those five areas; manage every ministry and accept/reject applications |
| `/ministries/<slug>/viewers` | `<SLUG>_VIEWER` | Read the ministry's data in the web app |
| `/ministries/<slug>/editors` | `<SLUG>_VIEWER`, `<SLUG>_EDITOR` | Read and write the ministry's data in the web app |

Keycloak roles grant API access as well as web access. Who belongs to a 사역 and who leads it lives in
the database (`MinistryAssignment` with role `LEADER`, see #300): `ministry_leader`
lets a user call the leader endpoints, and the server then allows changes only on
the 사역 that user leads. Every 사역 follows the `viewers`/`editors` pattern of
`/ministries/newcomer`. The first team beyond newcomer is
`/ministries/general_operations` with `GENERAL_OPERATIONS_VIEWER` and
`GENERAL_OPERATIONS_EDITOR`.

General operations viewers cannot use the administrative write endpoints in these
five areas; editors can create, update, delete, publish and withdraw. Editors
bypass the ministry ownership check for member management and application
reviews; viewers bypass it only when listing applications. Neither role grants
access to member administration or church-group administration. Member self-service
endpoints (attendance check-in, own RSVP response and ministry application) keep
their existing authenticated access.

Both checked-in realm exports contain these roles and groups. On an existing realm:

1. Create realm roles `pastor`, `note_taker`, `GENERAL_OPERATIONS_VIEWER` and
   `GENERAL_OPERATIONS_EDITOR` (and `group_leader`, `ministry_leader` if absent).
2. Create the groups above and assign exactly the listed roles on each leaf
   group's **Role mapping** tab.
3. Add users to a leaf group, not roles directly to users.
4. Refresh the token and confirm the role names under `realm_access.roles`.

### Migrating `ministry_leader` to `/leaders/ministries`

ST and production share one Keycloak instance. Run the whole section in the ST
realm, sign it off, then repeat it in the production realm and sign that off
separately.

1. Create group `/leaders/ministries` and map realm role `ministry_leader` to it.
2. Open **Users in role** on `ministry_leader` and note every user it lists.
3. For each of those users, one at a time:
   1. Add the user to `/leaders/ministries`.
   2. On the user's **Role mapping** tab, untick **Hide inherited roles** and
      confirm `ministry_leader` appears as inherited from `/leaders/ministries`.
   3. Only then remove the direct `ministry_leader` mapping.
   4. Have the user sign in again (or refresh the token) and confirm
      `ministry_leader` is still under `realm_access.roles`.
4. Create `/ministries/general_operations/viewers` and `/editors` with the roles
   from the table, then add the team members.

**Rollback:** if a user's fresh token lacks `ministry_leader`, assign the role
directly to that user again, confirm it is back in the token, and stop before
the next user until the group mapping is fixed.

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
