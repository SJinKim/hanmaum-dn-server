package com.hanmaum.dn.app.features.groups.service

import com.hanmaum.dn.app.common.exception.EntityNotFoundException
import com.hanmaum.dn.app.features.groups.api.v1.dto.CreateMeetingRequest
import com.hanmaum.dn.app.features.groups.api.v1.dto.ReportEntry
import com.hanmaum.dn.app.features.groups.api.v1.dto.SubmitMeetingReportRequest
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.domain.GroupMeeting
import com.hanmaum.dn.app.features.groups.domain.MeetingAttendance
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.groups.repository.GroupMeetingRepository
import com.hanmaum.dn.app.features.groups.repository.MeetingAttendanceRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class GroupMeetingServiceTest {

    @Mock private lateinit var meetingRepo: GroupMeetingRepository
    @Mock private lateinit var attendanceRepo: MeetingAttendanceRepository
    @Mock private lateinit var groupRepo: ChurchGroupRepository
    @Mock private lateinit var memberRepo: MemberRepository

    @InjectMocks
    private lateinit var groupMeetingService: GroupMeetingService

    private fun group(id: Long, name: String = "다니엘조"): ChurchGroup {
        val g = ChurchGroup(name = name)
        g.id = id
        return g
    }

    private fun member(id: Long, firstName: String = "철수", lastName: String = "김", grp: ChurchGroup? = null): Member {
        val m = Member(lastName = lastName, firstName = firstName)
        m.id = id
        m.group = grp
        return m
    }

    private fun meeting(id: Long, group: ChurchGroup): GroupMeeting {
        val m = GroupMeeting(
            group = group,
            meetingTime = LocalDateTime.of(2026, 3, 22, 14, 0),
            location = "교회",
        )
        m.id = id
        return m
    }

    // --- createMeeting ---

    @Test
    fun `createMeeting throws EntityNotFoundException when group not found`() {
        `when`(groupRepo.findById(anyLong())).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            groupMeetingService.createMeeting(
                CreateMeetingRequest(groupId = 1L, meetingTime = LocalDateTime.now(), location = "교회")
            )
        }
    }

    @Test
    fun `createMeeting saves meeting and returns its id`() {
        val g = group(1L)
        val saved = meeting(42L, g)
        `when`(groupRepo.findById(1L)).thenReturn(Optional.of(g))
        `when`(meetingRepo.save(any(GroupMeeting::class.java))).thenReturn(saved)

        val id = groupMeetingService.createMeeting(
            CreateMeetingRequest(groupId = 1L, meetingTime = LocalDateTime.of(2026, 4, 6, 14, 0), location = "교회")
        )

        assertEquals(42L, id)
    }

    // --- submitReport ---

    @Test
    fun `submitReport throws EntityNotFoundException when meeting not found`() {
        `when`(meetingRepo.findById(anyLong())).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            groupMeetingService.submitReport(99L, SubmitMeetingReportRequest(entries = emptyList()))
        }
    }

    @Test
    fun `submitReport deletes old attendances before saving new ones`() {
        val g = group(1L)
        val m = meeting(10L, g)
        `when`(meetingRepo.findById(10L)).thenReturn(Optional.of(m))
        `when`(attendanceRepo.saveAll(any())).thenReturn(emptyList())

        groupMeetingService.submitReport(10L, SubmitMeetingReportRequest(entries = emptyList()))

        verify(attendanceRepo).deleteAllByMeetingId(10L)
        verify(attendanceRepo).saveAll(any())
    }

    @Test
    fun `submitReport maps isPresent=true to PRESENT status`() {
        val g = group(1L)
        val m = meeting(10L, g)
        val member1 = member(1L)
        val req = SubmitMeetingReportRequest(
            entries = listOf(ReportEntry(memberId = member1.publicId.toString(), isPresent = true, prayerRequest = "건강"))
        )
        `when`(meetingRepo.findById(10L)).thenReturn(Optional.of(m))
        `when`(memberRepo.findByPublicId(any(UUID::class.java))).thenReturn(Optional.of(member1))
        val captor = ArgumentCaptor.forClass(Iterable::class.java)
        `when`(attendanceRepo.saveAll(any())).thenReturn(emptyList())

        groupMeetingService.submitReport(10L, req)

        verify(attendanceRepo).saveAll(captor.capture())
        @Suppress("UNCHECKED_CAST")
        val saved = (captor.value as Iterable<MeetingAttendance>).toList()
        assertEquals("PRESENT", saved[0].status)
        assertEquals("건강", saved[0].prayerRequest)
    }

    @Test
    fun `submitReport maps isPresent=false to ABSENT status`() {
        val g = group(1L)
        val m = meeting(10L, g)
        val member1 = member(1L)
        val req = SubmitMeetingReportRequest(
            entries = listOf(ReportEntry(memberId = member1.publicId.toString(), isPresent = false, prayerRequest = null))
        )
        `when`(meetingRepo.findById(10L)).thenReturn(Optional.of(m))
        `when`(memberRepo.findByPublicId(any(UUID::class.java))).thenReturn(Optional.of(member1))
        val captor = ArgumentCaptor.forClass(Iterable::class.java)
        `when`(attendanceRepo.saveAll(any())).thenReturn(emptyList())

        groupMeetingService.submitReport(10L, req)

        verify(attendanceRepo).saveAll(captor.capture())
        @Suppress("UNCHECKED_CAST")
        val saved = (captor.value as Iterable<MeetingAttendance>).toList()
        assertEquals("ABSENT", saved[0].status)
    }

    // --- getMeetingDetails ---

    @Test
    fun `getMeetingDetails throws EntityNotFoundException when meeting not found`() {
        `when`(meetingRepo.findById(anyLong())).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            groupMeetingService.getMeetingDetails(99L, UUID.randomUUID().toString(), false)
        }
    }

    @Test
    fun `getMeetingDetails throws IllegalAccessException when non-admin from different group`() {
        val g1 = group(1L, "그룹1")
        val g2 = group(2L, "그룹2")
        val m = meeting(10L, g1)
        val requester = member(2L, grp = g2)
        `when`(meetingRepo.findById(10L)).thenReturn(Optional.of(m))
        `when`(memberRepo.findByPublicId(any(UUID::class.java))).thenReturn(Optional.of(requester))

        assertThrows<IllegalAccessException> {
            groupMeetingService.getMeetingDetails(10L, requester.publicId.toString(), false)
        }
    }

    @Test
    fun `getMeetingDetails allows non-admin from same group`() {
        val g = group(1L, "다니엘조")
        val m = meeting(10L, g)
        val requester = member(2L, grp = g)
        `when`(meetingRepo.findById(10L)).thenReturn(Optional.of(m))
        `when`(memberRepo.findByPublicId(any(UUID::class.java))).thenReturn(Optional.of(requester))
        `when`(attendanceRepo.findAllByMeetingId(10L)).thenReturn(emptyList())

        val detail = groupMeetingService.getMeetingDetails(10L, requester.publicId.toString(), false)

        assertEquals(10L, detail.id)
        assertEquals("다니엘조", detail.groupName)
    }

    @Test
    fun `getMeetingDetails allows admin regardless of group`() {
        val g1 = group(1L, "그룹1")
        val g2 = group(2L, "그룹2")
        val m = meeting(10L, g1)
        val admin = member(99L, grp = g2)
        `when`(meetingRepo.findById(10L)).thenReturn(Optional.of(m))
        `when`(attendanceRepo.findAllByMeetingId(10L)).thenReturn(emptyList())

        val detail = groupMeetingService.getMeetingDetails(10L, admin.publicId.toString(), true)

        assertEquals("그룹1", detail.groupName)
    }

    @Test
    fun `getMeetingDetails maps attendees with memberName and status`() {
        val g = group(1L)
        val m = meeting(10L, g)
        val att = member(2L, "영희", "이", grp = g)
        val attendance = MeetingAttendance(meeting = m, member = att, status = "PRESENT", prayerRequest = "기도요청")
        `when`(meetingRepo.findById(10L)).thenReturn(Optional.of(m))
        `when`(attendanceRepo.findAllByMeetingId(10L)).thenReturn(listOf(attendance))

        val detail = groupMeetingService.getMeetingDetails(10L, att.publicId.toString(), true)

        assertEquals(1, detail.attendees.size)
        with(detail.attendees[0]) {
            assertEquals("이 영희", memberName)
            assertEquals(att.publicId.toString(), memberId)
            assertEquals("PRESENT", status)
            assertEquals("기도요청", prayerRequest)
        }
    }

    // --- getMeetings ---

    @Test
    fun `getMeetings returns all meetings ordered by time for admin`() {
        val g = group(1L)
        val meetings = listOf(meeting(1L, g), meeting(2L, g))
        `when`(meetingRepo.findAllByOrderByMeetingTimeDesc()).thenReturn(meetings)

        val result = groupMeetingService.getMeetings("any-id", true)

        assertEquals(2, result.size)
    }

    @Test
    fun `getMeetings returns only own group meetings for non-admin`() {
        val g = group(1L, "다니엘조")
        val requester = member(2L, grp = g)
        `when`(memberRepo.findByPublicId(any(UUID::class.java))).thenReturn(Optional.of(requester))
        `when`(meetingRepo.findAllByGroupIdOrderByMeetingTimeDesc(1L)).thenReturn(listOf(meeting(1L, g)))

        val result = groupMeetingService.getMeetings(requester.publicId.toString(), false)

        assertEquals(1, result.size)
        assertEquals("다니엘조", result[0].groupName)
    }

    @Test
    fun `getMeetings returns empty list when non-admin has no group`() {
        val requester = member(2L, grp = null)
        `when`(memberRepo.findByPublicId(any(UUID::class.java))).thenReturn(Optional.of(requester))

        val result = groupMeetingService.getMeetings(requester.publicId.toString(), false)

        assertTrue(result.isEmpty())
    }
}
