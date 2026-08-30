package com.hanmaum.dn.app.features.notifications.service

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.service.MemberService
import com.hanmaum.dn.app.features.notifications.domain.AppNotification
import com.hanmaum.dn.app.features.notifications.domain.DevicePlatform
import com.hanmaum.dn.app.features.notifications.domain.DeviceToken
import com.hanmaum.dn.app.features.notifications.domain.NotificationReferenceType
import com.hanmaum.dn.app.features.notifications.domain.NotificationType
import com.hanmaum.dn.app.features.notifications.repository.AppNotificationRepository
import com.hanmaum.dn.app.features.notifications.repository.DeviceTokenRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class NotificationService(
    private val memberService: MemberService,
    private val notificationRepository: AppNotificationRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val pushSender: PushSender,
) {
    private fun caller(
        keycloakSubject: String,
        email: String?,
    ): Member = memberService.resolveMember(keycloakSubject, email)

    fun getNotifications(
        keycloakSubject: String,
        email: String?,
        page: Int,
        size: Int,
    ): Page<AppNotification> {
        val member = caller(keycloakSubject, email)
        return notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(
            member.id!!,
            PageRequest.of(page, size.coerceIn(1, 50)),
        )
    }

    fun getUnseenCount(
        keycloakSubject: String,
        email: String?,
    ): Long = notificationRepository.countByMemberIdAndSeenAtIsNull(caller(keycloakSubject, email).id!!)

    @Transactional
    fun markAllSeen(
        keycloakSubject: String,
        email: String?,
    ) {
        notificationRepository.markAllSeen(caller(keycloakSubject, email).id!!, Instant.now())
    }

    @Transactional
    fun markRead(
        keycloakSubject: String,
        email: String?,
        publicId: UUID,
    ) {
        val member = caller(keycloakSubject, email)
        val notification =
            notificationRepository.findByPublicIdAndMemberId(publicId, member.id!!)
                ?: throw NoSuchElementException("notification not found")
        val now = Instant.now()
        notification.readAt = notification.readAt ?: now
        notification.seenAt = notification.seenAt ?: now
    }

    @Transactional
    fun markAllRead(
        keycloakSubject: String,
        email: String?,
    ) {
        notificationRepository.markAllRead(caller(keycloakSubject, email).id!!, Instant.now())
    }

    @Transactional
    fun deleteNotification(
        keycloakSubject: String,
        email: String?,
        publicId: UUID,
    ) {
        val member = caller(keycloakSubject, email)
        val removed = notificationRepository.deleteByPublicIdAndMemberId(publicId, member.id!!)
        if (removed == 0) throw NoSuchElementException("notification not found")
    }

    @Transactional
    fun deleteAll(
        keycloakSubject: String,
        email: String?,
    ) {
        notificationRepository.deleteAllByMemberId(caller(keycloakSubject, email).id!!)
    }

    @Transactional
    fun registerDeviceToken(
        keycloakSubject: String,
        email: String?,
        token: String,
        platform: DevicePlatform,
    ) {
        val member = caller(keycloakSubject, email)
        val existing = deviceTokenRepository.findByToken(token)
        if (existing != null) {
            existing.member = member
            existing.platform = platform
        } else {
            deviceTokenRepository.save(DeviceToken(member, token, platform))
        }
    }

    @Transactional
    fun deleteDeviceToken(
        keycloakSubject: String,
        email: String?,
        token: String,
    ) {
        val member = caller(keycloakSubject, email)
        deviceTokenRepository.deleteByTokenAndMemberId(token, member.id!!)
    }

    fun getPushEnabled(
        keycloakSubject: String,
        email: String?,
    ): Boolean = caller(keycloakSubject, email).pushEnabled

    @Transactional
    fun setPushEnabled(
        keycloakSubject: String,
        email: String?,
        enabled: Boolean,
    ) {
        caller(keycloakSubject, email).pushEnabled = enabled
    }

    @Transactional
    fun sendRsvpReminder(
        member: Member,
        eventPublicId: UUID,
        eventTitle: String,
    ) {
        val title = "행사 참석 여부를 알려주세요"
        val body = "$eventTitle 참석 여부를 다시 확인해 주세요."
        val notification =
            notificationRepository.save(
                AppNotification(
                    member = member,
                    type = NotificationType.EVENT,
                    title = title,
                    body = body,
                    referenceType = NotificationReferenceType.EVENT,
                    referencePublicId = eventPublicId,
                ),
            )
        if (!member.pushEnabled) {
            return
        }

        val tokens =
            deviceTokenRepository
                .findAllByMemberIdIn(listOf(member.id!!))
                .map(DeviceToken::token)
                .distinct()
        if (tokens.isEmpty()) {
            return
        }
        val badge = notificationRepository.countByMemberIdAndSeenAtIsNull(member.id!!).toInt()
        val data =
            mapOf(
                "type" to NotificationType.EVENT.name,
                "referenceType" to NotificationReferenceType.EVENT.name,
                "referencePublicId" to eventPublicId.toString(),
                "notificationPublicId" to notification.publicId.toString(),
            )
        val deadTokens = pushSender.send(tokens, title, body, data, badge)
        if (deadTokens.isNotEmpty()) {
            deviceTokenRepository.deleteAllByTokenIn(deadTokens)
        }
    }
}
