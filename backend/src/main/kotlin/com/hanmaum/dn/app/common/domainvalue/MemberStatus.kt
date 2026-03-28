package com.hanmaum.dn.app.common.domainvalue

/**
 * Allowed status transitions: ACTIVE ↔ INACTIVE → DELETED (terminal).
 * DELETED is set by the soft-delete endpoint only — never via PATCH.
 *
 * Note: PENDING and REJECTED were old values migrated away by V1774557470475.
 * They are kept here temporarily as deprecated aliases so any existing serialised
 * values don't cause a hard failure on startup before the migration runs.
 */
enum class MemberStatus {
    ACTIVE,
    INACTIVE,
    DELETED,

    @Deprecated("Migrated to ACTIVE by V1774557470475")
    PENDING,

    @Deprecated("Migrated to INACTIVE by V1774557470475")
    REJECTED,
}
