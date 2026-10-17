package com.hanmaum.dn.app.features.notifications.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.common.observability.OperationalMetrics
import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.CurrentMemberResolver
import com.hanmaum.dn.app.features.members.service.MemberService
import com.hanmaum.dn.app.features.notifications.domain.NotificationType
import com.hanmaum.dn.app.features.notifications.repository.AppNotificationRepository
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
                members.deleteById(memberId)
            }
            createdGroups.forEach(groups::deleteById)
        }
    }

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
