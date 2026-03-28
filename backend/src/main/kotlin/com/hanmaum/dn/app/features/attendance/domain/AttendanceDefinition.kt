package com.hanmaum.dn.app.features.attendance.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.DayOfWeek
import java.time.LocalTime

@Entity
@Table(name = "attendance_definitions")
class AttendanceDefinition(
    @Column(name = "title", nullable = false, length = 100)
    var title: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    var dayOfWeek: DayOfWeek,
    @Column(name = "window_start", nullable = false)
    var windowStart: LocalTime,
    @Column(name = "window_end", nullable = false)
    var windowEnd: LocalTime,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : BaseEntity()
