package com.hanmaum.dn.app.common.interceptor

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Blocks PENDING and INACTIVE members from accessing any endpoint except:
 * - GET /api/v1/members/me  (so the mobile app can read status and show the correct screen)
 * - POST /api/v1/members/register (public, bypasses security entirely)
 *
 * ADMIN-role users bypass this check entirely.
 */
@Component
class MemberStatusInterceptor(
    private val memberRepository: MemberRepository,
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val path = request.requestURI

        // Always allow /me and /register through
        if (path.endsWith("/members/me") || path.contains("/members/register")) {
            return true
        }

        val auth = SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken
            ?: return true   // unauthenticated — let security layer handle it

        // ADMIN users are never blocked by member status
        if (auth.authorities.any { it.authority == "ROLE_ADMIN" }) return true

        val keycloakId = auth.token.subject
        val member = memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakId)
            ?: return true   // no member record → admin-only account, allow through

        if (member.memberStatus != MemberStatus.ACTIVE) {
            response.sendError(
                HttpStatus.FORBIDDEN.value(),
                "계정이 활성화되지 않았습니다. 관리자 승인을 기다려 주세요.",
            )
            return false
        }

        return true
    }
}
