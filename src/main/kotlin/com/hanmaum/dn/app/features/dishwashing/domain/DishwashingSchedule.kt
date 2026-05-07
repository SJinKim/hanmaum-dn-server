package com.hanmaum.dn.app.features.dishwashing.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "dishwashing_schedule")
class DishwashingSchedule(
    @Column(name = "scheduled_date", nullable = false)
    val scheduledDate: LocalDate,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    val group: ChurchGroup,
    var note: String? = null,
) : BaseEntity()
