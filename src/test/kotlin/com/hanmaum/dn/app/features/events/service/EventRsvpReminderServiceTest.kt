package com.hanmaum.dn.app.features.events.service

import com.hanmaum.dn.app.common.observability.OperationOutcome
import com.hanmaum.dn.app.common.observability.OperationalMetrics
import com.hanmaum.dn.app.features.events.config.RsvpProperties
import com.hanmaum.dn.app.features.events.domain.EventRsvp
import com.hanmaum.dn.app.features.events.domain.EventRsvpLog
import com.hanmaum.dn.app.features.events.domain.RsvpStatus
import com.hanmaum.dn.app.features.events.repository.EventRsvpLogRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.notifications.service.NotificationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

@ExtendWith(MockitoExtension::class)
class EventRsvpReminderServiceTest {
    @Mock private lateinit var eventRsvpLogRepository: EventRsvpLogRepository

    @Mock private lateinit var notificationService: NotificationService

    @Mock private lateinit var operationalMetrics: OperationalMetrics

    private val eventEnd = OffsetDateTime.of(2026, 9, 30, 9, 0, 0, 0, ZoneOffset.UTC)

    @Test
    fun `one due offset sends exactly one reminder`() {
        val now = eventEnd.minusDays(6)
        val response = response(status = RsvpStatus.MAYBE)
        `when`(eventRsvpLogRepository.findReminderCandidates(now)).thenReturn(listOf(response))

        service(now, listOf(Duration.ofDays(7))).sendDueReminders()

        verify(notificationService).sendRsvpReminder(response.member, response.eventRsvp.publicId, response.eventRsvp.title)
        assertEquals(1, response.reminderCount)
        assertEquals(now.toInstant(), response.lastRemindedAt)
        verify(operationalMetrics).recordBackgroundJob(eq("rsvp-reminder"), eq(OperationOutcome.SUCCESS), any())
    }

    @Test
    fun `expanding to three offsets sends only the two newly due reminders`() {
        val response = response(status = RsvpStatus.MAYBE)
        val firstDueAt = eventEnd.minusDays(14)
        `when`(eventRsvpLogRepository.findReminderCandidates(firstDueAt)).thenReturn(listOf(response))
        service(firstDueAt, listOf(Duration.ofDays(14))).sendDueReminders()
        assertEquals(1, response.reminderCount)
        clearInvocations(notificationService)

        val expandedOffsets = listOf(Duration.ofDays(14), Duration.ofDays(7), Duration.ofDays(2))
        val secondDueAt = eventEnd.minusDays(7)
        `when`(eventRsvpLogRepository.findReminderCandidates(secondDueAt)).thenReturn(listOf(response))
        service(secondDueAt, expandedOffsets).sendDueReminders()
        val thirdDueAt = eventEnd.minusDays(2)
        `when`(eventRsvpLogRepository.findReminderCandidates(thirdDueAt)).thenReturn(listOf(response))
        service(thirdDueAt, expandedOffsets).sendDueReminders()

        verify(notificationService, times(2))
            .sendRsvpReminder(response.member, response.eventRsvp.publicId, response.eventRsvp.title)
        assertEquals(3, response.reminderCount)
        assertEquals(thirdDueAt.toInstant(), response.lastRemindedAt)
    }

    @Test
    fun `running twice at the same time does not send a duplicate reminder`() {
        val now = eventEnd.minusDays(6)
        val response = response(status = RsvpStatus.MAYBE)
        `when`(eventRsvpLogRepository.findReminderCandidates(now)).thenReturn(listOf(response))
        val service = service(now, listOf(Duration.ofDays(7)))

        service.sendDueReminders()
        service.sendDueReminders()

        verify(notificationService, times(1))
            .sendRsvpReminder(response.member, response.eventRsvp.publicId, response.eventRsvp.title)
        assertEquals(1, response.reminderCount)
    }

    @Test
    fun `going and not going responses are never reminded`() {
        val now = eventEnd.minusDays(1)
        val going = response(status = RsvpStatus.GOING)
        val notGoing = response(status = RsvpStatus.NOT_GOING)
        `when`(eventRsvpLogRepository.findReminderCandidates(now)).thenReturn(listOf(going, notGoing))

        service(now, listOf(Duration.ofDays(7))).sendDueReminders()

        verify(notificationService, never()).sendRsvpReminder(any(), any(), any())
        assertEquals(0, going.reminderCount)
        assertEquals(0, notGoing.reminderCount)
    }

    @Test
    fun `expired response window is skipped`() {
        val now = eventEnd.plusMinutes(1)
        val response = response(status = RsvpStatus.MAYBE)
        `when`(eventRsvpLogRepository.findReminderCandidates(now)).thenReturn(listOf(response))

        service(now, listOf(Duration.ofDays(7))).sendDueReminders()

        verify(notificationService, never()).sendRsvpReminder(any(), any(), any())
        assertEquals(0, response.reminderCount)
        assertNull(response.lastRemindedAt)
    }

    @Test
    fun `members without a response are not reminded`() {
        val now = eventEnd.minusDays(1)
        `when`(eventRsvpLogRepository.findReminderCandidates(now)).thenReturn(emptyList())

        service(now, listOf(Duration.ofDays(7))).sendDueReminders()

        verify(notificationService, never()).sendRsvpReminder(any(), any(), any())
    }

    private fun service(
        now: OffsetDateTime,
        offsets: List<Duration>,
    ) = EventRsvpReminderService(
        eventRsvpLogRepository = eventRsvpLogRepository,
        notificationService = notificationService,
        rsvpProperties = RsvpProperties(offsets),
        clock = Clock.fixed(now.toInstant(), ZoneOffset.UTC),
        operationalMetrics = operationalMetrics,
    )

    private fun response(status: RsvpStatus): EventRsvpLog {
        val member = Member(lastName = "김", firstName = "철수").also { it.id = 1L }
        val eventRsvp =
            EventRsvp(
                title = "가을 수련회",
                windowStart = eventEnd.minusDays(30),
                windowEnd = eventEnd,
            ).also { it.id = 2L }
        return EventRsvpLog(
            eventRsvp = eventRsvp,
            member = member,
            checkedInAt = eventEnd.minusDays(20).toInstant(),
            status = status,
        ).also { it.id = 3L }
    }
}
