package com.hanmaum.dn.app.features.guide.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDate

/** NULL means incomplete; summation promotes each count before adding. */
fun worshipTotal(counts: List<Int?>): Long? {
    return counts.fold(0L) { sum, count -> sum + (count?.toLong() ?: return null) }
}

@Entity
@Table(name = "guide_worship_reports")
class WorshipCountReport(
    @Column(name = "service_date", nullable = false) var serviceDate: LocalDate,
    @Column(name = "service1_count") var service1Count: Int?,
    @Column(name = "service2_count") var service2Count: Int?,
    @Column(name = "service3_count") var service3Count: Int?,
    @Column(columnDefinition = "text") var notes: String?,
    @Column(name = "created_by", nullable = false) val createdBy: String,
    @Column(name = "updated_by", nullable = false) var updatedBy: String = createdBy,
) : BaseEntity() {
    @Version var version: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "report_mode", nullable = false, length = 16)
    var reportMode: GuideReportMode = GuideReportMode.SUMMARY_ONLY

    fun total(): Long? = worshipTotal(listOf(service1Count, service2Count, service3Count))
}

enum class GuideReportMode { SUMMARY_ONLY, DETAILED }
