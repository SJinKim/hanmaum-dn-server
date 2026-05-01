package com.hanmaum.dn.app.features.groups.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "group_meetings")
class GroupMeeting(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    val group: ChurchGroup,
    @Column(name = "meeting_time", nullable = false)
    var meetingTime: OffsetDateTime,
    var location: String, // z.B. "Raum 101" oder "Café"
    var description: String = "순모임", // Default Wert
) : BaseEntity()

// 2. Die Teilnahme & Gebet
@Entity
@Table(name = "meeting_attendances")
class MeetingAttendance(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    val meeting: GroupMeeting,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,
    @Column(name = "attendance_status")
    var status: String = "PRESENT", // "PRESENT" oder "ABSENT"
    @Column(name = "prayer_request", columnDefinition = "TEXT")
    var prayerRequest: String? = null,
) : BaseEntity()
