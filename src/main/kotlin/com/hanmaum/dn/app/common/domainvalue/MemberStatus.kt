package com.hanmaum.dn.app.common.domainvalue

/**
 * Allowed status transitions:
 *   PENDING  → ACTIVE   (admin approval)
 *   PENDING  → REJECTED (admin rejection, POST /members/{publicId}/reject)
 *   ACTIVE   ↔ INACTIVE
 *   ACTIVE / INACTIVE → DELETED (terminal, soft-delete endpoint only)
 *
 * PENDING is the default for app registrations — admin must approve. It is the approval
 * queue and nothing else: members created without an account (새가족 form, 빠른 등록, import)
 * start ACTIVE, because there is no app access to grant (#275). When such a member later
 * gets an account through linking, the identity check or the admin's link decision stands
 * in for the approval, as it already does for manually added members.
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
