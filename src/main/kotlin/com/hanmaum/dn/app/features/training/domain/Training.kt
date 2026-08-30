package com.hanmaum.dn.app.features.training.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Entity
@Table(name = "training")
class Training(
    /** Stable, language-independent key; see [TrainingCode]. */
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 50, unique = true)
    var code: TrainingCode,
    @Column(name = "name", nullable = false, length = 100)
    var name: String,
    /** Progression order; the members grid surfaces the highest completed sort_order. */
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,
    /** Korean display name. Data only — never used as an identifier. */
    @Column(name = "name_ko", length = 100)
    var nameKo: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20)
    var category: TrainingCategory? = null,
    /** True when the course runs in numbered cohorts (기수). */
    @Column(name = "has_cohorts", nullable = false)
    var hasCohorts: Boolean = false,
    /** False for discontinued courses that exist only for archived records. */
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
    /** Course that must be completed first; null when there is no requirement. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prerequisite_training_id")
    var prerequisite: Training? = null,
    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,
    // ─── Current offering ─────────────────────────────────────────────────────
    // When this run of the course starts, how it meets, and whether members can
    // still apply. All nullable: a course in the catalog need not be running.
    @Column(name = "start_date")
    var startDate: LocalDate? = null,
    /** Length of the course in weeks — the "4주" half of the list's meta line. */
    @Column(name = "duration_weeks")
    var durationWeeks: Int? = null,
    /** Drives the "신청 가능" badge and gates [registrationDeadline] checks. */
    @Column(name = "open_for_registration", nullable = false)
    var openForRegistration: Boolean = false,
    @Enumerated(EnumType.STRING)
    @Column(name = "weekday", length = 20)
    var weekday: DayOfWeek? = null,
    @Column(name = "start_time")
    var startTime: LocalTime? = null,
    @Column(name = "duration_minutes")
    var durationMinutes: Int? = null,
    @Column(name = "location", length = 200)
    var location: String? = null,
    /** Display name of whoever leads this run; often a guest with no member row. */
    @Column(name = "leader_name", length = 150)
    var leaderName: String? = null,
    /** Seats in this run; null means uncapped. */
    @Column(name = "capacity")
    var capacity: Int? = null,
    /** Last day a member may apply; null means "until it starts". */
    @Column(name = "registration_deadline")
    var registrationDeadline: LocalDate? = null,
) : BaseEntity() {
    /** "이런 분께 권합니다" — ordered lines describing who the course is for. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "training_target_audience",
        joinColumns = [JoinColumn(name = "training_id")],
    )
    @OrderColumn(name = "display_order")
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    val targetAudience: MutableList<String> = mutableListOf()

    fun replaceTargetAudience(newTargetAudience: List<String>) {
        targetAudience.clear()
        targetAudience.addAll(newTargetAudience)
    }
}
