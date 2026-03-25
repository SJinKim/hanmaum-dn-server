package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.features.attendance.api.v1.dto.CheckInRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.CreateDefinitionRequest
import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.domain.AttendanceLog
import com.hanmaum.dn.app.features.attendance.repository.AttendanceDefinitionRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceLogRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AttendanceServiceTest {
    @Mock private lateinit var defRepo: AttendanceDefinitionRepository

    @Mock private lateinit var logRepo: AttendanceLogRepository

    @Mock private lateinit var memberRepo: MemberRepository

    @InjectMocks
    private lateinit var attendanceService: AttendanceService

    /** A definition whose time window spans the entire day — always matches the current time. */
    private fun alwaysActiveDefinition(title: String = "주일 예배"): AttendanceDefinition =
        AttendanceDefinition(
            dayOfWeek = DayOfWeek.SUNDAY,
            startTime = LocalTime.MIN,
            endTime = LocalTime.MAX,
            title = title,
        )

    private fun memberWithId(id: Long): Member {
        val m = Member(lastName = "김", firstName = "철수")
        m.id = id
        return m
    }

    // --- getCheckInStatus ---

    @Test
    fun `getCheckInStatus returns active and not checked in when window open`() {
        val member = memberWithId(1L)
        `when`(defRepo.findByDayOfWeekAndActiveTrue(any<DayOfWeek>()))
            .thenReturn(listOf(alwaysActiveDefinition("주일 예배")))
        `when`(memberRepo.findByPublicId(any<UUID>())).thenReturn(Optional.of(member))
        `when`(logRepo.existsByMemberIdAndDateAndCategory(anyLong(), any<LocalDate>(), any<String>()))
            .thenReturn(false)

        val result = attendanceService.getCheckInStatus(member.publicId.toString())

        assertTrue(result.isCheckInActive)
        assertFalse(result.alreadyCheckedIn)
        assertEquals("주일 예배", result.activeDefinitionTitle)
        assertEquals("Check-in offen! 👋", result.message)
    }

    @Test
    fun `getCheckInStatus returns inactive when member already checked in`() {
        val member = memberWithId(1L)
        `when`(defRepo.findByDayOfWeekAndActiveTrue(any<DayOfWeek>()))
            .thenReturn(listOf(alwaysActiveDefinition()))
        `when`(memberRepo.findByPublicId(any<UUID>())).thenReturn(Optional.of(member))
        `when`(logRepo.existsByMemberIdAndDateAndCategory(anyLong(), any<LocalDate>(), any<String>()))
            .thenReturn(true)

        val result = attendanceService.getCheckInStatus(member.publicId.toString())

        assertFalse(result.isCheckInActive)
        assertTrue(result.alreadyCheckedIn)
        assertEquals("Du bist bereits eingecheckt ✅", result.message)
    }

    @Test
    fun `getCheckInStatus returns inactive when no active definition for today`() {
        val member = memberWithId(1L)
        `when`(defRepo.findByDayOfWeekAndActiveTrue(any<DayOfWeek>())).thenReturn(emptyList())
        `when`(memberRepo.findByPublicId(any<UUID>())).thenReturn(Optional.of(member))
        `when`(logRepo.existsByMemberIdAndDateAndCategory(anyLong(), any<LocalDate>(), any<String>()))
            .thenReturn(false)

        val result = attendanceService.getCheckInStatus(member.publicId.toString())

        assertFalse(result.isCheckInActive)
        assertNull(result.activeDefinitionTitle)
        assertEquals("Kein Check-in Zeitraum aktiv 💤", result.message)
    }

    @Test
    fun `getCheckInStatus throws EntityNotFoundException when member not found`() {
        `when`(defRepo.findByDayOfWeekAndActiveTrue(any<DayOfWeek>())).thenReturn(emptyList())
        `when`(memberRepo.findByPublicId(any<UUID>())).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            attendanceService.getCheckInStatus(UUID.randomUUID().toString())
        }
    }

    // --- checkIn ---

    @Test
    fun `checkIn throws IllegalStateException when already checked in`() {
        val member = memberWithId(1L)
        `when`(defRepo.findByDayOfWeekAndActiveTrue(any<DayOfWeek>()))
            .thenReturn(listOf(alwaysActiveDefinition()))
        `when`(memberRepo.findByPublicId(any<UUID>())).thenReturn(Optional.of(member))
        `when`(logRepo.existsByMemberIdAndDateAndCategory(anyLong(), any<LocalDate>(), any<String>()))
            .thenReturn(true)

        val ex =
            assertThrows<IllegalStateException> {
                attendanceService.checkIn(CheckInRequest(memberId = member.publicId.toString()))
            }
        assertEquals("Already checked in today", ex.message)
    }

    @Test
    fun `checkIn throws IllegalStateException when check-in window is closed`() {
        val member = memberWithId(1L)
        `when`(defRepo.findByDayOfWeekAndActiveTrue(any<DayOfWeek>())).thenReturn(emptyList())
        `when`(memberRepo.findByPublicId(any<UUID>())).thenReturn(Optional.of(member))
        `when`(logRepo.existsByMemberIdAndDateAndCategory(anyLong(), any<LocalDate>(), any<String>()))
            .thenReturn(false)

        val ex =
            assertThrows<IllegalStateException> {
                attendanceService.checkIn(CheckInRequest(memberId = member.publicId.toString()))
            }
        assertEquals("Check-in is currently closed", ex.message)
    }

    @Test
    fun `checkIn saves attendance log on success`() {
        val member = memberWithId(1L)
        `when`(defRepo.findByDayOfWeekAndActiveTrue(any<DayOfWeek>()))
            .thenReturn(listOf(alwaysActiveDefinition()))
        `when`(memberRepo.findByPublicId(any<UUID>())).thenReturn(Optional.of(member))
        `when`(logRepo.existsByMemberIdAndDateAndCategory(anyLong(), any<LocalDate>(), any<String>()))
            .thenReturn(false)
        `when`(logRepo.save(any<AttendanceLog>())).thenAnswer { it.arguments[0] }

        attendanceService.checkIn(CheckInRequest(memberId = member.publicId.toString()))

        verify(logRepo).save(any<AttendanceLog>())
    }

    // --- createDefinition ---

    @Test
    fun `createDefinition saves definition and returns its id`() {
        val req =
            CreateDefinitionRequest(
                dayOfWeek = DayOfWeek.SUNDAY,
                startTime = LocalTime.of(11, 0),
                endTime = LocalTime.of(13, 0),
                title = "Gottesdienst",
            )
        val saved =
            AttendanceDefinition(
                dayOfWeek = req.dayOfWeek,
                startTime = req.startTime,
                endTime = req.endTime,
                title = req.title,
            )
        saved.id = 7L
        `when`(defRepo.save(any<AttendanceDefinition>())).thenReturn(saved)

        val id = attendanceService.createDefinition(req)

        assertEquals(7L, id)
    }

    // --- getMyHistory ---

    @Test
    fun `getMyHistory throws EntityNotFoundException when member not found`() {
        `when`(memberRepo.findByPublicId(any<UUID>())).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            attendanceService.getMyHistory(UUID.randomUUID().toString())
        }
    }

    @Test
    fun `getMyHistory returns empty list when no logs`() {
        val member = memberWithId(1L)
        `when`(memberRepo.findByPublicId(any<UUID>())).thenReturn(Optional.of(member))
        `when`(logRepo.findAllByMemberIdOrderByDateDesc(1L)).thenReturn(emptyList())

        val result = attendanceService.getMyHistory(member.publicId.toString())

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getMyHistory returns mapped dto list`() {
        val member = memberWithId(1L)
        val log =
            AttendanceLog(
                member = member,
                date = LocalDate.of(2026, 3, 22),
                category = "SUNDAY_SERVICE",
                status = "PRESENT",
            )
        log.id = 100L
        `when`(memberRepo.findByPublicId(any<UUID>())).thenReturn(Optional.of(member))
        `when`(logRepo.findAllByMemberIdOrderByDateDesc(1L)).thenReturn(listOf(log))

        val result = attendanceService.getMyHistory(member.publicId.toString())

        assertEquals(1, result.size)
        assertEquals(100L, result[0].id)
        assertEquals(LocalDate.of(2026, 3, 22), result[0].date)
        assertEquals("SUNDAY_SERVICE", result[0].category)
        assertEquals("PRESENT", result[0].status)
        assertNull(result[0].checkInTime)
    }
}
