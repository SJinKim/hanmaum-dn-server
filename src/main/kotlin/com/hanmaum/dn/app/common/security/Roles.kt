package com.hanmaum.dn.app.common.security

/**
 * Keycloak realm roles as Spring authorities.
 *
 * Keycloak hands the roles out through groups (see KEYCLOAK_RUNBOOK.md);
 * `SecurityConfig.jwtAuthenticationConverter` turns `pastor` into `ROLE_PASTOR`.
 * `@PreAuthorize` expressions use the names without the `ROLE_` prefix.
 */
object Roles {
    const val ADMIN = "ADMIN"
    const val PASTOR = "PASTOR"
    const val NOTE_TAKER = "NOTE_TAKER"
    const val GROUP_LEADER = "GROUP_LEADER"
    const val MINISTRY_LEADER = "MINISTRY_LEADER"
    const val NEWCOMER_VIEWER = "NEWCOMER_VIEWER"
    const val NEWCOMER_EDITOR = "NEWCOMER_EDITOR"

    /** Members of the 사역 general operations (`/ministries/general_operations/viewers|editors`). */
    const val GENERAL_OPERATIONS_VIEWER = "GENERAL_OPERATIONS_VIEWER"
    const val GENERAL_OPERATIONS_EDITOR = "GENERAL_OPERATIONS_EDITOR"

    /** Members of the 양육 team (`/ministries/training/viewers|editors`). */
    const val TRAINING_VIEWER = "TRAINING_VIEWER"
    const val TRAINING_EDITOR = "TRAINING_EDITOR"

    const val GUIDE_VIEWER = "GUIDE_VIEWER"
    const val GUIDE_EDITOR = "GUIDE_EDITOR"

    /** Pastors inherit admin access; team editors inherit their viewer access. */
    const val HIERARCHY =
        "ROLE_$PASTOR > ROLE_$ADMIN\n" +
            "ROLE_$GENERAL_OPERATIONS_EDITOR > ROLE_$GENERAL_OPERATIONS_VIEWER\n" +
            "ROLE_$TRAINING_EDITOR > ROLE_$TRAINING_VIEWER\n" +
            "ROLE_$GUIDE_EDITOR > ROLE_$GUIDE_VIEWER"

    fun authority(role: String): String = "ROLE_$role"
}
