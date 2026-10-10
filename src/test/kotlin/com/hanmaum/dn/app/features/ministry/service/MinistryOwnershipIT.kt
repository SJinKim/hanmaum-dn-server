package com.hanmaum.dn.app.features.ministry.service

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.CurrentMemberResolver
import com.hanmaum.dn.app.features.members.service.MemberProfileNotFoundException
import com.hanmaum.dn.app.features.ministry.domain.Ministry
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignment
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignmentRole
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignmentStatus
import com.hanmaum.dn.app.features.ministry.repository.MinistryAssignmentRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate

/**
 * Ownership against the real `findCurrentByMinistryIds` query. The unit test mocks that query,
 * so only this one notices when ended, PENDING or soft-deleted rows start counting as the leader.
 *
 * The resolver is a stand-in that does the same keycloak lookup as the real one, without the
 * legacy-account healing that would need the newcomer beans. JPA slice; needs the test DB
 * (see -PincludeIntegration).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class MinistryOwnershipIT {
    @Autowired lateinit var members: MemberRepository

    @Autowired lateinit var assignments: MinistryAssignmentRepository

    @PersistenceContext lateinit var em: EntityManager

    private fun ownership(): MinistryOwnership {
        val resolver = mock(CurrentMemberResolver::class.java)
        `when`(resolver.require(anyString())).thenAnswer { invocation ->
            members.findByKeycloakIdAndDeletedAtIsNull(invocation.getArgument(0)) ?: throw MemberProfileNotFoundException()
        }
        return MinistryOwnership(assignments, resolver)
    }

    /** One ministry with one LEADER row for a member logged in as [sub]. */
    private fun ministryLedBy(
        sub: String,
        status: MinistryAssignmentStatus = MinistryAssignmentStatus.ACTIVE,
        endDate: LocalDate? = null,
        assignmentDeleted: Boolean = false,
        memberDeleted: Boolean = false,
    ): Ministry {
        val ministry = Ministry(name = "사역 $sub", shortDescription = "사역", longDescription = "사역")
        val leader =
            Member(lastName = "김", firstName = "리더").apply {
                keycloakId = sub
                if (memberDeleted) deletedAt = Instant.now()
            }
        val assignment =
            MinistryAssignment(
                ministry = ministry,
                member = leader,
                startDate = LocalDate.of(2026, 1, 1),
                role = MinistryAssignmentRole.LEADER,
                status = status,
                endDate = endDate,
            ).apply { if (assignmentDeleted) deletedAt = Instant.now() }
        listOf(ministry, leader, assignment).forEach(em::persist)
        em.flush()
        em.clear()
        return ministry
    }

    private fun assertDenied(
        ministry: Ministry,
        sub: String,
    ) {
        val before = assignments.count()
        val ex = assertThrows<ResponseStatusException> { ownership().requireLeader(ministry, sub, isAdmin = false) }
        assertEquals(403, ex.statusCode.value())
        assertEquals(before, assignments.count())
    }

    @Test
    fun `the active leader passes`() {
        val ministry = ministryLedBy("owner-active")
        assertDoesNotThrow { ownership().requireLeader(ministry, "owner-active", isAdmin = false) }
    }

    @Test
    fun `an ended leader assignment no longer owns the ministry`() {
        assertDenied(ministryLedBy("owner-ended", endDate = LocalDate.of(2026, 6, 1)), "owner-ended")
    }

    @Test
    fun `a PENDING leader assignment does not own the ministry yet`() {
        assertDenied(ministryLedBy("owner-pending", status = MinistryAssignmentStatus.PENDING), "owner-pending")
    }

    @Test
    fun `a soft-deleted leader assignment does not own the ministry`() {
        assertDenied(ministryLedBy("owner-deleted-row", assignmentDeleted = true), "owner-deleted-row")
    }

    @Test
    fun `a leader whose member profile is soft-deleted gets 403, not 404`() {
        assertDenied(ministryLedBy("owner-deleted-member", memberDeleted = true), "owner-deleted-member")
    }
}
