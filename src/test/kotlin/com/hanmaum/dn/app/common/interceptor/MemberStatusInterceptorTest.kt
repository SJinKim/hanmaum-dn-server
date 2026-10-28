package com.hanmaum.dn.app.common.interceptor

import com.hanmaum.dn.app.common.api.ApiErrorCode
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.common.security.Roles
import com.hanmaum.dn.app.features.consents.service.ConsentException
import com.hanmaum.dn.app.features.consents.service.ConsentService
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemberStatusInterceptorTest {
    private val memberRepository = mock<MemberRepository>()
    private val consentService = mock<ConsentService>()
    private val interceptor =
        MemberStatusInterceptor(
            memberRepository,
            mock<ObjectProvider<ConsentService>>().also { `when`(it.getObject()).thenReturn(consentService) },
        )
    private val member =
        Member(lastName = "김", firstName = "철수", email = "a@example.com", keycloakId = "kc-001").apply {
            id = 1L
            memberStatus = MemberStatus.ACTIVE
        }

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    private fun signIn(vararg authorities: String) {
        val jwt =
            Jwt
                .withTokenValue("t")
                .header("alg", "none")
                .subject("kc-001")
                .build()
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(jwt, authorities.map { SimpleGrantedAuthority(it) })
    }

    private fun preHandle(path: String) = interceptor.preHandle(MockHttpServletRequest("GET", path), MockHttpServletResponse(), Any())

    private fun lockedOut() = ConsentException(HttpStatus.FORBIDDEN, ApiErrorCode.CONSENT_REQUIRED, "개인정보 수집·이용에 동의해야 앱을 사용할 수 있습니다.")

    @Test
    fun `an active member without the account consent is rejected with CONSENT_REQUIRED`() {
        signIn("ROLE_USER")
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-001")).thenReturn(member)
        doThrow(lockedOut()).`when`(consentService).requireAccountConsent(member)

        val e = assertThrows<ConsentException> { preHandle("/api/v1/events") }

        assertEquals(HttpStatus.FORBIDDEN, e.status)
        assertEquals(ApiErrorCode.CONSENT_REQUIRED, e.code)
    }

    @Test
    fun `an active member with the account consent passes`() {
        signIn("ROLE_USER")
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-001")).thenReturn(member)

        assertTrue(preHandle("/api/v1/events"))
        verify(consentService).requireAccountConsent(member)
    }

    @Test
    fun `the consent routes and members me stay open without consent`() {
        signIn("ROLE_USER")

        listOf(
            "/api/v1/members/me",
            "/api/v1/members/me/consents",
            "/api/v1/members/me/consents/APP_PRIVACY",
            "/api/v1/consents/policies",
        ).forEach { assertTrue(preHandle(it), it) }
        verify(consentService, never()).requireAccountConsent(any())
    }

    @Test
    fun `an admin is never checked for consent`() {
        signIn(Roles.authority(Roles.ADMIN))

        assertTrue(preHandle("/api/v1/events"))
        verify(consentService, never()).requireAccountConsent(any())
    }

    @Test
    fun `a pending member gets the status lock before any consent check`() {
        signIn("ROLE_USER")
        member.memberStatus = MemberStatus.PENDING
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-001")).thenReturn(member)
        val response = MockHttpServletResponse()

        val passed = interceptor.preHandle(MockHttpServletRequest("GET", "/api/v1/events"), response, Any())

        assertEquals(false, passed)
        assertEquals(HttpStatus.FORBIDDEN.value(), response.status)
        verify(consentService, never()).requireAccountConsent(any())
    }
}
