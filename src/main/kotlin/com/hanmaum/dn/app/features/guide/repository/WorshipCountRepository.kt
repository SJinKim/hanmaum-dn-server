package com.hanmaum.dn.app.features.guide.repository

import com.hanmaum.dn.app.features.guide.domain.WorshipCountReport
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.util.UUID

interface WorshipCountRepository : JpaRepository<WorshipCountReport, Long> {
    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): WorshipCountReport?

    fun findByServiceDateAndDeletedAtIsNull(serviceDate: LocalDate): WorshipCountReport?

    fun findByServiceDateInAndDeletedAtIsNull(serviceDates: Collection<LocalDate>): List<WorshipCountReport>

    fun findAllByServiceDateLessThanEqualAndDeletedAtIsNullOrderByServiceDateDesc(
        to: LocalDate,
        pageable: Pageable,
    ): List<WorshipCountReport>

    @Query(
        "select r from WorshipCountReport r where r.deletedAt is null and r.serviceDate <= :to and " +
            "(r.service1Count is not null or r.service2Count is not null or r.service3Count is not null or " +
            "exists (select d.id from GuideWorshipDetail d where d.report = r and " +
            "(d.adultCount is not null or d.schoolCount is not null or d.youthCount is not null))) order by r.serviceDate desc",
    )
    fun findHomeConfirmed(
        to: LocalDate,
        pageable: Pageable,
    ): List<WorshipCountReport>

    @Query(
        value =
            "select * from guide_worship_reports where deleted_at is null and service_date <= :to " +
                "and extract(isodow from service_date) = 7 and service1_count is not null " +
                "and service2_count is not null and service3_count is not null order by service_date desc",
        nativeQuery = true,
    )
    fun findHomeCompleteSundays(
        to: LocalDate,
        pageable: Pageable,
    ): List<WorshipCountReport>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from WorshipCountReport r where r.publicId = :publicId and r.deletedAt is null")
    fun lockActive(publicId: UUID): WorshipCountReport?

    @Query("select r from WorshipCountReport r where r.deletedAt is null and r.serviceDate between :from and :to")
    fun findRange(
        from: LocalDate,
        to: LocalDate,
        pageable: Pageable,
    ): Page<WorshipCountReport>
}
