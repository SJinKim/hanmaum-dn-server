package com.hanmaum.dn.app.features.notifications.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.common.observability.OperationalMetrics
import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberTrainingItem
import com.hanmaum.dn.app.features.members.api.v1.dto.ReplaceMemberTrainingsRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.CurrentMemberResolver
import com.hanmaum.dn.app.features.members.service.MemberService
import com.hanmaum.dn.app.features.notifications.domain.NotificationType
import com.hanmaum.dn.app.features.notifications.repository.AppNotificationRepository
import com.hanmaum.dn.app.features.training.domain.Training
import com.hanmaum.dn.app.features.training.domain.TrainingStatus
import com.hanmaum.dn.app.features.training.domain.UserTraining
import com.hanmaum.dn.app.features.training.repository.TrainingRepository
import com.hanmaum.dn.app.features.training.repository.UserTrainingRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.keycloak.admin.client.Keycloak
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class, MemberService::class, MemberNotificationListener::class)
@Tag("integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MemberNotificationListenerIT {
    @Autowired private lateinit var members: MemberRepository

    @Autowired private lateinit var groups: ChurchGroupRepository

    @Autowired private lateinit var notifications: AppNotificationRepository

    @Autowired private lateinit var service: MemberService

    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    @Autowired private lateinit var courses: TrainingRepository

    @Autowired private lateinit var participations: UserTrainingRepository

    @MockitoBean private lateinit var keycloak: Keycloak

    @MockitoBean private lateinit var resolver: CurrentMemberResolver

    @MockitoBean private lateinit var metrics: OperationalMetrics

    @MockitoBean private lateinit var push: PushSender

    private val createdMembers = mutableListOf<Long>()
    private val createdGroups = mutableListOf<Long>()

    @AfterEach
    fun cleanUpOwnFixtures() {
        TransactionTemplate(transactionManager).executeWithoutResult {
            createdMembers.forEach { memberId ->
                notifications.deleteAllByMemberId(memberId)
                participations.deleteByMemberId(memberId)
                members.deleteById(memberId)
            }
            createdGroups.forEach(groups::deleteById)
        }
    }

    @Test
    fun `course acceptance commits one notification per course and retries do not duplicate it`() {
        val member = pendingMember()
        val applied = appliedCourses(member)
        val transaction = TransactionTemplate(transactionManager)
        val request = ReplaceMemberTrainingsRequest(applied.map { MemberTrainingItem(it.publicId.toString(), "ENROLLED") })

        repeat(2) {
            transaction.executeWithoutResult { service.replaceMemberTrainings(member.publicId, request) }
        }

        val rows = notifications.findAllByMemberIdOrderByCreatedAtDesc(member.id!!, PageRequest.of(0, 10))
        assertEquals(2L, rows.totalElements)
        assertEquals(applied.map { it.publicId }.toSet(), rows.content.map { it.referencePublicId }.toSet())
        rows.forEach { assertEquals(NotificationType.TRAINING, it.type) }
        assertEquals(listOf(TrainingStatus.ENROLLED, TrainingStatus.ENROLLED), participations.findByMemberId(member.id!!).map { it.status })
    }

    @Test
    fun `rolled back course acceptance retains applied status without notifications`() {
        val member = pendingMember()
        val applied = appliedCourses(member)

        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            service.replaceMemberTrainings(
                member.publicId,
                ReplaceMemberTrainingsRequest(applied.map { MemberTrainingItem(it.publicId.toString(), "ENROLLED") }),
            )
            status.setRollbackOnly()
        }

        assertEquals(0L, notifications.countByMemberIdAndSeenAtIsNull(member.id!!))
        assertEquals(listOf(TrainingStatus.APPLIED, TrainingStatus.APPLIED), participations.findByMemberId(member.id!!).map { it.status })
        verifyNoInteractions(push)
    }

    private fun appliedCourses(member: Member): List<Training> =
        requireNotNull(
            TransactionTemplate(transactionManager).execute {
                val applied = courses.findAllByDeletedAtIsNullOrderBySortOrderAsc().take(2)
                check(applied.size == 2)
                participations.saveAllAndFlush(applied.map { UserTraining(member, it, TrainingStatus.APPLIED) })
                applied
            },
        )

    @Test
    fun `approval commit persists one notification and repeated approval does not duplicate it`() {
        val member = pendingMember()
        val transaction = TransactionTemplate(transactionManager)

        repeat(2) {
            transaction.executeWithoutResult {
                service.updateMember(member.publicId, UpdateMemberRequest(memberStatus = "ACTIVE"))
            }
        }

        val rows = notifications.findAllByMemberIdOrderByCreatedAtDesc(member.id!!, PageRequest.of(0, 10))
        assertEquals(1L, rows.totalElements)
        assertEquals(NotificationType.MEMBER, rows.single().type)
        assertEquals("회원 가입 승인", rows.single().title)
        assertNull(rows.single().referencePublicId)
        assertEquals(MemberStatus.ACTIVE, members.findById(member.id!!).orElseThrow().memberStatus)
        verifyNoInteractions(push)
    }

    @Test
    fun `simultaneous approvals produce a single notification`() {
        val member = pendingMember()
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val approvals =
                (1..2).map {
                    executor.submit {
                        check(start.await(10, TimeUnit.SECONDS))
                        TransactionTemplate(transactionManager).executeWithoutResult {
                            service.updateMember(member.publicId, UpdateMemberRequest(memberStatus = "ACTIVE"))
                        }
                    }
                }
            start.countDown()
            approvals.forEach { it.get(10, TimeUnit.SECONDS) }

            assertEquals(1L, notifications.countByMemberIdAndSeenAtIsNull(member.id!!))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `rolled back approval leaves no notification or push`() {
        val member = pendingMember()

        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            service.updateMember(member.publicId, UpdateMemberRequest(memberStatus = "ACTIVE"))
            status.setRollbackOnly()
        }

        assertEquals(0L, notifications.countByMemberIdAndSeenAtIsNull(member.id!!))
        assertEquals(MemberStatus.PENDING, members.findById(member.id!!).orElseThrow().memberStatus)
        verifyNoInteractions(push)
    }

    private fun pendingMember(): Member =
        requireNotNull(
            TransactionTemplate(transactionManager).execute {
                val group = groups.saveAndFlush(ChurchGroup(name = "Notification test"))
                createdGroups += requireNotNull(group.id)
                members
                    .saveAndFlush(
                        Member(lastName = "Test", firstName = "Member", group = group).apply { pushEnabled = false },
                    ).also { createdMembers += requireNotNull(it.id) }
            },
        )
}
