package com.hanmaum.dn.app.features.newcomers.service

import com.hanmaum.dn.app.features.newcomers.repository.NewcomerVisitRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Retention of 방문 기록 (#265). Visits are history for the 새가족 team, but not forever:
 * after `app.newcomer-visits.retention-years` they go, and a soft-deleted visit goes
 * `deleted-retention-days` after its deletion. Called by the nightly cleanup job.
 */
@Service
class NewcomerVisitPurgeService(
    private val visitRepo: NewcomerVisitRepository,
    private val clock: Clock,
    @Value("\${app.newcomer-visits.retention-years:3}") private val retentionYears: Long,
    @Value("\${app.newcomer-visits.deleted-retention-days:30}") private val deletedRetentionDays: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun purgeExpired(now: Instant): Int {
        val visitCutoff = LocalDate.ofInstant(now, clock.zone).minusYears(retentionYears)
        val expired = visitRepo.hardDeleteVisitedBefore(visitCutoff)
        val deleted = visitRepo.hardDeleteSoftDeletedBefore(now.minus(deletedRetentionDays, ChronoUnit.DAYS))
        log.info("Newcomer visits purged visitedBefore={} expired={} softDeleted={}", visitCutoff, expired, deleted)
        return expired + deleted
    }
}
