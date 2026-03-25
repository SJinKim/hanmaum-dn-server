package com.hanmaum.dn.app.features.attendance.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.*
import java.time.LocalDate

// 2. Der Eintrag: "Max Mustermann war am 12.01.2026 da"
@Entity
@Table(name = "attendance_logs")
class AttendanceLog(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,
    @Column(nullable = false)
    val date: LocalDate,
    @Column(nullable = false)
    val category: String = "SUNDAY_SERVICE", // Könnte später ein Enum sein
    @Column(nullable = false)
    val status: String = "PRESENT", // PRESENT, LATE, ONLINE...
) : BaseEntity()
