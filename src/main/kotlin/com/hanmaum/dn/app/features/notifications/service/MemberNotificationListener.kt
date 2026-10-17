package com.hanmaum.dn.app.features.notifications.service

import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.notifications.domain.AppNotification
import com.hanmaum.dn.app.features.notifications.domain.NotificationReferenceType
import com.hanmaum.dn.app.features.notifications.domain.NotificationType
import com.hanmaum.dn.app.features.notifications.repository.AppNotificationRepository
import com.hanmaum.dn.app.features.notifications.repository.DeviceTokenRepository
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.UUID

/** Scalar payload: the committed member is loaded again in the delivery transaction. */
data class MemberNotificationRequested(
    val memberId: Long,
    val type: NotificationType,
    val title: String,
    val body: String,
    val referenceType: NotificationReferenceType,
    val referencePublicId: UUID? = null,
)

@Component
class MemberNotificationListener(
    private val members: MemberRepository,
    private val notifications: AppNotificationRepository,
    private val deviceTokens: DeviceTokenRepository,
    private val pushSender: PushSender,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onNotificationRequested(event: MemberNotificationRequested) {
        try {
            deliver(event)
        } catch (e: DataAccessException) {
            throw IllegalStateException("Member notification delivery failed memberId=${event.memberId} type=${event.type}", e)
        }
    }

    private fun deliver(event: MemberNotificationRequested) {
        // The member may have been purged or soft deleted since the triggering commit.
        val member = members.findById(event.memberId).orElse(null)?.takeIf { it.deletedAt == null } ?: return
        val notification =
            notifications.save(
                AppNotification(
                    member = member,
                    type = event.type,
                    title = event.title,
                    body = event.body,
                    referenceType = event.referenceType,
                    referencePublicId = event.referencePublicId,
                ),
            )
        if (!member.pushEnabled) return
        val tokens = deviceTokens.findAllByMemberIdIn(listOf(event.memberId)).map { it.token }
        if (tokens.isEmpty()) return
        val data =
            buildMap {
                put("type", event.type.name)
                put("referenceType", event.referenceType.name)
                event.referencePublicId?.let { put("referencePublicId", it.toString()) }
                put("notificationPublicId", notification.publicId.toString())
            }
        val badge = notifications.countByMemberIdAndSeenAtIsNull(event.memberId).toInt()
        val invalidTokens = pushSender.send(tokens, event.title, event.body, data, badge)
        if (invalidTokens.isNotEmpty()) deviceTokens.deleteAllByTokenIn(invalidTokens)
    }
}
