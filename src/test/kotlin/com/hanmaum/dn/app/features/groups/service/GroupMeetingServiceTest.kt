package com.hanmaum.dn.app.features.groups.service

import com.hanmaum.dn.app.common.exception.EntityNotFoundException
import com.hanmaum.dn.app.features.groups.api.v1.dto.CreateMeetingRequest
import com.hanmaum.dn.app.features.groups.api.v1.dto.ReportEntry
import com.hanmaum.dn.app.features.groups.api.v1.dto.SubmitMeetingReportRequest
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.domain.GroupLeader
import com.hanmaum.dn.app.features.groups.domain.GroupMeeting
import com.hanmaum.dn.app.features.groups.domain.MeetingAttendance
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.groups.repository.GroupLeaderRepository
import com.hanmaum.dn.app.features.groups.repository.GroupMeetingRepository
import com.hanmaum.dn.app.features.groups.repository.MeetingAttendanceRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.CurrentMemberResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class GroupMeetingServiceTest {
    @Mock private lateinit var meetingRepo: GroupMeetingRepository

    @Mock private lateinit var attendanceRepo: MeetingAttendanceRepository

    @Mock private lateinit var groupRepo: ChurchGroupRepository

    @Mock private lateinit var memberRepo: MemberRepository

    @Mock private lateinit var leaderRepo: GroupLeaderRepository

    @Mock private lateinit var currentMember: CurrentMemberResolver

    @InjectMocks
    private lateinit var groupMeetingService: GroupMeetingService

    private val subject = "kc-001"

    private fun group(
        id: Long,
        name: String = "다니엘조",
    ): ChurchGroup {
        val g = ChurchGroup(name = name)
        g.id = id
        return g
    }

    private fun member(
        id: Long,
        firstName: String = "길동",
        lastName: String = "홍",
        grp: ChurchGroup? = null,
    ): Member {
        val m = Member(lastName = lastName, firstName = firstName)
        m.id = id
        m.group = grp
        return m
    }

    private fun meeting(
        id: Long,
        group: ChurchGroup,
    ): GroupMeeting {
        val m =
            GroupMeeting(
                group = group,
                meetingTime = OffsetDateTime.of(2026, 3, 22, 14, 0, 0, 0, ZoneOffset.UTC),
                location = "교회",
            )
        m.id = id
        return m
    }

    private fun leader(
        group: ChurchGroup,
        member: Member,
    ) = GroupLeader(group = group, member = member, startDate = LocalDate.of(2026, 1, 1))

    // --- createMeeting ---

    @Test
    fun `createMeeting throws EntityNotFoundException when group not found`() {
        `when`(groupRepo.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            groupMeetingService.createMeeting(
                CreateMeetingRequest(groupId = UUID.randomUUID(), meetingTime = OffsetDateTime.now(), location = "교회"),
            )
        }
    }

    @Test
    fun `createMeeting looks the group up by publicId and returns the meeting publicId`() {
        val g = group(1L)
        val saved = meeting(42L, g)
        `when`(groupRepo.findByPublicIdAndDeletedAtIsNull(g.publicId)).thenReturn(Optional.of(g))
        `when`(meetingRepo.save(any<GroupMeeting>())).thenReturn(saved)

        val publicId =
            groupMeetingService.createMeeting(
                CreateMeetingRequest(
                    groupId = g.publicId,
                    meetingTime = OffsetDateTime.of(2026, 4, 6, 14, 0, 0, 0, ZoneOffset.UTC),
                    location = "교회",
                ),
            )

        assertEquals(saved.publicId, publicId)
    }

    // --- submitReport ---

    @Test
    fun `submitReport throws EntityNotFoundException when meeting not found`() {
        `when`(meetingRepo.findByPublicId(any())).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            groupMeetingService.submitReport(UUID.randomUUID(), SubmitMeetingReportRequest(entries = emptyList()), subject, true)
        }
    }

    @Test
    fun `submitReport deletes old attendances before saving new ones`() {
        val m = meeting(10L, group(1L))
        `when`(meetingRepo.findByPublicId(m.publicId)).thenReturn(Optional.of(m))
        `when`(attendanceRepo.saveAll(any<Iterable<MeetingAttendance>>())).thenReturn(emptyList())

        groupMeetingService.submitReport(m.publicId, SubmitMeetingReportRequest(entries = emptyList()), subject, true)

        verify(attendanceRepo).deleteAllByMeetingId(10L)
        verify(attendanceRepo).saveAll(any<Iterable<MeetingAttendance>>())
    }

    @Test
    fun `submitReport maps isPresent to PRESENT and ABSENT`() {
        val m = meeting(10L, group(1L))
        val present = member(1L)
        val absent = member(2L)
        val req =
            SubmitMeetingReportRequest(
                entries =
                    listOf(
                        ReportEntry(memberId = present.publicId.toString(), isPresent = true, prayerRequest = "건강"),
                        ReportEntry(memberId = absent.publicId.toString(), isPresent = false, prayerRequest = null),
                    ),
            )
        `when`(meetingRepo.findByPublicId(m.publicId)).thenReturn(Optional.of(m))
        `when`(memberRepo.findByPublicId(present.publicId)).thenReturn(Optional.of(present))
        `when`(memberRepo.findByPublicId(absent.publicId)).thenReturn(Optional.of(absent))
        val captor = argumentCaptor<Iterable<MeetingAttendance>>()
        `when`(attendanceRepo.saveAll(any<Iterable<MeetingAttendance>>())).thenReturn(emptyList())

        groupMeetingService.submitReport(m.publicId, req, subject, true)

        verify(attendanceRepo).saveAll(captor.capture())
        val saved = captor.firstValue.toList()
        assertEquals("PRESENT", saved[0].status)
        assertEquals("건강", saved[0].prayerRequest)
        assertEquals("ABSENT", saved[1].status)
    }

    @Test
    fun `submitReport lets the active leader of the meeting's group report`() {
        val g = group(1L)
        val m = meeting(10L, g)
        val caller = member(5L, grp = g)
        `when`(meetingRepo.findByPublicId(m.publicId)).thenReturn(Optional.of(m))
        `when`(currentMember.require(subject)).thenReturn(caller)
        `when`(leaderRepo.findActiveByGroupId(1L)).thenReturn(leader(g, caller))
        `when`(attendanceRepo.saveAll(any<Iterable<MeetingAttendance>>())).thenReturn(emptyList())

        groupMeetingService.submitReport(m.publicId, SubmitMeetingReportRequest(entries = emptyList()), subject, false)

        verify(attendanceRepo).deleteAllByMeetingId(10L)
    }

    @Test
    fun `submitReport rejects a leader of another group with 403`() {
        val g = group(1L)
        val m = meeting(10L, g)
        val otherLeader = member(6L, grp = group(2L))
        `when`(meetingRepo.findByPublicId(m.publicId)).thenReturn(Optional.of(m))
        `when`(currentMember.require(subject)).thenReturn(otherLeader)
        `when`(leaderRepo.findActiveByGroupId(1L)).thenReturn(leader(g, member(5L, grp = g)))

        val ex =
            assertThrows<ResponseStatusException> {
                groupMeetingService.submitReport(m.publicId, SubmitMeetingReportRequest(entries = emptyList()), subject, false)
            }

        assertEquals(HttpStatus.FORBIDDEN, ex.statusCode)
        verify(attendanceRepo, never()).deleteAllByMeetingId(anyOrNull())
    }

    @Test
    fun `submitReport rejects a non-admin when the group has no active leader`() {
        val g = group(1L)
        val m = meeting(10L, g)
        `when`(meetingRepo.findByPublicId(m.publicId)).thenReturn(Optional.of(m))
        `when`(currentMember.require(subject)).thenReturn(member(5L, grp = g))
        `when`(leaderRepo.findActiveByGroupId(1L)).thenReturn(null)

        assertThrows<ResponseStatusException> {
            groupMeetingService.submitReport(m.publicId, SubmitMeetingReportRequest(entries = emptyList()), subject, false)
        }
    }

    // --- getMeetingDetails ---

    @Test
    fun `getMeetingDetails throws EntityNotFoundException when meeting not found`() {
        `when`(meetingRepo.findByPublicId(any())).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            groupMeetingService.getMeetingDetails(UUID.randomUUID(), subject, false)
        }
    }

    @Test
    fun `getMeetingDetails rejects a non-admin from another group with 403`() {
        val m = meeting(10L, group(1L, "그룹1"))
        `when`(meetingRepo.findByPublicId(m.publicId)).thenReturn(Optional.of(m))
        `when`(currentMember.require(subject)).thenReturn(member(2L, grp = group(2L, "그룹2")))

        val ex =
            assertThrows<ResponseStatusException> {
                groupMeetingService.getMeetingDetails(m.publicId, subject, false)
            }

        assertEquals(HttpStatus.FORBIDDEN, ex.statusCode)
        verify(attendanceRepo, never()).findAllByMeetingId(anyOrNull())
    }

    @Test
    fun `getMeetingDetails allows a non-admin from the same group`() {
        val g = group(1L, "다니엘조")
        val m = meeting(10L, g)
        `when`(meetingRepo.findByPublicId(m.publicId)).thenReturn(Optional.of(m))
        `when`(currentMember.require(subject)).thenReturn(member(2L, grp = g))
        `when`(attendanceRepo.findAllByMeetingId(10L)).thenReturn(emptyList())

        val detail = groupMeetingService.getMeetingDetails(m.publicId, subject, false)

        assertEquals(m.publicId, detail.publicId)
        assertEquals("다니엘조", detail.groupName)
    }

    @Test
    fun `getMeetingDetails lets an admin read any group without resolving a member`() {
        val m = meeting(10L, group(1L, "그룹1"))
        `when`(meetingRepo.findByPublicId(m.publicId)).thenReturn(Optional.of(m))
        `when`(attendanceRepo.findAllByMeetingId(10L)).thenReturn(emptyList())

        val detail = groupMeetingService.getMeetingDetails(m.publicId, subject, true)

        assertEquals("그룹1", detail.groupName)
        verifyNoInteractions(currentMember)
    }

    @Test
    fun `getMeetingDetails maps attendees with memberName and status`() {
        val g = group(1L)
        val m = meeting(10L, g)
        val att = member(2L, "길동", "홍", grp = g)
        val attendance = MeetingAttendance(meeting = m, member = att, status = "PRESENT", prayerRequest = "기도요청")
        `when`(meetingRepo.findByPublicId(m.publicId)).thenReturn(Optional.of(m))
        `when`(attendanceRepo.findAllByMeetingId(10L)).thenReturn(listOf(attendance))

        val detail = groupMeetingService.getMeetingDetails(m.publicId, subject, true)

        assertEquals(1, detail.attendees.size)
        with(detail.attendees[0]) {
            assertEquals("홍 길동", memberName)
            assertEquals(att.publicId.toString(), memberId)
            assertEquals("PRESENT", status)
            assertEquals("기도요청", prayerRequest)
        }
    }

    // --- getMeetings ---

    @Test
    fun `getMeetings returns all meetings for admin`() {
        val g = group(1L)
        `when`(meetingRepo.findAllByOrderByMeetingTimeDesc()).thenReturn(listOf(meeting(1L, g), meeting(2L, g)))

        val result = groupMeetingService.getMeetings(subject, true)

        assertEquals(2, result.size)
        verifyNoInteractions(currentMember)
    }

    @Test
    fun `getMeetings returns only the caller's group for non-admin`() {
        val g = group(1L, "다니엘조")
        `when`(currentMember.require(subject)).thenReturn(member(2L, grp = g))
        `when`(meetingRepo.findAllByGroupIdOrderByMeetingTimeDesc(1L)).thenReturn(listOf(meeting(1L, g)))

        val result = groupMeetingService.getMeetings(subject, false)

        assertEquals(1, result.size)
        assertEquals("다니엘조", result[0].groupName)
    }

    @Test
    fun `getMeetings returns empty list when non-admin has no group`() {
        `when`(currentMember.require(subject)).thenReturn(member(2L, grp = null))

        assertTrue(groupMeetingService.getMeetings(subject, false).isEmpty())
    }
}
