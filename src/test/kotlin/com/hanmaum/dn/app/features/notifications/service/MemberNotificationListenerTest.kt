package com.hanmaum.dn.app.features.notifications.service

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.notifications.domain.AppNotification
import com.hanmaum.dn.app.features.notifications.domain.DevicePlatform
import com.hanmaum.dn.app.features.notifications.domain.DeviceToken
import com.hanmaum.dn.app.features.notifications.domain.NotificationReferenceType
import com.hanmaum.dn.app.features.notifications.domain.NotificationType
import com.hanmaum.dn.app.features.notifications.repository.AppNotificationRepository
import com.hanmaum.dn.app.features.notifications.repository.DeviceTokenRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.dao.DataRetrievalFailureException
import java.time.Instant
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MemberNotificationListenerTest {
    private val members = mock<MemberRepository>()
    private val notifications = mock<AppNotificationRepository>()
    private val devices = mock<DeviceTokenRepository>()
    private val push = mock<PushSender>()
    private val listener = MemberNotificationListener(members, notifications, devices, push)
    private val member = Member(lastName = "Test", firstName = "Member").apply { id = 1 }
    private val event = MemberNotificationRequested(1, NotificationType.MEMBER, "회원 가입 승인", "승인되었습니다.", NotificationReferenceType.MEMBER)

    @Test
    fun `push opt out keeps the in app notification`() {
        member.pushEnabled = false
        whenever(members.findById(1)).thenReturn(Optional.of(member))
        whenever(notifications.save(any<AppNotification>())).thenAnswer { it.arguments[0] }

        listener.onNotificationRequested(event)

        val saved = argumentCaptor<AppNotification>()
        verify(notifications).save(saved.capture())
        assertEquals(member, saved.firstValue.member)
        assertEquals(NotificationType.MEMBER, saved.firstValue.type)
        assertEquals(event.title, saved.firstValue.title)
        assertEquals(event.body, saved.firstValue.body)
        assertEquals(NotificationReferenceType.MEMBER, saved.firstValue.referenceType)
        assertNull(saved.firstValue.referencePublicId)
        verifyNoInteractions(devices, push)
    }

    @Test
    fun `push carries the notification identity and badge and removes invalid devices`() {
        member.pushEnabled = true
        whenever(members.findById(1)).thenReturn(Optional.of(member))
        whenever(notifications.save(any<AppNotification>())).thenAnswer { it.arguments[0] }
        whenever(
            devices.findAllByMemberIdIn(listOf(1L)),
        ).thenReturn(listOf(DeviceToken(member, "synthetic-device", DevicePlatform.ANDROID)))
        whenever(notifications.countByMemberIdAndSeenAtIsNull(1)).thenReturn(2)
        whenever(push.send(any(), any(), any(), any(), any())).thenReturn(listOf("synthetic-device"))

        listener.onNotificationRequested(event)

        val saved = argumentCaptor<AppNotification>()
        verify(notifications).save(saved.capture())
        val data = argumentCaptor<Map<String, String>>()
        verify(push).send(eq(listOf("synthetic-device")), eq(event.title), eq(event.body), data.capture(), eq(2))
        assertEquals(saved.firstValue.publicId.toString(), data.firstValue["notificationPublicId"])
        assertEquals("MEMBER", data.firstValue["type"])
        assertEquals("MEMBER", data.firstValue["referenceType"])
        assertFalse(data.firstValue.containsKey("referencePublicId"))
        verify(devices).deleteAllByTokenIn(listOf("synthetic-device"))
    }

    @Test
    fun `deleted member receives no notification`() {
        member.deletedAt = Instant.now()
        whenever(members.findById(1)).thenReturn(Optional.of(member))

        listener.onNotificationRequested(event)

        verifyNoInteractions(notifications, devices, push)
    }

    @Test
    fun `purged member receives no notification`() {
        whenever(members.findById(1)).thenReturn(Optional.empty())

        listener.onNotificationRequested(event)

        verifyNoInteractions(notifications, devices, push)
    }

    @Test
    fun `database failures retain their cause with delivery context`() {
        val failure = DataRetrievalFailureException("synthetic failure")
        whenever(members.findById(1)).thenThrow(failure)

        val error = assertFailsWith<IllegalStateException> { listener.onNotificationRequested(event) }

        assertEquals(failure, error.cause)
        assertEquals("Member notification delivery failed memberId=1 type=MEMBER", error.message)
    }
}
