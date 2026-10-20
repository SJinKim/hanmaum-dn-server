package com.hanmaum.dn.app.features.guide.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.floorplan.domain.Room
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version

@Entity
@Table(name = "guide_counting_areas")
class GuideCountingArea(
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "room_id", nullable = false) var room: Room,
    @Column(nullable = false, length = 200) var label: String,
    @Column(name = "display_order", nullable = false) var displayOrder: Int,
    @Column(nullable = false) var active: Boolean = true,
) : BaseEntity() {
    @Version var version: Long = 0
}

/** Immutable report membership and labels, independent of subsequent room/configuration edits. */
@Entity
@Table(name = "guide_report_areas")
class GuideReportArea(
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "report_id", nullable = false) val report: WorshipCountReport,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "area_id", nullable = false) val area: GuideCountingArea,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "room_id", nullable = false) val room: Room,
    @Column(name = "room_name", nullable = false, length = 200) val roomName: String,
    @Column(nullable = false, length = 200) val label: String,
    @Column(name = "display_order", nullable = false) val displayOrder: Int,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
