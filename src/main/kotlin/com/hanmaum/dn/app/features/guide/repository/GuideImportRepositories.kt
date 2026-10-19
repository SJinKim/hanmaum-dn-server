package com.hanmaum.dn.app.features.guide.repository

import com.hanmaum.dn.app.features.guide.domain.GuideImportBatch
import com.hanmaum.dn.app.features.guide.domain.GuideImportRecord
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface GuideImportBatchRepository : JpaRepository<GuideImportBatch, Long> {
    fun findByPublicId(publicId: UUID): GuideImportBatch?
}

interface GuideImportRecordRepository : JpaRepository<GuideImportRecord, Long> {
    fun findByServiceDate(serviceDate: LocalDate): GuideImportRecord?

    fun findAllByBatchPublicId(publicId: UUID): List<GuideImportRecord>
}
