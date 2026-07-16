package com.hanmaum.dn.app.features.notifications.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.service.MemberService
import com.hanmaum.dn.app.features.notifications.domain.AppNotification
import com.hanmaum.dn.app.features.notifications.domain.DevicePlatform
import com.hanmaum.dn.app.features.notifications.domain.DeviceToken
import com.hanmaum.dn.app.features.notifications.domain.NotificationType
import com.hanmaum.dn.app.features.notifications.repository.AppNotificationRepository
import com.hanmaum.dn.app.features.notifications.repository.DeviceTokenRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class NotificationServiceTest {
    @Mock
    private lateinit var memberService: MemberService

    @Mock
    private lateinit var notificationRepository: AppNotificationRepository

    @Mock
    private lateinit var deviceTokenRepository: DeviceTokenRepository

    @InjectMocks
    private lateinit var service: NotificationService

    private fun member(id: Long = 1L): Member =
        Member(lastName = "김", firstName = "성진").also {
            it.id = id
            it.memberStatus = MemberStatus.ACTIVE
        }

    @Test
    fun `markRead sets readAt and seenAt on own notification`() {
        val m = member()
        `when`(memberService.resolveMember("sub", null)).thenReturn(m)
        val n = AppNotification(m, NotificationType.ANNOUNCEMENT, "t", "b")
        `when`(notificationRepository.findByPublicIdAndMemberId(n.publicId, 1L)).thenReturn(n)

        service.markRead("sub", null, n.publicId)

        assertNotNull(n.readAt)
        assertNotNull(n.seenAt)
    }

    @Test
    fun `markRead throws for another members notification`() {
        `when`(memberService.resolveMember("sub", null)).thenReturn(member())
        val foreignId = UUID.randomUUID()
        `when`(notificationRepository.findByPublicIdAndMemberId(foreignId, 1L)).thenReturn(null)

        assertThrows(NoSuchElementException::class.java) { service.markRead("sub", null, foreignId) }
    }

    @Test
    fun `markRead does not overwrite existing seenAt`() {
        val m = member()
        `when`(memberService.resolveMember("sub", null)).thenReturn(m)
        val n = AppNotification(m, NotificationType.ANNOUNCEMENT, "t", "b")
        val seen = Instant.parse("2026-07-01T00:00:00Z")
        n.seenAt = seen
        `when`(notificationRepository.findByPublicIdAndMemberId(n.publicId, 1L)).thenReturn(n)

        service.markRead("sub", null, n.publicId)

        assertEquals(seen, n.seenAt)
    }

    @Test
    fun `registerDeviceToken reassigns existing token to caller`() {
        val caller = member(1L)
        val other = member(2L)
        `when`(memberService.resolveMember("sub", null)).thenReturn(caller)
        val existing = DeviceToken(other, "tok", DevicePlatform.ANDROID)
        `when`(deviceTokenRepository.findByToken("tok")).thenReturn(existing)

        service.registerDeviceToken("sub", null, "tok", DevicePlatform.IOS)

        assertEquals(caller, existing.member)
        assertEquals(DevicePlatform.IOS, existing.platform)
        verify(deviceTokenRepository, never()).save(any())
    }

    @Test
    fun `registerDeviceToken saves new token`() {
        `when`(memberService.resolveMember("sub", null)).thenReturn(member())
        `when`(deviceTokenRepository.findByToken("tok")).thenReturn(null)
        `when`(deviceTokenRepository.save(any<DeviceToken>())).thenAnswer { it.arguments[0] }

        service.registerDeviceToken("sub", null, "tok", DevicePlatform.ANDROID)

        val captor = argumentCaptor<DeviceToken>()
        verify(deviceTokenRepository).save(captor.capture())
        assertEquals("tok", captor.firstValue.token)
    }

    @Test
    fun `setPushEnabled flips the member flag`() {
        val m = member()
        `when`(memberService.resolveMember("sub", null)).thenReturn(m)

        service.setPushEnabled("sub", null, false)

        assertEquals(false, m.pushEnabled)
    }

    @Test
    fun `markAllSeen delegates to bulk update`() {
        `when`(memberService.resolveMember("sub", null)).thenReturn(member())

        service.markAllSeen("sub", null)

        verify(notificationRepository).markAllSeen(eq(1L), any())
    }
}
