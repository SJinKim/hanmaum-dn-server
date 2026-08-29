package com.hanmaum.dn.app.features.members.domain

/**
 * Why a member left the DN community.
 *
 * MARRIAGE is the common case: DN serves young adults, and members leave on marrying.
 * The values are mirrored by ck_member_graduations_reason — adding one needs a migration.
 */
enum class GraduationReason {
    MARRIAGE,
    RELOCATION,
    AGE_OUT,
    OTHER,
}
