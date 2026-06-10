package com.hanmaum.dn.app.features.ministry.repository

import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.domain.Ministry
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignment
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.LocalDate

/**
 * Guards against the replace-set delete detaching the member it was loaded with.
 *
 * Regression: `deleteByMemberId` was annotated `@Modifying(clearAutomatically = true)`,
 * which clears the whole persistence context. In `MemberService.replaceMemberMinistries`
 * that detached the already-loaded `member`, so building the response DTO threw
 * `LazyInitializationException` when it read the member's lazy `group`.
 *
 * JPA slice (no Keycloak/web context); needs the test DB (see -PincludeIntegration).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
class MinistryAssignmentRepositoryIT {
    @Autowired lateinit var members: MemberRepository

    @Autowired lateinit var assignments: MinistryAssignmentRepository

    @PersistenceContext lateinit var em: EntityManager

    @Test
    fun `deleteByMemberId keeps the loaded member's lazy group accessible`() {
        val group = ChurchGroup(name = "다니엘조")
        em.persist(group)
        val member = Member(lastName = "김", firstName = "철수").apply { this.group = group }
        em.persist(member)
        em.flush()
        em.clear()

        // Reload so `group` is an uninitialized lazy proxy, as in a real request.
        val reloaded = members.findByPublicIdAndDeletedAtIsNull(member.publicId).orElseThrow()

        // The replace-set flow deletes the member's assignments first; this must NOT clear
        // the persistence context (doing so detaches `reloaded` and its group proxy).
        assignments.deleteByMemberId(reloaded.id!!)

        // Would throw LazyInitializationException if the context was cleared above.
        assertEquals("다니엘조", reloaded.group?.name)
    }

    @Test
    fun `findActiveByMinistryPublicId - excludes soft-deleted members`() {
        val ministry = Ministry(name = "찬양팀", shortDescription = "Worship team")
        em.persist(ministry)

        val activeMember = Member(lastName = "이", firstName = "영희")
        em.persist(activeMember)

        val deletedMember =
            Member(lastName = "박", firstName = "민준").apply {
                deletedAt = Instant.now()
            }
        em.persist(deletedMember)

        val startDate = LocalDate.of(2024, 1, 1)
        em.persist(MinistryAssignment(ministry = ministry, member = activeMember, startDate = startDate))
        em.persist(MinistryAssignment(ministry = ministry, member = deletedMember, startDate = startDate))

        em.flush()
        em.clear()

        val results = assignments.findActiveByMinistryPublicId(ministry.publicId)

        assertEquals(1, results.size)
        assertEquals("이영희", results.single().fullName)
    }

    @Test
    fun `findActiveByMinistryPublicId - excludes soft-deleted assignments`() {
        val ministry = Ministry(name = "봉사팀", shortDescription = "Service team")
        em.persist(ministry)

        val member = Member(lastName = "최", firstName = "지수")
        em.persist(member)

        val startDate = LocalDate.of(2024, 1, 1)
        em.persist(MinistryAssignment(ministry = ministry, member = member, startDate = startDate))
        em.persist(
            MinistryAssignment(ministry = ministry, member = member, startDate = startDate).apply {
                deletedAt = Instant.now()
            },
        )

        em.flush()
        em.clear()

        val results = assignments.findActiveByMinistryPublicId(ministry.publicId)

        assertEquals(1, results.size)
    }
}
