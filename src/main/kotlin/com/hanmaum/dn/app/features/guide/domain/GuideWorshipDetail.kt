package com.hanmaum.dn.app.features.guide.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

enum class GuideRoom(
    val label: String,
) {
    MAIN_1F_A("본당1층A"),
    MAIN_1F_B("본당1층B"),
    MAIN_2F_C("본당2층C"),
    PRESCHOOL("유아유치부"),
}

@Entity
@Table(name = "guide_worship_details")
class GuideWorshipDetail(
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "report_id", nullable = false) val report: WorshipCountReport,
    @Enumerated(EnumType.STRING) @Column(name = "room_code", nullable = false, length = 20) val room: GuideRoom,
    @Column(name = "service_number", nullable = false) val serviceNumber: Int,
    @Column(name = "adult_count") val adultCount: Int?,
    @Column(name = "school_count") val schoolCount: Int?,
    @Column(name = "youth_count") val youthCount: Int?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}

@Entity
@Table(name = "guide_summary_conversions")
class GuideSummaryConversion(
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "report_id", nullable = false) val report: WorshipCountReport,
    @Column(name = "source_version", nullable = false) val sourceVersion: Long,
    @Column(name = "source_date", nullable = false) val sourceDate: LocalDate,
    @Column(name = "source_service1_count") val sourceService1Count: Int?,
    @Column(name = "source_service2_count") val sourceService2Count: Int?,
    @Column(name = "source_service3_count") val sourceService3Count: Int?,
    @Column(name = "source_notes", columnDefinition = "text") val sourceNotes: String?,
    @Column(name = "converted_by", nullable = false) val convertedBy: String,
    @Column(name = "converted_at", nullable = false) val convertedAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
