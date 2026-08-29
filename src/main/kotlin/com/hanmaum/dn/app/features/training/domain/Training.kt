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
) : BaseEntity()
