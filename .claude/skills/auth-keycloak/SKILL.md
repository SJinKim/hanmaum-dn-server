---
name: auth-keycloak
description: Use when touching authentication, JWT handling, member provisioning, or anything involving Keycloak identity in this backend
---

# Keycloak integration for hanmaum-dn-server

## Identity model
- Keycloak is the source of truth for identity.
- JWT `sub` claim = stable user ID from Keycloak.
- We store this in `members.keycloak_id` (UUID, unique, not null).
- We never store passwords. Keycloak handles all credential logic.

## First-login provisioning pattern
When a request arrives with a JWT that has no matching `members` row:

1. Look up by `keycloak_id` first (the `sub` claim).
2. If not found, fall back to email match — one-time migration path.
3. If matched by email: set `keycloak_id` on the existing row, don't create a duplicate.
4. If no match at all: create a new `members` row with `keycloak_id`, `email`,
   and minimal profile fields from the JWT claims.
5. All of this lives in `MemberService.provisionOnFirstLogin(Jwt)`.

This runs inside the JWT authentication filter — never in a controller.

## JWT parsing rules
- Never parse JWTs manually. Use Spring Security's `JwtAuthenticationToken`.
- Required claims: `sub`, `email`, `preferred_username`.
- Optional claims: `given_name`, `family_name`, `email_verified`.
- If a required claim is missing, reject with 401 — do not silently fall back.

## Configuration
- Issuer URI: `app.keycloak.issuer-uri` with Spring profile fallbacks.
- Every `@Value` reading Keycloak config provides a default:
  `@Value("\${app.keycloak.issuer-uri:http://localhost:8091/realms/hanmaum}")`
- Local Keycloak runs on port 8091 via Docker Compose with explicit port mapping.

## Realm changes and rollout

Every change to application realm roles, groups or group-role mappings includes:

- Update `scripts/keycloak/configure-realm.sh` or the declarative configuration it reads,
  and both checked-in realm exports, in the same change. This applies when an endpoint
  introduces a new role, not just when the task explicitly mentions Keycloak automation.
  Extend the existing managed configuration; an export or manual console checklist alone
  does not update an existing realm.
- Preserve the script's dry-run default and `APPLY=1` mode. Test missing and partially
  configured state, applying twice without duplicates, and a final dry run with no changes.
  Preserve unrelated realm configuration and user memberships. Do not assign people to
  privileged groups merely because the groups were created.
- Document the target realm and application command in `KEYCLOAK_RUNBOOK.md` or
  `KEYCLOAK_ENVIRONMENTS.md`. An ordinary backend deploy currently copies the script;
  it does not run realm reconciliation. Use the actual configured realm name.
- Verify the roles, group paths and leaf-group role mappings after application in ST,
  then verify authorization with refreshed tokens. Apply to Prod after ST verification
  when Prod rollout is in scope. Do not print credentials or tokens.
- Report repository implementation, deployment, realm application and live verification
  separately. If live rollout is pending, state the remaining step; do not report it as
  completed because the PR merged. This workflow does not itself authorize a deployment
  or a live realm change beyond the user's task.

## Don't do
- Don't use email as a primary identity key anywhere — it's mutable.
- Don't duplicate member rows if the same Keycloak user exists.
- Don't log JWT contents, even at DEBUG. Log `sub` only if needed for tracing.
- Don't add custom auth endpoints. All sign-in/sign-up flows go through Keycloak.

## When changing this
- JWT filter or provisioning logic changes require integration tests against a
  test Keycloak realm.
- Changes to `members.keycloak_id` need a Flyway migration — see `db-migrations`.
- PII changes visible via API coordinate with `api-contracts`.
