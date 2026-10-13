package com.hanmaum.dn.app.features.ministry.api.v1

import com.hanmaum.dn.app.common.security.Roles
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

private val ADMIN_AUTHORITIES = setOf(Roles.authority(Roles.ADMIN), Roles.authority(Roles.PASTOR))

/**
 * True for ADMIN and PASTOR, who bypass the ministry ownership check. PASTOR is listed explicitly:
 * [Roles.HIERARCHY] lets it pass `hasRole('ADMIN')`, but the token itself only carries `ROLE_PASTOR`.
 */
internal fun JwtAuthenticationToken.isAdmin(): Boolean = authorities.any { it.authority in ADMIN_AUTHORITIES }

/** General operations editors manage all ministries, just like admins and pastors. */
internal fun JwtAuthenticationToken.canManageMinistries(): Boolean =
    isAdmin() || authorities.any { it.authority == Roles.authority(Roles.GENERAL_OPERATIONS_EDITOR) }

/** Viewers may inspect every ministry's applications, but cannot review them. */
internal fun JwtAuthenticationToken.canReadMinistryApplications(): Boolean =
    canManageMinistries() || authorities.any { it.authority == Roles.authority(Roles.GENERAL_OPERATIONS_VIEWER) }
