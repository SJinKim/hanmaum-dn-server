package com.hanmaum.dn.app.features.attendance.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.DayOfWeek
import java.time.LocalTime

// 1. Die Regel: "Sonntags 11:00 - 14:00"
@Entity
@Table(name = "attendance_definitions")
class AttendanceDefinition(
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    val dayOfWeek: DayOfWeek,
    @Column(name = "start_time", nullable = false)
    val startTime: LocalTime,
    @Column(name = "end_time", nullable = false)
    var endTime: LocalTime,
    var title: String? = "Sonntagsgottesdienst",
    @Column(name = "is_active")
    var active: Boolean = true,
) : BaseEntity()
