package com.hanmaum.dn.app.features.groups.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.common.observability.OperationalMetrics
import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.groups.api.v1.dto.AssignGroupLeaderRequest
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.groups.repository.GroupLeaderRepository
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.CurrentMemberResolver
import com.hanmaum.dn.app.features.members.service.MemberService
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.keycloak.admin.client.Keycloak
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class, TrainingMatrixService::class, MemberService::class, ChurchGroupService::class)
@Tag("integration")
class TrainingMatrixServiceIT {
    @Autowired private lateinit var service: TrainingMatrixService

    @Autowired private lateinit var members: MemberRepository

    @Autowired private lateinit var entityManager: EntityManager

    @Autowired private lateinit var memberService: MemberService

    @Autowired private lateinit var groupService: ChurchGroupService

    @Autowired private lateinit var groups: ChurchGroupRepository

    @Autowired private lateinit var leaders: GroupLeaderRepository

    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    @MockitoBean private lateinit var keycloak: Keycloak

    @MockitoBean private lateinit var currentMemberResolver: CurrentMemberResolver

    @MockitoBean private lateinit var operationalMetrics: OperationalMetrics

    @Test
    fun `setting and clearing the flag persists without changing contact or other flags`() {
        val member =
            members.saveAndFlush(
                Member(lastName = "김", firstName = "테스트", email = "matrix-test@example.org", memberStatus = MemberStatus.ACTIVE)
                    .also { it.oneOnOneSignupFilled = true },
            )
        for (value in listOf(true, false)) {
            val response = service.setNextGroupLeader(member.publicId, value)
            members.flush()
            entityManager.clear()
            val saved = members.findByPublicIdAndDeletedAtIsNull(member.publicId).orElseThrow()
            assertEquals(value, response.isNextGroupLeader)
            assertEquals(value, saved.isNextGroupLeader)
            assertEquals("matrix-test@example.org", saved.email)
            assertEquals(true, saved.oneOnOneSignupFilled)
            assertEquals(MemberStatus.ACTIVE, saved.memberStatus)
        }
    }

    @Test
    fun `soft deleted and pending members cannot be changed through the scoped flag API`() {
        for (member in listOf(
            Member(lastName = "김", firstName = "테스트", memberStatus = MemberStatus.ACTIVE).also { it.deletedAt = Instant.now() },
            Member(lastName = "김", firstName = "테스트", memberStatus = MemberStatus.PENDING),
        )) {
            members.saveAndFlush(member)
            entityManager.clear()
            assertThrows<EntityNotFoundException> { service.setNextGroupLeader(member.publicId, true) }
            entityManager.clear()
            // JPQL bypasses the deletedAt filter and sees any eagerly flushed/targeted update.
            val persisted =
                entityManager
                    .createQuery(
                        "select m.isNextGroupLeader from Member m where m.publicId = :publicId",
                        java.lang.Boolean::class.java,
                    ).setParameter("publicId", member.publicId)
                    .singleResult
            assertFalse(persisted.booleanValue())
        }
    }

    @Test
    fun `non-active group members cannot acquire a leader tenure`() {
        val group = groups.saveAndFlush(ChurchGroup(name = "matrix-test"))
        for (status in listOf(MemberStatus.PENDING, MemberStatus.INACTIVE)) {
            val member =
                members.saveAndFlush(
                    Member(lastName = "김", firstName = "테스트", memberStatus = status).also { it.group = group },
                )
            val error =
                assertThrows<ResponseStatusException> {
                    groupService.assignLeader(group.publicId, AssignGroupLeaderRequest(member.publicId.toString()))
                }
            assertEquals(400, error.statusCode.value())
            entityManager.clear()
            assertEquals(null, leaders.findActiveByGroupId(requireNotNull(group.id)))
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `concurrent admin contact edit preserves the training flag after waiting for its transaction`() {
        val member =
            members.saveAndFlush(
                Member(lastName = "김", firstName = "테스트", email = "concurrent-matrix@example.org", memberStatus = MemberStatus.ACTIVE),
            )
        val attempted = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val adminUpdate =
                TransactionTemplate(transactionManager).execute {
                    service.setNextGroupLeader(member.publicId, true)
                    members.flush()
                    val future =
                        executor.submit {
                            attempted.countDown()
                            memberService.updateMember(member.publicId, UpdateMemberRequest(city = "서울"))
                        }
                    assertTrue(attempted.await(5, TimeUnit.SECONDS))
                    // Even if the admin loaded the old flag, its UPDATE must touch only the changed contact field.
                    assertThrows<TimeoutException> { future.get(250, TimeUnit.MILLISECONDS) }
                    future
                }!!
            adminUpdate.get(10, TimeUnit.SECONDS)
            val persisted = members.findByPublicIdAndDeletedAtIsNull(member.publicId).orElseThrow()
            assertTrue(persisted.isNextGroupLeader)
            assertEquals("서울", persisted.city)
            assertEquals("concurrent-matrix@example.org", persisted.email)
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
            members.deleteById(requireNotNull(member.id))
        }
    }
}
