package com.hanmaum.dn.app.features.notifications.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.common.observability.OperationOutcome
import com.hanmaum.dn.app.common.observability.OperationalMetrics
import com.hanmaum.dn.app.features.announcements.service.AnnouncementCreatedEvent
import com.hanmaum.dn.app.features.events.domain.EventRsvp
import com.hanmaum.dn.app.features.events.domain.EventRsvpLog
import com.hanmaum.dn.app.features.events.repository.EventRsvpLogRepository
import com.hanmaum.dn.app.features.events.service.EventRsvpScheduleChangedEvent
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.notifications.domain.AppNotification
import com.hanmaum.dn.app.features.notifications.domain.DevicePlatform
import com.hanmaum.dn.app.features.notifications.domain.DeviceToken
import com.hanmaum.dn.app.features.notifications.repository.AppNotificationRepository
import com.hanmaum.dn.app.features.notifications.repository.DeviceTokenRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class NotificationFanoutListenerTest {
    @Mock private lateinit var memberRepository: MemberRepository

    @Mock private lateinit var notificationRepository: AppNotificationRepository

    @Mock private lateinit var deviceTokenRepository: DeviceTokenRepository

    @Mock private lateinit var pushSender: PushSender

    @Mock private lateinit var operationalMetrics: OperationalMetrics

    @Mock private lateinit var eventRsvpLogRepository: EventRsvpLogRepository

    @InjectMocks private lateinit var listener: NotificationFanoutListener

    private fun member(
        id: Long,
        push: Boolean = true,
    ): Member =
        Member(lastName = "김", firstName = "m$id").also {
            it.id = id
            it.memberStatus = MemberStatus.ACTIVE
            it.pushEnabled = push
        }

    private fun response(member: Member): EventRsvpLog =
        EventRsvpLog(
            eventRsvp =
                EventRsvp(
                    title = "여름 수련회",
                    windowStart = OffsetDateTime.parse("2026-06-01T10:00:00+09:00"),
                    windowEnd = OffsetDateTime.parse("2026-06-01T12:00:00+09:00"),
                ),
            member = member,
            checkedInAt = Instant.parse("2026-05-01T00:00:00Z"),
        )

    @Test
    fun `writes one row per active member and pushes only to push enabled members`() {
        val withPush = member(1L, push = true)
        val noPush = member(2L, push = false)
        `when`(memberRepository.findAllByMemberStatusAndDeletedAtIsNull(MemberStatus.ACTIVE))
            .thenReturn(listOf(withPush, noPush))
        `when`(notificationRepository.saveAll(any<List<AppNotification>>())).thenAnswer { it.arguments[0] }
        `when`(deviceTokenRepository.findAllByMemberIdIn(eq(listOf(1L))))
            .thenReturn(listOf(DeviceToken(withPush, "tok1", DevicePlatform.ANDROID)))
        `when`(notificationRepository.countByMemberIdAndSeenAtIsNull(1L)).thenReturn(3L)
        `when`(pushSender.send(any(), any(), any(), any(), anyOrNull())).thenReturn(emptyList())

        listener.onAnnouncementCreated(AnnouncementCreatedEvent(UUID.randomUUID(), "여름 수련회"))

        val rows = argumentCaptor<List<AppNotification>>()
        verify(notificationRepository).saveAll(rows.capture())
        assertEquals(2, rows.firstValue.size)
        assertEquals("새로운 소식이 있습니다!", rows.firstValue[0].title)
        assertEquals("여름 수련회", rows.firstValue[0].body)
        verify(pushSender).send(eq(listOf("tok1")), any(), any(), any(), eq(3))
        verify(operationalMetrics).recordNotificationFanout(eq(OperationOutcome.SUCCESS), any())
    }

    @Test
    fun `deletes tokens reported dead by fcm`() {
        val m = member(1L)
        `when`(memberRepository.findAllByMemberStatusAndDeletedAtIsNull(MemberStatus.ACTIVE)).thenReturn(listOf(m))
        `when`(notificationRepository.saveAll(any<List<AppNotification>>())).thenAnswer { it.arguments[0] }
        `when`(deviceTokenRepository.findAllByMemberIdIn(eq(listOf(1L))))
            .thenReturn(listOf(DeviceToken(m, "dead", DevicePlatform.IOS)))
        `when`(notificationRepository.countByMemberIdAndSeenAtIsNull(1L)).thenReturn(1L)
        `when`(pushSender.send(any(), any(), any(), any(), anyOrNull())).thenReturn(listOf("dead"))

        listener.onAnnouncementCreated(AnnouncementCreatedEvent(UUID.randomUUID(), "t"))

        verify(deviceTokenRepository).deleteAllByTokenIn(eq(listOf("dead")))
    }

    @Test
    fun `writes schedule changes for responders and pushes only to enabled recipients`() {
        val withPush = member(1L, push = true)
        val noPush = member(2L, push = false)
        val eventId = UUID.randomUUID()
        `when`(eventRsvpLogRepository.findScheduleChangeRecipients(42L))
            .thenReturn(listOf(response(withPush), response(noPush)))
        `when`(notificationRepository.saveAll(any<List<AppNotification>>())).thenAnswer { it.arguments[0] }
        `when`(deviceTokenRepository.findAllByMemberIdIn(eq(listOf(1L))))
            .thenReturn(listOf(DeviceToken(withPush, "tok1", DevicePlatform.ANDROID)))
        `when`(notificationRepository.countByMemberIdAndSeenAtIsNull(1L)).thenReturn(3L)
        `when`(pushSender.send(any(), any(), any(), any(), anyOrNull())).thenReturn(emptyList())

        listener.onEventRsvpScheduleChanged(
            EventRsvpScheduleChangedEvent(
                eventRsvpId = 42L,
                eventPublicId = eventId,
                eventTitle = "여름 수련회",
                previousWindowStart = OffsetDateTime.parse("2026-06-01T10:00:00+09:00"),
                previousWindowEnd = OffsetDateTime.parse("2026-06-01T12:00:00+09:00"),
                currentWindowStart = OffsetDateTime.parse("2026-06-08T10:00:00+09:00"),
                currentWindowEnd = OffsetDateTime.parse("2026-06-08T12:00:00+09:00"),
            ),
        )

        val rows = argumentCaptor<List<AppNotification>>()
        verify(notificationRepository).saveAll(rows.capture())
        assertEquals(2, rows.firstValue.size)
        assertEquals("일정이 변경되었습니다", rows.firstValue[0].title)
        assertEquals(
            "여름 수련회 일정이 변경되었습니다.\n" +
                "기존: 2026년 6월 1일 10:00 +09:00 ~ 2026년 6월 1일 12:00 +09:00\n" +
                "변경: 2026년 6월 8일 10:00 +09:00 ~ 2026년 6월 8일 12:00 +09:00",
            rows.firstValue[0].body,
        )
        assertEquals(eventId, rows.firstValue[0].referencePublicId)
        verify(pushSender).send(eq(listOf("tok1")), eq("일정이 변경되었습니다"), any(), any(), eq(3))
        verify(operationalMetrics).recordNotificationFanout(eq(OperationOutcome.SUCCESS), any())
    }

    @Test
    fun `records and rethrows fanout failures`() {
        val failure = IllegalStateException("database unavailable")
        `when`(memberRepository.findAllByMemberStatusAndDeletedAtIsNull(MemberStatus.ACTIVE))
            .thenThrow(failure)

        val thrown =
            assertThrows(IllegalStateException::class.java) {
                listener.onAnnouncementCreated(AnnouncementCreatedEvent(UUID.randomUUID(), "t"))
            }

        assertEquals(failure, thrown)
        verify(operationalMetrics).recordNotificationFanout(eq(OperationOutcome.FAILURE), any())
    }
}
