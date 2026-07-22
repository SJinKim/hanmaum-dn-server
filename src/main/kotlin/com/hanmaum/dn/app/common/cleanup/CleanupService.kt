package com.hanmaum.dn.app.common.cleanup

import com.hanmaum.dn.app.common.observability.OperationOutcome
import com.hanmaum.dn.app.common.observability.OperationalMetrics
import com.hanmaum.dn.app.features.announcements.repository.AnnouncementRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceLogRepository
import com.hanmaum.dn.app.features.members.service.MemberPurgeService
import com.hanmaum.dn.app.features.ministry.repository.MinistryAssignmentRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class CleanupService(
    private val ministryAssignmentRepository: MinistryAssignmentRepository,
    private val announcementRepository: AnnouncementRepository,
    private val attendanceLogRepository: AttendanceLogRepository,
    private val memberPurgeService: MemberPurgeService,
    private val operationalMetrics: OperationalMetrics,
) {
    private val log = LoggerFactory.getLogger(CleanupService::class.java)

    @Scheduled(cron = "\${app.cleanup.cron:0 0 2 * * *}")
    fun purgeExpiredSoftDeletedEntries() {
        val startedAt = System.nanoTime()
        val now = Instant.now()
        val failures = mutableListOf<RuntimeException>()
        log.info("Cleanup job started cutoff={}", now)

        purge("MinistryAssignment", failures) {
            ministryAssignmentRepository.hardDeleteExpired(now)
        }
        purge("Announcement", failures) {
            // Announcements use delete_entry_at = admin click time; purge 30 days after.
            announcementRepository.hardDeleteExpired(now.minus(30, ChronoUnit.DAYS))
        }
        purge("AttendanceLog", failures) {
            attendanceLogRepository.hardDeleteExpired(now)
        }
        purge("Member", failures) {
            memberPurgeService.purgeExpired(now)
        }

        if (failures.isNotEmpty()) {
            operationalMetrics.recordBackgroundJob(
                job = "cleanup",
                outcome = OperationOutcome.FAILURE,
                elapsedNanos = System.nanoTime() - startedAt,
            )
            val exception =
                IllegalStateException(
                    "Cleanup job completed with ${failures.size} failure(s).",
                    failures.first(),
                )
            failures.drop(1).forEach(exception::addSuppressed)
            throw exception
        }

        val elapsedNanos = System.nanoTime() - startedAt
        operationalMetrics.recordBackgroundJob(
            job = "cleanup",
            outcome = OperationOutcome.SUCCESS,
            elapsedNanos = elapsedNanos,
        )
        log.info("Cleanup job finished failures=0 durationMs={}", elapsedNanos / 1_000_000)
    }

    private fun purge(
        entity: String,
        failures: MutableList<RuntimeException>,
        block: () -> Int,
    ) {
        try {
            val count = block()
            log.info("Purged expired soft-deleted entries entity={} count={}", entity, count)
        } catch (ex: RuntimeException) {
            log.error("Cleanup failed entity={} error={}", entity, ex.message, ex)
            failures.add(ex)
        }
    }
}
