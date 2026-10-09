package com.hanmaum.dn.app.features.bulletin.repository

import com.hanmaum.dn.app.features.bulletin.domain.BulletinEdition
import com.hanmaum.dn.app.features.bulletin.domain.BulletinService
import com.hanmaum.dn.app.features.bulletin.domain.BulletinStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.util.UUID

interface BulletinEditionRepository : JpaRepository<BulletinEdition, Long> {
    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): BulletinEdition?

    fun findByServiceDateAndDeletedAtIsNull(serviceDate: LocalDate): BulletinEdition?

    /**
     * Deleted rows included: the unique constraint on service_date covers them too, so a
     * Sunday with a deleted edition is not free.
     */
    fun existsByServiceDate(serviceDate: LocalDate): Boolean

    /** Sundays from [from] on that already carry an edition, deleted ones included. */
    @Query("select e.serviceDate from BulletinEdition e where e.serviceDate >= :from")
    fun findServiceDatesFrom(from: LocalDate): List<LocalDate>

    /** Highest VOL ever handed out, deleted rows included, so a number is never reused. */
    @Query("select max(e.volume) from BulletinEdition e")
    fun findMaxVolume(): Int?

    fun findFirstByStatusAndServiceDateLessThanEqualAndDeletedAtIsNullOrderByServiceDateDesc(
        status: BulletinStatus,
        serviceDate: LocalDate,
    ): BulletinEdition?

    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<BulletinEdition>

    fun findAllByStatusAndDeletedAtIsNull(
        status: BulletinStatus,
        pageable: Pageable,
    ): Page<BulletinEdition>

    fun findAllByStatusAndServiceDateLessThanEqualAndDeletedAtIsNull(
        status: BulletinStatus,
        serviceDate: LocalDate,
        pageable: Pageable,
    ): Page<BulletinEdition>

    fun findByServiceDateAndStatusAndDeletedAtIsNull(
        serviceDate: LocalDate,
        status: BulletinStatus,
    ): BulletinEdition?

    /** Deleted editions included: their rows still point at the service. */
    fun existsByService(service: BulletinService): Boolean
}
