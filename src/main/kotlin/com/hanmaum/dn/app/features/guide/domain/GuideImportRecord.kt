package com.hanmaum.dn.app.features.guide.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "guide_import_records")
class GuideImportRecord(
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "batch_id", nullable = false) val batch: GuideImportBatch,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "report_id", nullable = false) val report: WorshipCountReport,
    @Column(name = "service_date", nullable = false) val serviceDate: LocalDate,
    @Column(name = "source_row", nullable = false) val sourceRow: Int,
    @Column(name = "source_data", nullable = false, columnDefinition = "text") val sourceData: String,
    @Column(name = "canonical_sha256", nullable = false, length = 64) val canonicalSha256: String,
    @Column(name = "imported_version", nullable = false) val importedVersion: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
