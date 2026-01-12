package com.hanmaum.dn.app.features.attendance.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.*
import java.time.DayOfWeek
import java.time.LocalTime

// 1. Die Regel: "Sonntags 11:00 - 14:00"
@Entity
@Table(name = "attendance_definitions")
class AttendanceDefinition(
    @Id
    @GeneratedValue(GenerationType.IDENTITY)
    val id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    val dayOfWeek: DayOfWeek,

    @Column(name = "start_time", nullable = false)
    val startTime: LocalTime,

    @Column(name = "end_time", nullable = false)
    var endTime: LocalTime,

    var title: String? = "Sonntagsgottesdienst",

    @Column(name = "is_active")
    var isActive: Boolean = true

) : BaseEntity()