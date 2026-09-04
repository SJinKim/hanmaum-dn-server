package com.hanmaum.dn.app.features.attendance.domain

import com.hanmaum.dn.app.common.domainvalue.CheckInPresence
import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(
    name = "attendance_logs",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_attendance_log",
            columnNames = ["member_id", "definition_id", "attendance_date"],
        ),
    ],
)
class AttendanceLog(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false)
    val definition: AttendanceDefinition,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    val member: Member?,
    @Column(name = "attendance_date", nullable = false)
    val attendanceDate: LocalDate,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id_at_check_in")
    val groupAtCheckIn: ChurchGroup? = null,
    @Column(name = "attended", nullable = false)
    val attended: Boolean = true,
    /**
     * What the server could establish about the member's position at check-in. Evidence,
     * not a gate — see [CheckInPresence]. Rows written before HDN-142 carry UNCONFIRMED.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "presence", nullable = false)
    val presence: CheckInPresence = CheckInPresence.UNCONFIRMED,
) : BaseEntity() {
    @Column(name = "delete_entry_at")
    var deleteEntryAt: Instant? = null
}
