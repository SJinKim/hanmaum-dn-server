package com.hanmaum.dn.app.features.attendance.repository

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.domain.AttendanceLog
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.sql.Timestamp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class AttendanceRosterRepositoryIT {
    @Autowired lateinit var repository: AttendanceLogRepository

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    private val sunday = LocalDate.of(2026, 6, 14)

    private fun definition(title: String) =
        AttendanceDefinition(
            title = title,
            dayOfWeek = DayOfWeek.SUNDAY,
            windowStart = LocalTime.of(10, 0),
            windowEnd = LocalTime.of(12, 0),
        ).also { entityManager.persist(it) }

    private fun member(
        firstName: String,
        group: ChurchGroup?,
    ) = Member(lastName = "홍", firstName = firstName, group = group).also { entityManager.persist(it) }

    /** Persists a log and pins its created_at, which @CreationTimestamp would otherwise set to now. */
    private fun log(
        definition: AttendanceDefinition,
        member: Member,
        group: ChurchGroup?,
        checkedInAt: Instant,
        date: LocalDate = sunday,
    ): AttendanceLog {
        val log = AttendanceLog(definition = definition, member = member, attendanceDate = date, groupAtCheckIn = group)
        entityManager.persist(log)
        entityManager.flush()
        jdbcTemplate.update("UPDATE attendance_logs SET created_at = ? WHERE id = ?", Timestamp.from(checkedInAt), log.id)
        return log
    }

    @Test
    fun `roster filters by date and definition, oldest check-in first, and drops soft-deleted rows`() {
        val group = ChurchGroup(division = "청년부", name = "1순").also { entityManager.persist(it) }
        val worship = definition("주일예배")
        val prayer = definition("기도회")
        val early = member("길동", group)
        val late = member("길순", group)
        val removed = member("길자", group)
        val otherDefinition = member("길남", group)
        val otherDay = member("길녀", group)

        log(worship, late, group, Instant.parse("2026-06-14T08:20:00Z"))
        log(worship, early, group, Instant.parse("2026-06-14T08:05:00Z"))
        log(worship, removed, group, Instant.parse("2026-06-14T08:10:00Z")).deletedAt = Instant.now()
        log(prayer, otherDefinition, null, Instant.parse("2026-06-14T09:00:00Z"))
        log(worship, otherDay, group, Instant.parse("2026-06-07T08:00:00Z"), date = sunday.minusWeeks(1))
        entityManager.flush()
        entityManager.clear()

        val worshipRoster = repository.findRoster(sunday, worship.id)
        val wholeDay = repository.findRoster(sunday, null)

        assertEquals(listOf("홍길동", "홍길순"), worshipRoster.map { it.member?.getFullName() })
        assertEquals("1순", worshipRoster.first().groupAtCheckIn?.name)
        assertEquals(listOf("홍길동", "홍길순", "홍길남"), wholeDay.map { it.member?.getFullName() })
        assertNull(wholeDay.last().groupAtCheckIn)
    }

    @Test
    fun `roster keeps a purged member's row without the member`() {
        val group = ChurchGroup(division = "청년부", name = "2순").also { entityManager.persist(it) }
        val worship = definition("주일예배")
        val member = member("길동", group)
        log(worship, member, group, Instant.parse("2026-06-14T08:05:00Z"))

        entityManager.flush()
        entityManager.clear()
        // The hard purge goes through the database: member_id is ON DELETE SET NULL.
        jdbcTemplate.update("DELETE FROM members WHERE id = ?", member.id)

        val row = repository.findRoster(sunday, worship.id).single()

        assertNull(row.member)
        assertEquals("2순", row.groupAtCheckIn?.name)
    }
}
