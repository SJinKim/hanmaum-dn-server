package com.hanmaum.dn.app.common.domainvalue

/**
 * Allowed status transitions:
 *   PENDING  → ACTIVE (admin approval)
 *   ACTIVE   ↔ INACTIVE
 *   ACTIVE / INACTIVE → DELETED (terminal, soft-delete endpoint only)
 *
 * PENDING is the default for newly registered members — admin must approve.
 * REJECTED is kept as a deprecated alias; no longer assigned.
 */
enum class MemberStatus {
    PENDING,
    ACTIVE,
    INACTIVE,
    DELETED,

    @Deprecated("No longer used. Historical values only.")
    REJECTED,
}
