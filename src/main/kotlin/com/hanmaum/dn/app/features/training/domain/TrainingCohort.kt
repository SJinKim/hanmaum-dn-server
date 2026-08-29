package com.hanmaum.dn.app.features.training.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

/**
 * A numbered intake (기수) of a course.
 *
 * Identified by [series] + [ordinal], not by [label]: the same intake appears under
 * different labels in different source sheets.
 */
@Entity
@Table(name = "training_cohort")
class TrainingCohort(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id", nullable = false)
    var training: Training,
    @Column(name = "ordinal", nullable = false)
    var ordinal: Int,
    @Enumerated(EnumType.STRING)
    @Column(name = "series", nullable = false, length = 20)
    var series: CohortSeries = CohortSeries.POWER,
    /** Raw label from the source sheet; display only. */
    @Column(name = "label", length = 100)
    var label: String? = null,
    @Column(name = "cohort_year")
    var cohortYear: Int? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "term", length = 20)
    var term: CohortTerm? = null,
    @Column(name = "started_on")
    var startedOn: LocalDate? = null,
    @Column(name = "ended_on")
    var endedOn: LocalDate? = null,
) : BaseEntity()
