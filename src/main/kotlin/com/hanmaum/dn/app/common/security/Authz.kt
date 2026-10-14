package com.hanmaum.dn.app.common.security

/** Shared role expressions for the operational dashboard. Editors inherit viewer access. */
object Authz {
    const val OPERATIONS_READ = "hasAnyRole('${Roles.ADMIN}', '${Roles.NOTE_TAKER}', '${Roles.GENERAL_OPERATIONS_VIEWER}')"
    const val OPERATIONS_WRITE = "hasAnyRole('${Roles.ADMIN}', '${Roles.NOTE_TAKER}', '${Roles.GENERAL_OPERATIONS_EDITOR}')"
    const val GROUP_OPERATIONS_READ =
        "hasAnyRole('${Roles.ADMIN}', '${Roles.GROUP_LEADER}', '${Roles.NOTE_TAKER}', " +
            "'${Roles.GENERAL_OPERATIONS_VIEWER}')"
    const val GROUP_OPERATIONS_WRITE =
        "hasAnyRole('${Roles.ADMIN}', '${Roles.GROUP_LEADER}', '${Roles.NOTE_TAKER}', " +
            "'${Roles.GENERAL_OPERATIONS_EDITOR}')"
    const val MINISTRY_READ = "hasAnyRole('${Roles.ADMIN}', '${Roles.MINISTRY_LEADER}', '${Roles.GENERAL_OPERATIONS_VIEWER}')"
    const val MINISTRY_WRITE = "hasAnyRole('${Roles.ADMIN}', '${Roles.MINISTRY_LEADER}', '${Roles.GENERAL_OPERATIONS_EDITOR}')"
    const val MINISTRY_MANAGE = "hasAnyRole('${Roles.ADMIN}', '${Roles.GENERAL_OPERATIONS_EDITOR}')"

    /** Access to the 양육 team's church-group matrix and leader assignments. */
    const val TRAINING_READ = "hasAnyRole('${Roles.ADMIN}', '${Roles.TRAINING_VIEWER}')"
    const val TRAINING_WRITE = "hasAnyRole('${Roles.ADMIN}', '${Roles.TRAINING_EDITOR}')"
}
