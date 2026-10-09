package com.hanmaum.dn.app.features.bulletin.service

import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinEditionResponse
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinEditionSummary
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinSectionTitleResponse
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinSundayOption
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.UpdateBulletinRequest
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.toEntity
import com.hanmaum.dn.app.features.bulletin.domain.BulletinEdition
import com.hanmaum.dn.app.features.bulletin.domain.BulletinService
import com.hanmaum.dn.app.features.bulletin.domain.BulletinSharingBlockType
import com.hanmaum.dn.app.features.bulletin.domain.BulletinStatus
import com.hanmaum.dn.app.features.bulletin.repository.BulletinEditionRepository
import com.hanmaum.dn.app.features.bulletin.repository.BulletinSectionTitleRepository
import com.hanmaum.dn.app.features.bulletin.repository.BulletinServiceRepository
import org.hibernate.exception.ConstraintViolationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.orm.ObjectOptimisticLockingFailureException
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
    val sundays: List<BulletinSundayOption>,
    val nextFrom: LocalDate,
)

/**
 * Sunday logic, VOL numbering and taking over last week's content (HDN-290), plus editing,
 * listing and the member views behind the HTTP layer (HDN-146).
 */
@Service
class BulletinEditionService(
    private val editions: BulletinEditionRepository,
    private val services: BulletinServiceRepository,
    private val sectionTitles: BulletinSectionTitleRepository,
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
    fun nextFreeSunday(): LocalDate = firstFreeSunday(comingSunday())

    @Transactional(readOnly = true)
    fun defaults(from: LocalDate? = null): BulletinDefaults {
        val coming = comingSunday()
        if (from != null && (from.dayOfWeek != DayOfWeek.SUNDAY || from > MAX_CURSOR_DATE)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be a Sunday within the supported date range")
        }
        // A cursor can age while a tab stays open. Never offer past Sundays when browsing.
        val first = maxOf(from ?: coming, coming)
        val nextFrom = first.plusWeeks(SUNDAY_OPTION_COUNT.toLong())
        val existing = editions.findAllByServiceDateBetweenAndDeletedAtIsNull(first, nextFrom.minusWeeks(1)).associateBy { it.serviceDate }
        val sundays =
            (0 until SUNDAY_OPTION_COUNT).map { week ->
                val date = first.plusWeeks(week.toLong())
                val edition = existing[date]
                BulletinSundayOption(date, edition?.publicId, edition?.status)
            }
        // The suggestion is global: paging must not change the intended edition date.
        val suggested = firstFreeSunday(coming, existing.keys.takeIf { first == coming })
        return BulletinDefaults(suggested, defaultService(), sundays, nextFrom)
    }

    /** Reuse the first selection window; only query further windows when every Sunday is taken. */
    private fun firstFreeSunday(
        from: LocalDate,
        firstWindowTaken: Set<LocalDate>? = null,
    ): LocalDate {
        var window = from
        while (window <= MAX_CURSOR_DATE) {
            val taken =
                firstWindowTaken?.takeIf { window == from }
                    ?: editions.findServiceDatesBetween(window, window.plusWeeks((SUNDAY_OPTION_COUNT - 1).toLong())).toSet()
            for (week in 0 until SUNDAY_OPTION_COUNT) {
                val date = window.plusWeeks(week.toLong())
                if (date !in taken) return date
            }
            window = window.plusWeeks(SUNDAY_OPTION_COUNT.toLong())
        }
        throw ResponseStatusException(HttpStatus.CONFLICT, "No free Sunday within the supported date range")
    }

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
        // Like defaults(), today is valid when Berlin's current day is Sunday.
        if (date.isBefore(today()) || date > MAX_SERVICE_DATE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "serviceDate must be today or later within the supported date range")
        }
        if (editions.existsByServiceDateAndDeletedAtIsNull(date)) throw sundayTaken()

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
        } catch (error: DataIntegrityViolationException) {
            val constraint = generateSequence<Throwable>(error) { it.cause }.filterIsInstance<ConstraintViolationException>().firstOrNull()
            if (constraint?.constraintName == SUNDAY_CONSTRAINT) throw sundayTaken()
            throw error
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
        // Checked before the VOL is taken, so an incomplete edition never burns a number.
        missingForPublish(edition).takeIf { it.isNotEmpty() }?.let { throw BulletinIncompleteException(it) }
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

    // ─── HDN-146: admin ─────────────────────────────────────────────────────

    /** One edition in any status, with the current section titles. */
    @Transactional(readOnly = true)
    fun view(publicId: UUID): BulletinEditionResponse = BulletinEditionResponse.from(findEdition(publicId), sectionTitleViews())

    /** Newest Sunday first; [status] null lists every status. */
    @Transactional(readOnly = true)
    fun list(
        status: BulletinStatus?,
        page: Int,
        size: Int,
    ): Page<BulletinEditionSummary> {
        val pageable = byDateDesc(page, size)
        val result =
            if (status == null) {
                editions.findAllByDeletedAtIsNull(pageable)
            } else {
                editions.findAllByStatusAndDeletedAtIsNull(status, pageable)
            }
        return result.map { BulletinEditionSummary.from(it) }
    }

    /**
     * Replaces the content of a draft or withdrawn edition. A published one must be withdrawn
     * first, so members never see a half-edited 주보. [UpdateBulletinRequest.version] older than
     * the stored one is the same 409 Hibernate raises for a race between two saves.
     */
    @Transactional
    fun update(
        publicId: UUID,
        request: UpdateBulletinRequest,
        by: String,
    ): BulletinEditionResponse {
        val edition = findEdition(publicId)
        if (edition.version != request.version) {
            throw ObjectOptimisticLockingFailureException(BulletinEdition::class.java, publicId)
        }
        if (edition.status == BulletinStatus.PUBLISHED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Withdraw a published edition before editing it")
        }
        if (request.sharingBlocks.any { it.type != BulletinSharingBlockType.SCRIPTURE && !it.reference.isNullOrBlank() }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Only a SCRIPTURE block carries a reference")
        }

        edition.openingPrayerBy = request.openingPrayerBy.clean()
        edition.offeringSongBy = request.offeringSongBy.clean()
        edition.scriptureReference = request.scriptureReference.clean()
        edition.sermonTitle = request.sermonTitle.clean()
        edition.sermonPreacher = request.sermonPreacher.clean()
        edition.responsePrayerBy = request.responsePrayerBy.clean()
        edition.responseSong = request.responseSong.clean()
        edition.songs.clear()
        edition.songs.addAll(request.songs.map { it.trim() })
        edition.announcements.clear()
        edition.announcements.addAll(request.announcements.map { it.toEntity() })
        edition.sharingBlocks.clear()
        edition.sharingBlocks.addAll(request.sharingBlocks.map { it.toEntity() })
        edition.updatedBy = by

        return BulletinEditionResponse.from(editions.saveAndFlush(edition), sectionTitleViews())
    }

    /**
     * Soft-deletes a draft that was never published. Anything with a VOL stays: the number is
     * printed history, and withdrawing is how it leaves the app.
     */
    @Transactional
    fun delete(publicId: UUID) {
        val edition = findEdition(publicId)
        if (edition.status != BulletinStatus.DRAFT || edition.volume != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Only a never published draft can be deleted")
        }
        edition.deletedAt = clock.instant()
        editions.save(edition)
    }

    // ─── HDN-146: members ───────────────────────────────────────────────────

    /** [currentEdition] as the app shows it; null is the empty state. */
    @Transactional(readOnly = true)
    fun currentView(): BulletinEditionResponse? = currentEdition()?.let { BulletinEditionResponse.from(it, sectionTitleViews()) }

    /** The published edition of [serviceDate]; drafts and withdrawn ones are a 404 for members. */
    @Transactional(readOnly = true)
    fun publishedView(serviceDate: LocalDate): BulletinEditionResponse {
        val edition =
            editions.findByServiceDateAndStatusAndDeletedAtIsNull(serviceDate, BulletinStatus.PUBLISHED)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Bulletin edition not found")
        return BulletinEditionResponse.from(edition, sectionTitleViews())
    }

    /** Published editions, newest Sunday first. */
    @Transactional(readOnly = true)
    fun history(
        page: Int,
        size: Int,
    ): Page<BulletinEditionSummary> =
        editions
            .findAllByStatusAndDeletedAtIsNull(BulletinStatus.PUBLISHED, byDateDesc(page, size))
            .map { BulletinEditionSummary.from(it) }

    /** Field name to message for every required field [edition] lacks; empty when it can go out. */
    private fun missingForPublish(edition: BulletinEdition): Map<String, String> =
        buildMap {
            if (edition.sermonTitle.isNullOrBlank()) put("sermonTitle", "설교 제목은 필수입니다.")
            if (edition.sermonPreacher.isNullOrBlank()) put("sermonPreacher", "설교자는 필수입니다.")
            if (edition.songs.isEmpty()) put("songs", "찬양은 최소 1곡이 필요합니다.")
        }

    private fun sectionTitleViews(): List<BulletinSectionTitleResponse> =
        sectionTitles.findAll().sortedBy { it.key.ordinal }.map { BulletinSectionTitleResponse.from(it) }

    private fun byDateDesc(
        page: Int,
        size: Int,
    ) = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "serviceDate"))

    private fun String?.clean(): String? = this?.trim()?.ifEmpty { null }

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

        private const val MAX_PAGE_SIZE = 100

        private const val SUNDAY_OPTION_COUNT = 12

        private const val SUNDAY_CONSTRAINT = "uq_bulletin_edition_service_date"

        // Keep dates and the forward cursor in the API's four-digit ISO year format.
        private val MAX_SERVICE_DATE = LocalDate.of(9999, 12, 31)
        private val MAX_CURSOR_DATE = MAX_SERVICE_DATE.minusWeeks(SUNDAY_OPTION_COUNT.toLong())
    }
}
