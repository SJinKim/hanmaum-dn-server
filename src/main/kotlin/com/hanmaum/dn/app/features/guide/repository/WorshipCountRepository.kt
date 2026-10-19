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
