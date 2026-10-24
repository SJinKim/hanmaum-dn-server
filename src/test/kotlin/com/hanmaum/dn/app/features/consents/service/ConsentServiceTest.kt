package com.hanmaum.dn.app.features.consents.service

import com.hanmaum.dn.app.common.api.ApiErrorCode
import com.hanmaum.dn.app.common.domainvalue.ConsentPurpose
import com.hanmaum.dn.app.features.consents.api.v1.dto.ConsentGrant
import com.hanmaum.dn.app.features.consents.api.v1.dto.ConsentState
import com.hanmaum.dn.app.features.consents.config.ConsentProperties
import com.hanmaum.dn.app.features.consents.domain.ConsentEndReason
import com.hanmaum.dn.app.features.consents.domain.ConsentSource
import com.hanmaum.dn.app.features.consents.domain.MemberConsent
import com.hanmaum.dn.app.features.consents.repository.MemberConsentRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.CurrentMemberResolver
import com.hanmaum.dn.app.features.members.service.MemberPrincipal
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.never
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class ConsentServiceTest {
    @Mock private lateinit var consentRepository: MemberConsentRepository

    @Mock private lateinit var memberRepository: MemberRepository

    private val now = Instant.parse("2026-10-22T09:00:00Z")
    private val caller = MemberPrincipal(subject = "kc-001", email = "a@example.com", emailVerified = true)
    private val member =
        Member(lastName = "김", firstName = "철수", email = caller.email, keycloakId = caller.subject).apply { id = 1L }

    @BeforeEach
    fun setUp() {
        lenient().`when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-001")).thenReturn(member)
        lenient().`when`(consentRepository.save(any<MemberConsent>())).thenAnswer { it.arguments[0] }
        lenient().`when`(consentRepository.saveAndFlush(any<MemberConsent>())).thenAnswer { it.arguments[0] }
    }

    private fun service(
        versions: Map<ConsentPurpose, String> = mapOf(ConsentPurpose.APP_PRIVACY to "v2"),
        enforce: Boolean = false,
    ) = ConsentService(
        consentRepository,
        CurrentMemberResolver(memberRepository, org.mockito.kotlin.mock()),
        ConsentProperties(versions = versions, enforceAccountConsent = enforce),
        Clock.fixed(now, ZoneId.of("Europe/Berlin")),
    )

    private fun standing(version: String) =
        MemberConsent(
            member = member,
            purpose = ConsentPurpose.APP_PRIVACY,
            policyVersion = version,
            source = ConsentSource.REGISTRATION,
            grantedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

    @Test
    fun `policies list every purpose with its version, defaulting to v1`() {
        val policies = service().policies().associateBy { it.purpose }

        assertEquals("v2", policies.getValue(ConsentPurpose.APP_PRIVACY).version)
        assertTrue(policies.getValue(ConsentPurpose.APP_PRIVACY).requiredForAccount)
        assertEquals("v1", policies.getValue(ConsentPurpose.COURSE_APPLICATION).version)
    }

    @Test
    fun `check refuses a grant for a version no longer in force`() {
        val e = assertThrows<ConsentException> { service().check(ConsentPurpose.APP_PRIVACY, ConsentGrant(true, "v1")) }

        assertEquals(HttpStatus.CONFLICT, e.status)
        assertEquals(ApiErrorCode.CONSENT_VERSION_OUTDATED, e.code)
    }

    @Test
    fun `check refuses an unaccepted grant`() {
        val e = assertThrows<ConsentException> { service().check(ConsentPurpose.APP_PRIVACY, ConsentGrant(false, "v2")) }

        assertEquals(ApiErrorCode.CONSENT_REQUIRED, e.code)
    }

    @Test
    fun `check refuses a missing grant`() {
        val e = assertThrows<ConsentException> { service().check(ConsentPurpose.APP_PRIVACY, null) }

        assertEquals(HttpStatus.BAD_REQUEST, e.status)
        assertEquals(ApiErrorCode.CONSENT_REQUIRED, e.code)
    }

    @Test
    fun `require records the grant with the version sent and the server's time`() {
        val recorded =
            service().require(member, ConsentPurpose.APP_PRIVACY, ConsentGrant(true, "v2"), ConsentSource.REGISTRATION)

        assertEquals("v2", recorded.policyVersion)
        assertEquals(now, recorded.grantedAt)
        assertEquals(ConsentSource.REGISTRATION, recorded.source)
        assertTrue(recorded.isStanding)
    }

    @Test
    fun `require without a grant accepts a standing grant of the current version`() {
        val current = standing("v2")
        `when`(consentRepository.findStanding(1L, ConsentPurpose.APP_PRIVACY)).thenReturn(current)

        val result = service().require(member, ConsentPurpose.APP_PRIVACY, null, ConsentSource.IN_APP)

        assertSame(current, result)
        verify(consentRepository, never()).save(any<MemberConsent>())
    }

    @Test
    fun `require without a grant does not accept a standing grant of an outdated version`() {
        `when`(consentRepository.findStanding(1L, ConsentPurpose.APP_PRIVACY)).thenReturn(standing("v1"))

        val e =
            assertThrows<ConsentException> {
                service().require(member, ConsentPurpose.APP_PRIVACY, null, ConsentSource.IN_APP)
            }
        assertEquals(ApiErrorCode.CONSENT_REQUIRED, e.code)
    }

    @Test
    fun `require without any grant fails and records nothing`() {
        val e =
            assertThrows<ConsentException> {
                service().require(member, ConsentPurpose.APP_PRIVACY, null, ConsentSource.REGISTRATION)
            }
        assertEquals(ApiErrorCode.CONSENT_REQUIRED, e.code)
        verify(consentRepository, never()).save(any<MemberConsent>())
    }

    @Test
    fun `granting a newer version supersedes the standing grant`() {
        val old = standing("v1")
        `when`(consentRepository.findStanding(1L, ConsentPurpose.APP_PRIVACY)).thenReturn(old)

        val status = service().grant(caller, ConsentPurpose.APP_PRIVACY, ConsentGrant(true, "v2"))

        assertEquals(ConsentEndReason.SUPERSEDED, old.endReason)
        assertEquals(now, old.endedAt)
        verify(consentRepository).saveAndFlush(old)
        val inserted = argumentCaptor<MemberConsent>()
        verify(consentRepository).save(inserted.capture())
        assertEquals("v2", inserted.firstValue.policyVersion)
        assertEquals(ConsentSource.IN_APP, inserted.firstValue.source)
        assertEquals(ConsentState.GRANTED, status.state)
    }

    @Test
    fun `granting the version already standing writes nothing`() {
        val current = standing("v2")
        `when`(consentRepository.findStanding(1L, ConsentPurpose.APP_PRIVACY)).thenReturn(current)

        val status = service().grant(caller, ConsentPurpose.APP_PRIVACY, ConsentGrant(true, "v2"))

        assertTrue(current.isStanding)
        verify(consentRepository, never()).save(any<MemberConsent>())
        assertEquals(current.grantedAt, status.grantedAt)
    }

    @Test
    fun `withdrawal ends the standing grant instead of deleting it`() {
        val current = standing("v2")
        `when`(consentRepository.findStanding(1L, ConsentPurpose.APP_PRIVACY)).thenReturn(current)

        service().withdraw(caller, ConsentPurpose.APP_PRIVACY)

        assertEquals(ConsentEndReason.WITHDRAWN, current.endReason)
        assertFalse(current.isStanding)
        verify(consentRepository).save(current)
    }

    @Test
    fun `withdrawing with nothing standing is a no-op`() {
        service().withdraw(caller, ConsentPurpose.APP_PRIVACY)

        verify(consentRepository, never()).save(any<MemberConsent>())
    }

    @Test
    fun `status flags action required for a missing or outdated account consent`() {
        `when`(consentRepository.findStanding(1L)).thenReturn(listOf(standing("v1")))

        val status = service().status(caller)

        assertTrue(status.actionRequired)
        val privacy = status.consents.single { it.purpose == ConsentPurpose.APP_PRIVACY }
        assertEquals(ConsentState.OUTDATED, privacy.state)
        assertEquals("v1", privacy.grantedVersion)
        assertEquals("v2", privacy.currentVersion)
        assertEquals(
            ConsentState.MISSING,
            status.consents.single { it.purpose == ConsentPurpose.COURSE_APPLICATION }.state,
        )
    }

    @Test
    fun `status needs no action when the account consent is current, whatever the optional ones say`() {
        `when`(consentRepository.findStanding(1L)).thenReturn(listOf(standing("v2")))

        assertFalse(service().status(caller).actionRequired)
    }

    @Test
    fun `the account lock rejects a member without a standing account consent with 403`() {
        `when`(consentRepository.findStanding(1L)).thenReturn(emptyList())

        val e = assertThrows<ConsentException> { service(enforce = true).requireAccountConsent(member) }

        assertEquals(HttpStatus.FORBIDDEN, e.status)
        assertEquals(ApiErrorCode.CONSENT_REQUIRED, e.code)
    }

    @Test
    fun `the account lock rejects an outdated account consent`() {
        `when`(consentRepository.findStanding(1L)).thenReturn(listOf(standing("v1")))

        val e = assertThrows<ConsentException> { service(enforce = true).requireAccountConsent(member) }

        assertEquals(ApiErrorCode.CONSENT_REQUIRED, e.code)
    }

    @Test
    fun `the account lock lets a current account consent through, whatever the optional ones say`() {
        `when`(consentRepository.findStanding(1L)).thenReturn(listOf(standing("v2")))

        service(enforce = true).requireAccountConsent(member)
    }

    @Test
    fun `the account lock does nothing while it is not enforced`() {
        service(enforce = false).requireAccountConsent(member)

        verify(consentRepository, never()).findStanding(1L)
    }
}
