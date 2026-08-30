package com.hanmaum.dn.app.features.attendance.repository

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.domain.AttendanceLog
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * The personal-history query against real SQL. The service tests mock the repository, so
 * this is the only place the range bounds, the soft-delete filter and — most importantly —
 * the member scoping are actually exercised.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class MemberAttendanceLogRepositoryIT {
    @Autowired lateinit var repository: AttendanceLogRepository

    @Autowired lateinit var entityManager: EntityManager

    private lateinit var definition: AttendanceDefinition

    private fun newMember(firstName: String): Member = Member(lastName = "김", firstName = firstName).also { entityManager.persist(it) }

    private fun sundayDefinition(): AttendanceDefinition =
        AttendanceDefinition(
            title = "주일예배",
            dayOfWeek = DayOfWeek.SUNDAY,
            windowStart = LocalTime.of(10, 0),
            windowEnd = LocalTime.of(12, 0),
        ).also { entityManager.persist(it) }

    private fun log(
        member: Member,
        date: LocalDate,
    ): AttendanceLog =
        AttendanceLog(definition = definition, member = member, attendanceDate = date)
            .also { entityManager.persist(it) }

    @Test
    fun `returns only the given member's logs inside the range, newest first`() {
        definition = sundayDefinition()
        val me = newMember("철수")
        val someoneElse = newMember("영희")

        log(me, LocalDate.of(2026, 8, 2))
        log(me, LocalDate.of(2026, 8, 16))
        log(me, LocalDate.of(2026, 8, 23))
        // Outside the range on either side.
        log(me, LocalDate.of(2026, 7, 26))
        log(me, LocalDate.of(2026, 8, 30))
        // Another member's check-in on a date inside the range.
        log(someoneElse, LocalDate.of(2026, 8, 9))
        entityManager.flush()
        entityManager.clear()

        val result =
            repository.findMemberLogsBetween(me.id!!, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 23))

        assertEquals(
            listOf(LocalDate.of(2026, 8, 23), LocalDate.of(2026, 8, 16), LocalDate.of(2026, 8, 2)),
            result.map { it.attendanceDate },
        )
        assertTrue(result.all { it.member?.id == me.id })
    }

    @Test
    fun `the range bounds are inclusive on both ends`() {
        definition = sundayDefinition()
        val me = newMember("철수")
        log(me, LocalDate.of(2026, 8, 2))
        log(me, LocalDate.of(2026, 8, 23))
        entityManager.flush()

        val result =
            repository.findMemberLogsBetween(me.id!!, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 23))

        assertEquals(2, result.size)
    }

    @Test
    fun `a soft-deleted log disappears from the history`() {
        definition = sundayDefinition()
        val me = newMember("철수")
        val kept = log(me, LocalDate.of(2026, 8, 2))
        val removed = log(me, LocalDate.of(2026, 8, 9))
        entityManager.flush()

        removed.deletedAt = Instant.now()
        entityManager.flush()

        val result =
            repository.findMemberLogsBetween(me.id!!, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 30))

        assertEquals(listOf(kept.attendanceDate), result.map { it.attendanceDate })
    }

    @Test
    fun `the definition is loaded with the log so the title needs no second query`() {
        definition = sundayDefinition()
        val me = newMember("철수")
        log(me, LocalDate.of(2026, 8, 2))
        entityManager.flush()
        entityManager.clear()

        val result =
            repository.findMemberLogsBetween(me.id!!, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 30))

        // Reading the title after the persistence context was cleared would fail on a lazy
        // proxy if the JOIN FETCH ever regressed to a plain JOIN.
        assertEquals("주일예배", result.single().definition.title)
    }
}
