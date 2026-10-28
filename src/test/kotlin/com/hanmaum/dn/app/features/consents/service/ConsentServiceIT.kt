package com.hanmaum.dn.app.features.consents.service

import com.hanmaum.dn.app.common.domainvalue.ConsentPurpose
import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.consents.api.v1.dto.ConsentGrant
import com.hanmaum.dn.app.features.consents.config.ConsentProperties
import com.hanmaum.dn.app.features.consents.domain.ConsentEndReason
import com.hanmaum.dn.app.features.consents.domain.ConsentSource
import com.hanmaum.dn.app.features.consents.domain.MemberConsent
import com.hanmaum.dn.app.features.consents.repository.MemberConsentRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.CurrentMemberResolver
import com.hanmaum.dn.app.features.members.service.MemberPrincipal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Runs against PostgreSQL with real commits, because both behaviours under test live in the
 * database: the per-member lock and the move of rows between members (HDN-358).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class, ConsentCarryOver::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Tag("integration")
class ConsentServiceIT {
    @Autowired private lateinit var consents: MemberConsentRepository

    @Autowired private lateinit var members: MemberRepository

    @Autowired private lateinit var carryOver: ConsentCarryOver

    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val tx by lazy { TransactionTemplate(transactionManager) }
    private val created = mutableListOf<Member>()

    private val service by lazy {
        ConsentService(consents, CurrentMemberResolver(members, mock()), ConsentProperties(), Clock.systemUTC())
    }

    private fun member(tag: String): Member =
        members
            .saveAndFlush(
                Member(lastName = "김", firstName = tag, email = "$tag-${System.nanoTime()}@example.org"),
            ).also { created += it }

    private fun standingGrant(
        member: Member,
        version: String,
        source: ConsentSource,
        at: Instant,
    ) = consents.saveAndFlush(MemberConsent(member, ConsentPurpose.APP_PRIVACY, version, source, at))

    @AfterEach
    fun cleanUp() {
        tx.executeWithoutResult {
            created.forEach { consents.deleteAll(consents.findAll().filter { c -> c.member.id == it.id }) }
        }
        members.deleteAll(created)
        created.clear()
    }

    @Test
    fun `a parallel identical grant waits for the first and is then a no-op`() {
        val member = member("parallel")
        val grant = ConsentGrant(accepted = true, version = "v1")
        val started = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val second =
                tx.execute {
                    service.require(member, ConsentPurpose.APP_PRIVACY, grant, ConsentSource.IN_APP)
                    val future =
                        executor.submit {
                            started.countDown()
                            tx.execute { service.require(member, ConsentPurpose.APP_PRIVACY, grant, ConsentSource.IN_APP) }
                        }
                    assertTrue(started.await(5, TimeUnit.SECONDS))
                    // The first grant is not committed yet: the second must wait on the member lock.
                    assertThrows<TimeoutException> { future.get(300, TimeUnit.MILLISECONDS) }
                    future
                }!!
            // Without the lock this fails with a DataIntegrityViolationException on the unique index.
            second.get(10, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        val standing = consents.findStanding(member.id!!)
        assertEquals(1, standing.size)
        assertEquals("v1", standing.single().policyVersion)
    }

    @Test
    fun `carry over moves the grant to the surviving member and leaves none on the registration`() {
        val registration = member("registration")
        val canonical = member("canonical")
        consents.saveAndFlush(
            MemberConsent(
                registration,
                ConsentPurpose.APP_PRIVACY,
                "v1",
                ConsentSource.REGISTRATION,
                Instant.parse("2026-10-28T10:00:00Z"),
            ),
        )

        tx.executeWithoutResult { carryOver.carryOver(registration, canonical) }

        val standing = consents.findStanding(canonical.id!!, ConsentPurpose.APP_PRIVACY)
        assertEquals("v1", standing?.policyVersion)
        assertEquals(ConsentSource.REGISTRATION, standing?.source)
        assertTrue(consents.findStanding(registration.id!!).isEmpty())
    }

    @Test
    fun `carry over keeps the newer grant standing when both members had one`() {
        val registration = member("registration-both")
        val canonical = member("canonical-both")
        val older = Instant.parse("2026-09-01T10:00:00Z")
        val newer = Instant.parse("2026-10-28T10:00:00Z")
        standingGrant(canonical, "v1", ConsentSource.IN_APP, older)
        standingGrant(registration, "v2", ConsentSource.REGISTRATION, newer)

        tx.executeWithoutResult { carryOver.carryOver(registration, canonical) }

        assertEquals("v2", consents.findStanding(canonical.id!!, ConsentPurpose.APP_PRIVACY)?.policyVersion)
        val history = consents.findAll().filter { it.member.id == canonical.id }
        assertEquals(2, history.size)
        assertEquals(ConsentEndReason.SUPERSEDED, history.single { it.policyVersion == "v1" }.endReason)
        assertNull(history.single { it.policyVersion == "v2" }.endedAt)
    }

    @Test
    fun `a withdrawal that resolved the registration ends the grant of the canonical member after a link`() {
        val registration = member("registration-withdraw")
        val canonical = member("canonical-withdraw")
        standingGrant(registration, "v1", ConsentSource.REGISTRATION, Instant.parse("2026-10-28T10:00:00Z"))
        tx.executeWithoutResult { carryOver.carryOver(registration, canonical) }

        // The first lookup still sees the registration (token not yet verified), later ones the canonical member.
        val resolver = mock<CurrentMemberResolver>()
        whenever(resolver.resolveAndLink(any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(registration, canonical)
        val racing = ConsentService(consents, resolver, ConsentProperties(), Clock.systemUTC())

        tx.executeWithoutResult { racing.withdraw(MemberPrincipal("sub", null, false), ConsentPurpose.APP_PRIVACY) }

        // Without the re-resolve after the lock the withdrawal succeeds but the grant stays standing.
        assertNull(consents.findStanding(canonical.id!!, ConsentPurpose.APP_PRIVACY))
        val history = consents.findAll().filter { it.member.id == canonical.id }
        assertEquals(ConsentEndReason.WITHDRAWN, history.single().endReason)
    }
}
