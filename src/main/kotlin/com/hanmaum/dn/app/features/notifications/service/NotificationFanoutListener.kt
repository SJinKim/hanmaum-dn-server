package com.hanmaum.dn.app.features.notifications.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.common.observability.OperationOutcome
import com.hanmaum.dn.app.common.observability.OperationalMetrics
import com.hanmaum.dn.app.features.announcements.service.AnnouncementCreatedEvent
import com.hanmaum.dn.app.features.events.repository.EventRsvpLogRepository
import com.hanmaum.dn.app.features.events.service.EventRsvpScheduleChangedEvent
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
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private const val PUSH_TITLE = "새로운 소식이 있습니다!"
private const val EVENT_SCHEDULE_CHANGED_TITLE = "일정이 변경되었습니다"
private val EVENT_SCHEDULE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm XXX")

@Component
class NotificationFanoutListener(
    private val memberRepository: MemberRepository,
    private val notificationRepository: AppNotificationRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val pushSender: PushSender,
    private val operationalMetrics: OperationalMetrics,
    private val eventRsvpLogRepository: EventRsvpLogRepository,
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

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onEventRsvpScheduleChanged(event: EventRsvpScheduleChangedEvent) {
        val startedAt = System.nanoTime()
        try {
            val recipients = eventRsvpLogRepository.findScheduleChangeRecipients(event.eventRsvpId)
            if (recipients.isEmpty()) {
                operationalMetrics.recordNotificationFanout(
                    OperationOutcome.SUCCESS,
                    System.nanoTime() - startedAt,
                )
                return
            }

            val body = scheduleChangeBody(event)
            val rows =
                recipients.map { response ->
                    AppNotification(
                        member = response.member,
                        type = NotificationType.EVENT,
                        title = EVENT_SCHEDULE_CHANGED_TITLE,
                        body = body,
                        referenceType = NotificationReferenceType.EVENT,
                        referencePublicId = event.eventPublicId,
                    )
                }
            val saved = notificationRepository.saveAll(rows)
            val notificationIdByMember = saved.associate { it.member.id!! to it.publicId }

            val pushRecipients = recipients.map { it.member }.filter { it.pushEnabled }
            val tokensByMember =
                deviceTokenRepository
                    .findAllByMemberIdIn(pushRecipients.map { it.id!! })
                    .groupBy { it.member.id!! }

            val deadTokens = mutableListOf<String>()
            for (member in pushRecipients) {
                val tokens = tokensByMember[member.id]?.map { it.token } ?: continue
                val badge = notificationRepository.countByMemberIdAndSeenAtIsNull(member.id!!).toInt()
                val data =
                    mapOf(
                        "type" to NotificationType.EVENT.name,
                        "referenceType" to NotificationReferenceType.EVENT.name,
                        "referencePublicId" to event.eventPublicId.toString(),
                        "notificationPublicId" to notificationIdByMember[member.id]!!.toString(),
                    )
                deadTokens += pushSender.send(tokens, EVENT_SCHEDULE_CHANGED_TITLE, body, data, badge)
            }
            if (deadTokens.isNotEmpty()) deviceTokenRepository.deleteAllByTokenIn(deadTokens)
            operationalMetrics.recordNotificationFanout(
                OperationOutcome.SUCCESS,
                System.nanoTime() - startedAt,
            )
            log
                .atInfo()
                .addKeyValue("event.action", "notification.event_schedule_change")
                .addKeyValue("event.outcome", "success")
                .addKeyValue("event.public_id", event.eventPublicId)
                .addKeyValue("recipient_count", recipients.size)
                .addKeyValue("push_recipient_count", pushRecipients.size)
                .addKeyValue("invalid_token_count", deadTokens.size)
                .log("Event schedule change notification completed")
        } catch (e: RuntimeException) {
            operationalMetrics.recordNotificationFanout(
                OperationOutcome.FAILURE,
                System.nanoTime() - startedAt,
            )
            log
                .atError()
                .addKeyValue("event.action", "notification.event_schedule_change")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("event.public_id", event.eventPublicId)
                .addKeyValue("error.type", e::class.simpleName ?: "RuntimeException")
                .log("Event schedule change notification failed; exception is rethrown to the async handler")
            throw e
        }
    }

    private fun scheduleChangeBody(event: EventRsvpScheduleChangedEvent): String =
        "${event.eventTitle} 일정이 변경되었습니다.\n" +
            "기존: ${format(event.previousWindowStart)} ~ ${format(event.previousWindowEnd)}\n" +
            "변경: ${format(event.currentWindowStart)} ~ ${format(event.currentWindowEnd)}"

    private fun format(time: OffsetDateTime): String = time.format(EVENT_SCHEDULE_TIME_FORMAT)
}
