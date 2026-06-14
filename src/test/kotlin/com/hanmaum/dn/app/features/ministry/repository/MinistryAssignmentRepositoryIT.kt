package com.hanmaum.dn.app.features.ministry.repository

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.domain.Ministry
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignment
import com.hanmaum.dn.app.features.ministry.domain.MinistryContact
import com.hanmaum.dn.app.features.ministry.domain.MinistrySchedule
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

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
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class MinistryAssignmentRepositoryIT {
    @Autowired lateinit var members: MemberRepository

    @Autowired lateinit var assignments: MinistryAssignmentRepository

    @PersistenceContext lateinit var em: EntityManager

    @Test
    fun `persists ordered backend-driven ministry details`() {
        val ministry =
            Ministry(
                name = "난민 사역",
                shortDescription = "하나님의 사랑을 나누고 복음을 전합니다.",
                longDescription = "한 달에 한 번 난민 아이들을 섬깁니다.",
            ).also {
                it.replaceRequirements(listOf("첫 번째 자격", "두 번째 자격"))
                it.replaceSchedules(
                    listOf(
                        MinistrySchedule(
                            description = "준비모임",
                            startTime = LocalTime.of(7, 0),
                            endTime = LocalTime.of(9, 0),
                        ),
                        MinistrySchedule(
                            description = "사역",
                            startTime = LocalTime.of(16, 0),
                            endTime = LocalTime.of(18, 0),
                        ),
                    ),
                )
                it.replaceContacts(
                    listOf(
                        MinistryContact(role = "팀장", name = "김영원 권사님"),
                        MinistryContact(role = "간사", name = "최혜령 자매님"),
                    ),
                )
            }
        em.persist(ministry)
        em.flush()
        em.clear()

        val reloaded = em.find(Ministry::class.java, ministry.id)

        assertEquals(listOf("첫 번째 자격", "두 번째 자격"), reloaded.requirements)
        assertEquals(listOf("준비모임", "사역"), reloaded.schedules.map { it.description })
        assertEquals(LocalTime.of(7, 0), reloaded.schedules.first().startTime)
        assertEquals(listOf("팀장", "간사"), reloaded.contacts.map { it.role })
        assertEquals(listOf("김영원 권사님", "최혜령 자매님"), reloaded.contacts.map { it.name })

        reloaded.replaceRequirements(listOf("교체된 자격"))
        reloaded.replaceSchedules(
            listOf(
                MinistrySchedule(
                    description = "교체된 일정",
                    startTime = LocalTime.of(12, 0),
                    endTime = LocalTime.of(14, 0),
                ),
            ),
        )
        reloaded.replaceContacts(listOf(MinistryContact(role = "담당 교역자", name = "새 담당자님")))
        em.flush()
        em.clear()

        val updated = em.find(Ministry::class.java, ministry.id)
        assertEquals(listOf("교체된 자격"), updated.requirements)
        assertEquals(listOf("교체된 일정"), updated.schedules.map { it.description })
        assertEquals(listOf("담당 교역자"), updated.contacts.map { it.role })
        assertEquals(listOf("새 담당자님"), updated.contacts.map { it.name })
    }

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
        val ministry =
            Ministry(
                name = "찬양팀",
                shortDescription = "Worship team",
                longDescription = "Serves through worship.",
            )
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
        val ministry =
            Ministry(
                name = "봉사팀",
                shortDescription = "Service team",
                longDescription = "Serves the church community.",
            )
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
