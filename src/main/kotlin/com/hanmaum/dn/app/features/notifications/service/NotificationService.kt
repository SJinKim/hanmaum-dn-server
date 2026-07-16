package com.hanmaum.dn.app.features.notifications.service

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.service.MemberService
import com.hanmaum.dn.app.features.notifications.domain.AppNotification
import com.hanmaum.dn.app.features.notifications.domain.DevicePlatform
import com.hanmaum.dn.app.features.notifications.domain.DeviceToken
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
}
