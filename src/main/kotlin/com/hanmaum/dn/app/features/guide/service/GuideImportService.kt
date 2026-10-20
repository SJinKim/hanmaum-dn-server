package com.hanmaum.dn.app.features.guide.service

import com.hanmaum.dn.app.features.guide.domain.GuideImportBatch
import com.hanmaum.dn.app.features.guide.domain.GuideImportRecord
import com.hanmaum.dn.app.features.guide.domain.WorshipCountReport
import com.hanmaum.dn.app.features.guide.repository.GuideImportBatchRepository
import com.hanmaum.dn.app.features.guide.repository.GuideImportRecordRepository
import com.hanmaum.dn.app.features.guide.repository.WorshipCountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/** Safe summary only: never logs source notes or source row values. */
data class GuideImportResult(
    val dryRun: Boolean,
    val sourceSha256: String,
    val read: Int,
    val recorded: Int,
    val notes: Int,
    val serviceSums: List<Long>,
    val newReports: Int,
    val skipped: Int,
    val conflictRows: List<Int>,
    val warnings: List<String>,
    val batchPublicId: UUID? = null,
)

@Service
class GuideImportService(
    private val reader: GuideImportReader,
    private val reports: WorshipCountRepository,
    private val batches: GuideImportBatchRepository,
    private val records: GuideImportRecordRepository,
    private val mapper: ObjectMapper,
) {
    @Transactional
    fun import(
        path: Path,
        dryRun: Boolean = true,
        expectedSha256: String? = null,
    ): GuideImportResult {
        val source = reader.read(path)
        reader.validateApproved(source)
        if (!dryRun) require(expectedSha256 == source.sha256) { "Write requires the reviewed dry-run SHA-256." }
        val conflicts = mutableListOf<Int>()
        val fresh = mutableListOf<GuideImportRow>()
        var skipped = 0
        source.rows.forEach { row ->
            val record = records.findByServiceDate(row.date)
            val existing = reports.findByServiceDateAndDeletedAtIsNull(row.date)
            when {
                record != null &&
                    record.batch.mappingVersion == GUIDE_MAPPING_VERSION &&
                    record.canonicalSha256 == row.fingerprint &&
                    record.report.deletedAt == null &&
                    record.report.version == record.importedVersion &&
                    fingerprint(record.report) == row.fingerprint -> skipped++
                record != null || existing != null -> conflicts += row.rowNumber
                else -> fresh += row
            }
        }
        require(dryRun || conflicts.isEmpty()) { "Import conflicts at rows ${conflicts.joinToString()}; nothing imported." }
        val batch = if (!dryRun && fresh.isNotEmpty()) batches.save(GuideImportBatch(source.sha256, GUIDE_MAPPING_VERSION)) else null
        if (batch != null) {
            fresh.forEach { row ->
                val entity =
                    reports.saveAndFlush(
                        WorshipCountReport(
                            row.date,
                            row.counts[0],
                            row.counts[1],
                            row.counts[2],
                            row.notes,
                            "guide-import:${batch.publicId}",
                        ),
                    )
                records.save(
                    GuideImportRecord(
                        batch,
                        entity,
                        row.date,
                        row.rowNumber,
                        mapper.writeValueAsString(mapOf("sheet" to "Summary", "blockYear" to row.date.year, "cells" to row.source)),
                        row.fingerprint,
                        entity.version,
                    ),
                )
            }
        }
        return GuideImportResult(
            dryRun,
            source.sha256,
            source.rows.size,
            source.rows.count { it.total != null },
            source.rows.count { !it.notes.isNullOrBlank() },
            (0..2).map { i ->
                source.rows.sumOf { it.counts[i]?.toLong() ?: 0L }
            },
            fresh.size,
            skipped,
            conflicts,
            source.warnings,
            batch?.publicId,
        )
    }

    /** Roll back only untouched rows from this exact batch, under the same locks used by editors. */
    @Transactional
    fun rollback(
        batchId: UUID,
        dryRun: Boolean = true,
    ): Int {
        val batch = requireNotNull(batches.findByPublicId(batchId)) { "Import batch not found." }
        if (batch.rolledBackAt != null) return 0
        val rows = records.findAllByBatchPublicId(batchId)
        val locked =
            rows.sortedBy { it.serviceDate }.map { row ->
                val report =
                    requireNotNull(reports.lockActive(row.report.publicId)) { "Imported report is missing/deleted; rollback refused." }
                require(report.version == row.importedVersion && fingerprint(report) == row.canonicalSha256) {
                    "Imported report was edited; rollback refused."
                }
                report
            }
        if (!dryRun) {
            val now = Instant.now()
            locked.forEach {
                it.deletedAt = now
                it.updatedBy = "guide-rollback:$batchId"
            }
            reports.flush()
            batch.rolledBackAt = now
        }
        return locked.size
    }

    private fun fingerprint(report: WorshipCountReport): String =
        guideCanonical(report.serviceDate, listOf(report.service1Count, report.service2Count, report.service3Count), report.notes)
}
