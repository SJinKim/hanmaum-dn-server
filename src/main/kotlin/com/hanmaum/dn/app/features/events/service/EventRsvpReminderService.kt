package com.hanmaum.dn.app.features.events.service

import com.hanmaum.dn.app.common.observability.OperationOutcome
import com.hanmaum.dn.app.common.observability.OperationalMetrics
import com.hanmaum.dn.app.features.events.config.RsvpProperties
import com.hanmaum.dn.app.features.events.domain.RsvpStatus
import com.hanmaum.dn.app.features.events.repository.EventRsvpLogRepository
import com.hanmaum.dn.app.features.notifications.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime

@Service
class EventRsvpReminderService(
    private val eventRsvpLogRepository: EventRsvpLogRepository,
    private val notificationService: NotificationService,
    private val rsvpProperties: RsvpProperties,
    private val clock: Clock,
    private val operationalMetrics: OperationalMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${hanmaum.rsvp.reminder-cron:0 0 9 * * *}")
    @Transactional
    fun sendDueReminders() {
        val startedAt = System.nanoTime()
        try {
            val now = OffsetDateTime.now(clock)
            val candidates = eventRsvpLogRepository.findReminderCandidates(now)
            var sentCount = 0
            candidates.forEach { response ->
                val eventRsvp = response.eventRsvp
                if (
                    response.status != RsvpStatus.MAYBE ||
                    response.deletedAt != null ||
                    !eventRsvp.isActive ||
                    eventRsvp.deletedAt != null ||
                    !eventRsvp.windowEnd.isAfter(now)
                ) {
                    return@forEach
                }
                val dueCount =
                    rsvpProperties.reminderOffsets.count { offset ->
                        !now.isBefore(eventRsvp.windowEnd.minus(offset))
                    }
                if (dueCount <= response.reminderCount) {
                    return@forEach
                }

                notificationService.sendRsvpReminder(
                    member = response.member,
                    eventPublicId = eventRsvp.publicId,
                    eventTitle = eventRsvp.title,
                )
                response.reminderCount = dueCount
                response.lastRemindedAt = now.toInstant()
                sentCount++
            }

            operationalMetrics.recordBackgroundJob(
                job = "rsvp-reminder",
                outcome = OperationOutcome.SUCCESS,
                elapsedNanos = System.nanoTime() - startedAt,
            )
            log.info("RSVP reminder job completed candidateCount={} sentCount={}", candidates.size, sentCount)
        } catch (exception: RuntimeException) {
            operationalMetrics.recordBackgroundJob(
                job = "rsvp-reminder",
                outcome = OperationOutcome.FAILURE,
                elapsedNanos = System.nanoTime() - startedAt,
            )
            log.error("RSVP reminder job failed errorType={}", exception::class.simpleName, exception)
            throw IllegalStateException("RSVP reminder job failed.", exception)
        }
    }
}
