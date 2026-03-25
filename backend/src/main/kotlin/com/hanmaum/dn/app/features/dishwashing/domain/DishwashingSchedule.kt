package com.hanmaum.dn.app.features.dishwashing.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import jakarta.persistence.*
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
