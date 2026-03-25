package com.hanmaum.dn.app.features.groups.service

import com.hanmaum.dn.app.common.exception.EntityNotFoundException
import com.hanmaum.dn.app.features.groups.api.v1.dto.*
import com.hanmaum.dn.app.features.groups.domain.GroupMeeting
import com.hanmaum.dn.app.features.groups.domain.MeetingAttendance
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.groups.repository.GroupMeetingRepository
import com.hanmaum.dn.app.features.groups.repository.MeetingAttendanceRepository
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GroupMeetingService(
    private val meetingRepo: GroupMeetingRepository,
    private val attendanceRepo: MeetingAttendanceRepository,
    private val groupRepo: ChurchGroupRepository,
    private val memberRepo: MemberRepository,
) {
    // 1. CREATE (Admin): Meeting planen
    @Transactional
    fun createMeeting(req: CreateMeetingRequest): Long {
        val group =
            groupRepo
                .findById(req.groupId)
                .orElseThrow { EntityNotFoundException("Group not found") }

        val meeting =
            GroupMeeting(
                group = group,
                meetingTime = req.meetingTime,
                location = req.location,
                description = req.description,
            )
        return meetingRepo.save(meeting).id!!
    }

    // 2. SUBMIT REPORT (Leader): Gebete & Anwesenheit speichern
    @Transactional
    fun submitReport(
        meetingId: Long,
        req: SubmitMeetingReportRequest,
    ) {
        val meeting =
            meetingRepo
                .findById(meetingId)
                .orElseThrow { EntityNotFoundException("Meeting not found") }

        // Clean Slate: Alte Einträge löschen (falls Update)
        attendanceRepo.deleteAllByMeetingId(meetingId)

        val attendances =
            req.entries.map { entry ->
                val member =
                    memberRepo
                        .findByPublicId(UUID.fromString(entry.memberId))
                        .orElseThrow { EntityNotFoundException("Member ${entry.memberId} not found") }

                MeetingAttendance(
                    meeting = meeting,
                    member = member,
                    status = if (entry.isPresent) "PRESENT" else "ABSENT",
                    prayerRequest = entry.prayerRequest,
                )
            }
        attendanceRepo.saveAll(attendances)
    }

    // 3. GET DETAILS (Mit Security Check)
    @Transactional(readOnly = true)
    fun getMeetingDetails(
        meetingId: Long,
        requesterMemberId: String,
        isAdminOrPastor: Boolean,
    ): MeetingDetailDto {
        val meeting =
            meetingRepo
                .findById(meetingId)
                .orElseThrow { EntityNotFoundException("Meeting not found") }

        // Sicherheits-Check:
        // Wenn KEIN Admin/Pastor -> Prüfen ob User in der gleichen Gruppe ist
        if (!isAdminOrPastor) {
            val requester =
                memberRepo
                    .findByPublicId(UUID.fromString(requesterMemberId))
                    .orElseThrow { EntityNotFoundException("Requester not found") }

            if (requester.group?.id != meeting.group.id) {
                throw IllegalAccessException("Du darfst nur Gebetsanliegen deiner eigenen Gruppe sehen!")
            }
        }

        // Daten laden
        val attendances = attendanceRepo.findAllByMeetingId(meetingId)

        return MeetingDetailDto(
            id = meeting.id!!,
            groupName = meeting.group.name,
            meetingTime = meeting.meetingTime,
            location = meeting.location,
            attendees =
                attendances.map { att ->
                    AttendanceEntryDto(
                        memberName = "${att.member.lastName} ${att.member.firstName}",
                        memberId = att.member.publicId.toString(),
                        status = att.status,
                        prayerRequest = att.prayerRequest,
                    )
                },
        )
    }

    // 4. GET LIST (Admin: Alle, User: Nur meine Gruppe)
    @Transactional(readOnly = true)
    fun getMeetings(
        requesterMemberId: String,
        isAdminOrPastor: Boolean,
    ): List<GroupMeetingDto> {
        val meetings =
            if (isAdminOrPastor) {
                meetingRepo.findAllByOrderByMeetingTimeDesc()
            } else {
                val requester =
                    memberRepo
                        .findByPublicId(UUID.fromString(requesterMemberId))
                        .orElseThrow { EntityNotFoundException("Requester not found") }
                val myGroupId = requester.group?.id ?: return emptyList()
                meetingRepo.findAllByGroupIdOrderByMeetingTimeDesc(myGroupId)
            }

        return meetings.map { m ->
            GroupMeetingDto(
                id = m.id!!,
                groupName = m.group.name,
                meetingTime = m.meetingTime,
                location = m.location,
                description = m.description,
                attendanceCount = 0, // Könnte man mit COUNT Query optimieren
            )
        }
    }
}
