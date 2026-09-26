package com.hanmaum.dn.app.common.domainvalue

/**
 * Allowed status transitions:
 *   PENDING  → ACTIVE   (admin approval)
 *   PENDING  → REJECTED (admin rejection, POST /members/{publicId}/reject)
 *   ACTIVE   ↔ INACTIVE
 *   ACTIVE / INACTIVE → DELETED (terminal, soft-delete endpoint only)
 *
 * PENDING is the default for newly registered members — admin must approve.
 * REJECTED keeps the Keycloak account enabled on purpose: the person can still sign in,
 * reads REJECTED from GET /members/me and sees the rejection screen instead of a login
 * error. There is no self-service way out; an admin may still set ACTIVE.
 * (It was deprecated once, only because the first model had no approval flow at all.)
 */
enum class MemberStatus {
    PENDING,
    ACTIVE,
    INACTIVE,
    DELETED,
    REJECTED,
}
