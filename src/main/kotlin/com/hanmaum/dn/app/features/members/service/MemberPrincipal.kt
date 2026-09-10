package com.hanmaum.dn.app.features.members.service

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

/**
 * The authenticated caller, as much of them as resolving a member needs.
 *
 * Endpoints used to pass a bare subject string, which quietly forced every one of them into
 * the lookup that cannot adopt a legacy account — the email claims simply were not there to
 * pass. Carrying the three fields together makes the linking resolution available wherever
 * a JWT is.
 */
data class MemberPrincipal(
    val subject: String,
    val email: String?,
    val emailVerified: Boolean,
) {
    companion object {
        fun from(authentication: JwtAuthenticationToken): MemberPrincipal =
            MemberPrincipal(
                subject = authentication.token.subject,
                email = authentication.token.getClaimAsString("email"),
                emailVerified = authentication.token.getClaimAsBoolean("email_verified") == true,
            )
    }
}
