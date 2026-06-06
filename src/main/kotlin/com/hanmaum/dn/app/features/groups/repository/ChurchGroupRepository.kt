package com.hanmaum.dn.app.features.groups.repository

import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.domain.GroupMeeting
import com.hanmaum.dn.app.features.groups.domain.MeetingAttendance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface ChurchGroupRepository : JpaRepository<ChurchGroup, Long> {
    /** All non-deleted groups, ordered for display (division, then name). */
    fun findAllByDeletedAtIsNullOrderByDivisionAscNameAsc(): List<ChurchGroup>

    /** Lookup by external UUID, excluding soft-deleted rows. */
    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<ChurchGroup>
}

@Repository
interface GroupMeetingRepository : JpaRepository<GroupMeeting, Long> {
    // Finde Meetings einer bestimmten Gruppe (für User View)
    fun findAllByGroupIdOrderByMeetingTimeDesc(groupId: Long): List<GroupMeeting>

    // Finde alle Meetings (für Pastor View)
    fun findAllByOrderByMeetingTimeDesc(): List<GroupMeeting>
}

@Repository
interface MeetingAttendanceRepository : JpaRepository<MeetingAttendance, Long> {
    // Finde alle Einträge zu einem Meeting
    fun findAllByMeetingId(meetingId: Long): List<MeetingAttendance>

    // Lösche alte Einträge, falls der Leader den Bericht korrigiert (Update)
    fun deleteAllByMeetingId(meetingId: Long)
}
