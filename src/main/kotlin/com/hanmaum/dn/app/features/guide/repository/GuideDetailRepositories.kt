package com.hanmaum.dn.app.features.guide.repository

import com.hanmaum.dn.app.features.floorplan.domain.Room
import com.hanmaum.dn.app.features.guide.domain.GuideCountingArea
import com.hanmaum.dn.app.features.guide.domain.GuideReportArea
import com.hanmaum.dn.app.features.guide.domain.GuideSummaryConversion
import com.hanmaum.dn.app.features.guide.domain.GuideWorshipDetail
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface GuideWorshipDetailRepository : JpaRepository<GuideWorshipDetail, Long> {
    @Query("select d from GuideWorshipDetail d join fetch d.reportArea s join fetch s.area where d.report.publicId = :publicId")
    fun findAllByReportPublicId(publicId: UUID): List<GuideWorshipDetail>

    @Modifying(flushAutomatically = true)
    @Query("delete from GuideWorshipDetail d where d.report.publicId = :publicId")
    fun deleteMatrix(publicId: UUID): Int
}

interface GuideSummaryConversionRepository : JpaRepository<GuideSummaryConversion, Long>

interface GuideCountingAreaRepository : JpaRepository<GuideCountingArea, Long> {
    @Query("select a from GuideCountingArea a join fetch a.room r where a.deletedAt is null order by a.displayOrder, a.publicId")
    fun findConfigured(): List<GuideCountingArea>

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query(
        "select a from GuideCountingArea a join fetch a.room r join fetch r.floor f " +
            "where a.deletedAt is null and a.active = true and r.deletedAt is null and f.deletedAt is null " +
            "order by a.displayOrder, a.publicId",
    )
    fun lockUsable(): List<GuideCountingArea>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from GuideCountingArea a where a.publicId = :publicId and a.deletedAt is null")
    fun lockActive(publicId: UUID): GuideCountingArea?
}

interface GuideReportAreaRepository : JpaRepository<GuideReportArea, Long> {
    @Query("select s from GuideReportArea s join fetch s.area join fetch s.room where s.report.publicId = :publicId")
    fun findAllByReportPublicId(publicId: UUID): List<GuideReportArea>
}

interface GuideRoomLookupRepository : JpaRepository<Room, Long> {
    @Query(
        "select r from Room r join fetch r.floor f where r.deletedAt is null and f.deletedAt is null order by f.floorNumber, r.name, r.publicId",
    )
    fun findEligible(): List<Room>

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select r from Room r join fetch r.floor f where r.publicId = :publicId and r.deletedAt is null and f.deletedAt is null")
    fun lockEligible(publicId: UUID): Room?
}
