package com.hanmaum.dn.app.common.cleanup

import com.hanmaum.dn.app.features.announcements.repository.AnnouncementRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceLogRepository
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
) {
    private val log = LoggerFactory.getLogger(CleanupService::class.java)

    @Scheduled(cron = "\${app.cleanup.cron:0 0 2 * * *}")
    fun purgeExpiredSoftDeletedEntries() {
        val now = Instant.now()
        log.info("Cleanup job started cutoff={}", now)

        purge("MinistryAssignment") {
            ministryAssignmentRepository.hardDeleteExpired(now)
        }
        purge("Announcement") {
            // Announcements use delete_entry_at = admin click time; purge 30 days after.
            announcementRepository.hardDeleteExpired(now.minus(30, ChronoUnit.DAYS))
        }
        purge("AttendanceLog") {
            attendanceLogRepository.hardDeleteExpired(now)
        }

        log.info("Cleanup job finished")
    }

    private fun purge(
        entity: String,
        block: () -> Int,
    ) {
        try {
            val count = block()
            log.info("Purged expired soft-deleted entries entity={} count={}", entity, count)
        } catch (ex: Exception) {
            log.error("Cleanup failed entity={} error={}", entity, ex.message, ex)
        }
    }
}
