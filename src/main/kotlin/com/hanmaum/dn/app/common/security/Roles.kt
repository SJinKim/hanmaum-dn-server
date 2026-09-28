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

    /** A pastor may do everything an admin may. */
    const val HIERARCHY = "ROLE_$PASTOR > ROLE_$ADMIN"

    fun authority(role: String): String = "ROLE_$role"
}
