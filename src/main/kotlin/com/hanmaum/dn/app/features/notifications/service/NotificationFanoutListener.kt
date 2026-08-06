package com.hanmaum.dn.app.features.notifications.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.common.observability.OperationOutcome
import com.hanmaum.dn.app.common.observability.OperationalMetrics
import com.hanmaum.dn.app.features.announcements.service.AnnouncementCreatedEvent
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.notifications.domain.AppNotification
import com.hanmaum.dn.app.features.notifications.domain.NotificationReferenceType
import com.hanmaum.dn.app.features.notifications.domain.NotificationType
import com.hanmaum.dn.app.features.notifications.repository.AppNotificationRepository
import com.hanmaum.dn.app.features.notifications.repository.DeviceTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

private const val PUSH_TITLE = "새로운 소식이 있습니다!"

@Component
class NotificationFanoutListener(
    private val memberRepository: MemberRepository,
    private val notificationRepository: AppNotificationRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val pushSender: PushSender,
    private val operationalMetrics: OperationalMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onAnnouncementCreated(event: AnnouncementCreatedEvent) {
        val startedAt = System.nanoTime()
        try {
            val members = memberRepository.findAllByMemberStatusAndDeletedAtIsNull(MemberStatus.ACTIVE)
            if (members.isEmpty()) {
                operationalMetrics.recordNotificationFanout(
                    OperationOutcome.SUCCESS,
                    System.nanoTime() - startedAt,
                )
                return
            }

            val rows =
                members.map { member ->
                    AppNotification(
                        member = member,
                        type = NotificationType.ANNOUNCEMENT,
                        title = PUSH_TITLE,
                        body = event.announcementTitle,
                        referenceType = NotificationReferenceType.ANNOUNCEMENT,
                        referencePublicId = event.announcementPublicId,
                    )
                }
            val saved = notificationRepository.saveAll(rows)
            val notificationIdByMember = saved.associate { it.member.id!! to it.publicId }

            val pushMembers = members.filter { it.pushEnabled }
            val tokensByMember =
                deviceTokenRepository
                    .findAllByMemberIdIn(pushMembers.map { it.id!! })
                    .groupBy { it.member.id!! }

            val deadTokens = mutableListOf<String>()
            for (member in pushMembers) {
                val tokens = tokensByMember[member.id]?.map { it.token } ?: continue
                val badge = notificationRepository.countByMemberIdAndSeenAtIsNull(member.id!!).toInt()
                val data =
                    mapOf(
                        "type" to NotificationType.ANNOUNCEMENT.name,
                        "referenceType" to NotificationReferenceType.ANNOUNCEMENT.name,
                        "referencePublicId" to event.announcementPublicId.toString(),
                        "notificationPublicId" to notificationIdByMember[member.id]!!.toString(),
                    )
                deadTokens += pushSender.send(tokens, PUSH_TITLE, event.announcementTitle, data, badge)
            }
            if (deadTokens.isNotEmpty()) deviceTokenRepository.deleteAllByTokenIn(deadTokens)
            operationalMetrics.recordNotificationFanout(
                OperationOutcome.SUCCESS,
                System.nanoTime() - startedAt,
            )
            log
                .atInfo()
                .addKeyValue("event.action", "notification.fanout")
                .addKeyValue("event.outcome", "success")
                .addKeyValue("member_count", members.size)
                .addKeyValue("push_recipient_count", pushMembers.size)
                .addKeyValue("invalid_token_count", deadTokens.size)
                .log("Notification fan-out completed")
        } catch (e: RuntimeException) {
            operationalMetrics.recordNotificationFanout(
                OperationOutcome.FAILURE,
                System.nanoTime() - startedAt,
            )
            log
                .atError()
                .addKeyValue("event.action", "notification.fanout")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("error.type", e::class.simpleName ?: "RuntimeException")
                .addKeyValue("announcement.public_id", event.announcementPublicId)
                .log("Notification fan-out failed; exception is rethrown to the async handler")
            throw e
        }
    }
}
