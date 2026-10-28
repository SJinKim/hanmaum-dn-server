package com.hanmaum.dn.app.features.consents.repository

import com.hanmaum.dn.app.common.domainvalue.ConsentPurpose
import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.consents.domain.ConsentEndReason
import com.hanmaum.dn.app.features.consents.domain.ConsentSource
import com.hanmaum.dn.app.features.consents.domain.MemberConsent
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class MemberConsentRepositoryIT {
    @Autowired lateinit var repository: MemberConsentRepository

    @Autowired lateinit var entityManager: EntityManager

    private val now = Instant.parse("2026-10-22T10:00:00Z")

    private fun member(): Member = Member(lastName = "김", firstName = "철수").also { entityManager.persist(it) }

    private fun grant(
        member: Member,
        purpose: ConsentPurpose = ConsentPurpose.APP_PRIVACY,
        version: String = "v1",
    ) = MemberConsent(member, purpose, version, ConsentSource.REGISTRATION, now)

    @Test
    fun `findStanding returns only grants that have not ended`() {
        val member = member()
        val old = repository.saveAndFlush(grant(member, version = "v1"))
        old.end(ConsentEndReason.SUPERSEDED, now)
        repository.saveAndFlush(old)
        repository.saveAndFlush(grant(member, version = "v2"))
        repository.saveAndFlush(grant(member, ConsentPurpose.COURSE_APPLICATION))
        entityManager.clear()

        val standing = repository.findStanding(member.id!!)

        assertEquals(2, standing.size)
        assertEquals("v2", repository.findStanding(member.id!!, ConsentPurpose.APP_PRIVACY)?.policyVersion)
    }

    @Test
    fun `findStanding by purpose is null after withdrawal`() {
        val member = member()
        val consent = repository.saveAndFlush(grant(member))
        consent.end(ConsentEndReason.WITHDRAWN, now)
        repository.saveAndFlush(consent)
        entityManager.clear()

        assertNull(repository.findStanding(member.id!!, ConsentPurpose.APP_PRIVACY))
    }

    @Test
    fun `the database refuses a second standing grant for the same purpose`() {
        val member = member()
        repository.saveAndFlush(grant(member, version = "v1"))

        assertThrows<DataIntegrityViolationException> {
            repository.saveAndFlush(grant(member, version = "v2"))
        }
    }
}
