package com.hanmaum.dn.app.features.guide.repository

import com.hanmaum.dn.app.features.guide.domain.GuideSummaryConversion
import com.hanmaum.dn.app.features.guide.domain.GuideWorshipDetail
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface GuideWorshipDetailRepository : JpaRepository<GuideWorshipDetail, Long> {
    fun findAllByReportPublicId(publicId: UUID): List<GuideWorshipDetail>

    @Modifying(flushAutomatically = true)
    @Query("delete from GuideWorshipDetail d where d.report.publicId = :publicId")
    fun deleteMatrix(publicId: UUID): Int
}

interface GuideSummaryConversionRepository : JpaRepository<GuideSummaryConversion, Long>
