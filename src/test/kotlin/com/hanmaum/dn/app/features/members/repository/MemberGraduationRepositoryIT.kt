package com.hanmaum.dn.app.features.members.repository

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.members.domain.GraduationReason
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.domain.MemberGraduation
import jakarta.persistence.EntityManager
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.LocalDate

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class MemberGraduationRepositoryIT {
    @Autowired lateinit var repository: MemberGraduationRepository

    @Autowired lateinit var entityManager: EntityManager

    private fun persistedMember(): Member {
        val member = Member(lastName = "김", firstName = "철수")
        entityManager.persist(member)
        entityManager.flush()
        return member
    }

    private fun graduation(
        member: Member,
        graduatedOn: LocalDate = LocalDate.of(2026, 5, 1),
    ) = MemberGraduation(
        member = member,
        graduatedOn = graduatedOn,
        reason = GraduationReason.MARRIAGE,
        graduatedBy = "admin-sub-1",
        previousMemberStatus = MemberStatus.ACTIVE,
    )

    @Test
    fun `a second open graduation for the same member is rejected`() {
        val member = persistedMember()
        entityManager.persist(graduation(member))
        entityManager.flush()

        // persist() is inside the assertion, not just flush(): BaseEntity generates ids
        // with GenerationType.IDENTITY, so Hibernate runs the INSERT immediately to read
        // the generated key back and the constraint fires there rather than at flush.
        assertThrows<ConstraintViolationException> {
            entityManager.persist(graduation(member, LocalDate.of(2026, 6, 1)))
            entityManager.flush()
        }
    }

    @Test
    fun `a reverted graduation does not block a new one`() {
        val member = persistedMember()
        val first = graduation(member)
        first.revertedAt = Instant.now()
        first.revertedBy = "admin-sub-2"
        entityManager.persist(first)
        entityManager.flush()

        entityManager.persist(graduation(member, LocalDate.of(2026, 6, 1)))
        entityManager.flush()

        val open = repository.findOpenByMemberId(member.id!!)
        assertNotNull(open)
        assertEquals(LocalDate.of(2026, 6, 1), open!!.graduatedOn)
    }

    @Test
    fun `history returns reverted and open graduations most recent first`() {
        val member = persistedMember()
        val old = graduation(member, LocalDate.of(2025, 1, 1))
        old.revertedAt = Instant.now()
        old.revertedBy = "admin-sub-2"
        entityManager.persist(old)
        entityManager.persist(graduation(member, LocalDate.of(2026, 6, 1)))
        entityManager.flush()

        val history = repository.findAllByMemberIdOrderByGraduatedOnDesc(member.id!!)

        assertEquals(2, history.size)
        assertEquals(LocalDate.of(2026, 6, 1), history[0].graduatedOn)
        assertEquals(LocalDate.of(2025, 1, 1), history[1].graduatedOn)
    }

    @Test
    fun `open graduation lookup returns null when the member never graduated`() {
        val member = persistedMember()

        assertNull(repository.findOpenByMemberId(member.id!!))
    }

    @Test
    fun `batched lookup returns one row per member with an open graduation`() {
        val graduated = persistedMember()
        val current = persistedMember()
        entityManager.persist(graduation(graduated))
        entityManager.flush()

        val views = repository.findOpenByMemberIds(listOf(graduated.id!!, current.id!!))

        assertEquals(1, views.size)
        assertEquals(graduated.id, views[0].memberId)
        assertEquals(LocalDate.of(2026, 5, 1), views[0].graduatedOn)
    }
}
