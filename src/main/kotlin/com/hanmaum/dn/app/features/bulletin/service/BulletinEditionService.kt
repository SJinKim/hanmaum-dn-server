package com.hanmaum.dn.app.features.bulletin.service

import com.hanmaum.dn.app.features.bulletin.domain.BulletinEdition
import com.hanmaum.dn.app.features.bulletin.domain.BulletinService
import com.hanmaum.dn.app.features.bulletin.domain.BulletinStatus
import com.hanmaum.dn.app.features.bulletin.repository.BulletinEditionRepository
import com.hanmaum.dn.app.features.bulletin.repository.BulletinServiceRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/** What a new draft starts with unless the admin picks otherwise. */
data class BulletinDefaults(
    val serviceDate: LocalDate,
    val service: BulletinService,
)

/**
 * Sunday logic, VOL numbering and taking over last week's content (HDN-290). The HTTP layer
 * on top of this is HDN-146.
 */
@Service
class BulletinEditionService(
    private val editions: BulletinEditionRepository,
    private val services: BulletinServiceRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
    /** VOL of the first edition published here is this plus one; carries over the paper numbering. */
    @Value("\${bulletin.volume-offset:0}") private val volumeOffset: Int,
) {
    /** Today in Berlin, whatever zone the injected clock carries. */
    private fun today(): LocalDate = LocalDate.now(clock.withZone(BERLIN))

    private fun comingSunday(): LocalDate = today().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

    /** The first Sunday from today on (today included) that has no edition yet. */
    @Transactional(readOnly = true)
    fun nextFreeSunday(): LocalDate {
        val from = comingSunday()
        val taken = editions.findServiceDatesFrom(from).toSet()
        return generateSequence(from) { it.plusWeeks(1) }.first { it !in taken }
    }

    @Transactional(readOnly = true)
    fun defaults(): BulletinDefaults = BulletinDefaults(nextFreeSunday(), defaultService())

    /**
     * Creates a draft. Without [serviceDate] it lands on the next free Sunday, without
     * [servicePublicId] on the default service. [copyFrom] takes over that edition's content.
     */
    @Transactional
    fun createDraft(
        serviceDate: LocalDate?,
        servicePublicId: UUID?,
        copyFrom: UUID?,
        createdBy: String,
    ): BulletinEdition {
        val source = copyFrom?.let { findEdition(it) }
        val date = serviceDate ?: nextFreeSunday()
        if (date.dayOfWeek != DayOfWeek.SUNDAY) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "serviceDate must be a Sunday")
        }
        if (editions.existsByServiceDate(date)) throw sundayTaken()

        val service =
            servicePublicId?.let {
                services.findByPublicIdAndDeletedAtIsNull(it)
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found")
            } ?: defaultService()

        val edition = BulletinEdition(serviceDate = date, service = service, createdBy = createdBy)
        edition.snapshotService()
        source?.let { edition.copyContentFrom(it) }
        return try {
            editions.saveAndFlush(edition)
        } catch (_: DataIntegrityViolationException) {
            // Another admin took the same Sunday between the check and the insert.
            throw sundayTaken()
        }
    }

    /**
     * Publishes [publicId]. The first publish hands out the next VOL; the advisory lock makes two
     * simultaneous publishes take turns, the unique constraint on volume is the backstop.
     */
    @Transactional
    fun publish(
        publicId: UUID,
        by: String,
    ): BulletinEdition {
        val edition = findEdition(publicId)
        if (edition.status == BulletinStatus.PUBLISHED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Edition is already published")
        }
        edition.publish(edition.volume ?: nextVolume(), clock.instant(), by)
        return editions.saveAndFlush(edition)
    }

    /** Takes [publicId] off the app. Its VOL stays with it for a later republish. */
    @Transactional
    fun withdraw(
        publicId: UUID,
        by: String,
    ): BulletinEdition {
        val edition = findEdition(publicId)
        if (edition.status != BulletinStatus.PUBLISHED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Only a published edition can be withdrawn")
        }
        edition.withdraw(clock.instant(), by)
        return editions.saveAndFlush(edition)
    }

    /**
     * The edition members see: the published one with the latest date up to the coming Sunday.
     * Null when nothing is published yet; the caller renders the empty state.
     */
    @Transactional(readOnly = true)
    fun currentEdition(): BulletinEdition? =
        editions.findFirstByStatusAndServiceDateLessThanEqualAndDeletedAtIsNullOrderByServiceDateDesc(
            BulletinStatus.PUBLISHED,
            comingSunday(),
        )

    private fun nextVolume(): Int {
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock($VOLUME_LOCK_KEY)")
        return maxOf(editions.findMaxVolume() ?: 0, volumeOffset) + 1
    }

    private fun findEdition(publicId: UUID): BulletinEdition =
        editions.findByPublicIdAndDeletedAtIsNull(publicId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Bulletin edition not found")

    private fun defaultService(): BulletinService =
        services.findByIsBulletinDefaultTrueAndDeletedAtIsNull()
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "No default bulletin service configured")

    private fun sundayTaken() = ResponseStatusException(HttpStatus.CONFLICT, "A bulletin for this Sunday already exists")

    companion object {
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

        /** Arbitrary but fixed; only VOL assignment takes this lock. */
        private const val VOLUME_LOCK_KEY = 290_001L
    }
}
