package com.hanmaum.dn.app.features.newcomers.repository

import com.hanmaum.dn.app.common.domainvalue.CheckInPresence
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.repository.AttendanceLogRepository
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerProfile
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerVisit
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class NewcomerVisitRepositoryIT {
    @Autowired lateinit var repository: NewcomerVisitRepository

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Autowired lateinit var attendanceLogRepository: AttendanceLogRepository

    private val sunday = LocalDate.of(2026, 6, 14)

    private fun visit(
        firstName: String,
        date: LocalDate = sunday,
        profile: NewcomerProfile? = null,
        definition: AttendanceDefinition? = null,
    ) = NewcomerVisit(
        visitDate = date,
        lastName = "홍",
        firstName = firstName,
        newcomerProfile = profile,
        definition = definition,
    ).also { entityManager.persist(it) }

    private fun sundayService(
        title: String = "주일예배",
        start: LocalTime = LocalTime.of(10, 0),
    ) = AttendanceDefinition(
        title = title,
        dayOfWeek = DayOfWeek.SUNDAY,
        windowStart = start,
        windowEnd = start.plusHours(2),
    ).also { entityManager.persist(it) }

    @Test
    fun `findInRange keeps the range, newest day first, and drops soft-deleted rows`() {
        visit("길동", date = sunday.minusWeeks(1))
        visit("길순")
        visit("길자").deletedAt = Instant.now()
        visit("길남", date = sunday.minusWeeks(2))
        entityManager.flush()
        entityManager.clear()

        val rows = repository.findInRange(sunday.minusWeeks(1), sunday)

        assertEquals(listOf("길순", "길동"), rows.map { it.firstName })
    }

    @Test
    fun `findPageInRange pages in the same order and counts only active rows`() {
        visit("길동", date = sunday.minusYears(2))
        visit("길순")
        visit("길자").deletedAt = Instant.now()
        visit("길남", date = sunday.minusWeeks(2))
        entityManager.flush()
        entityManager.clear()

        val first = repository.findPageInRange(LocalDate.of(2000, 1, 1), sunday, PageRequest.of(0, 2))
        val second = repository.findPageInRange(LocalDate.of(2000, 1, 1), sunday, PageRequest.of(1, 2))

        assertEquals(3, first.totalElements)
        assertEquals(listOf("길순", "길남"), first.content.map { it.firstName })
        assertEquals(listOf("길동"), second.content.map { it.firstName })
    }

    @Test
    fun `hardDeleteVisitedBefore removes old visits, deleted or not`() {
        visit("길동", date = sunday.minusYears(3).minusDays(1))
        visit("길자", date = sunday.minusYears(4)).deletedAt = Instant.now()
        visit("길순", date = sunday.minusYears(3))
        entityManager.flush()

        val removed = repository.hardDeleteVisitedBefore(sunday.minusYears(3))

        assertEquals(2, removed)
        assertEquals(listOf("길순"), repository.findAll().map { it.firstName })
    }

    @Test
    fun `hardDeleteSoftDeletedBefore removes only rows deleted before the cutoff`() {
        val cutoff = Instant.parse("2026-06-01T00:00:00Z")
        visit("길동").deletedAt = cutoff.minusSeconds(60)
        visit("길자").deletedAt = cutoff.plusSeconds(60)
        visit("길순")
        entityManager.flush()

        val removed = repository.hardDeleteSoftDeletedBefore(cutoff)

        assertEquals(1, removed)
        assertEquals(setOf("길자", "길순"), repository.findAll().map { it.firstName }.toSet())
    }

    @Test
    fun `names are stored encrypted`() {
        val row = visit("길동")
        entityManager.flush()

        val stored = jdbcTemplate.queryForObject("SELECT first_name FROM newcomer_visits WHERE id = ?", String::class.java, row.id)

        assertTrue(stored != "길동")
    }

    @Test
    fun `a purged newcomer profile leaves the visit without a link`() {
        val member = Member(lastName = "홍", firstName = "길동").also { entityManager.persist(it) }
        val profile = NewcomerProfile(member = member).also { entityManager.persist(it) }
        val row = visit("길동", profile = profile)
        entityManager.flush()
        entityManager.clear()
        jdbcTemplate.update("DELETE FROM newcomer_profiles WHERE id = ?", profile.id)

        val reloaded = repository.findActive(row.publicId).orElseThrow()

        assertNull(reloaded.newcomerProfile)
    }

    @Test
    fun `countQuickRecordsWithoutCheckIn skips checked-in links, other days, other definitions and deleted rows`() {
        val definition = sundayService()
        val afternoon = sundayService(title = "오후예배", start = LocalTime.of(14, 0))
        val checkedIn = Member(lastName = "홍", firstName = "길동").also { entityManager.persist(it) }
        val notCheckedIn = Member(lastName = "Doe", firstName = "John").also { entityManager.persist(it) }
        visit("길동", profile = NewcomerProfile(member = checkedIn).also { entityManager.persist(it) }, definition = definition)
        visit("John", profile = NewcomerProfile(member = notCheckedIn).also { entityManager.persist(it) }, definition = definition)
        visit("길순", definition = definition)
        visit("길자", definition = definition).deletedAt = Instant.now()
        visit("길남", date = sunday.minusWeeks(1), definition = definition)
        visit("길수", definition = afternoon)
        visit("길영")
        entityManager.flush()
        attendanceLogRepository.insertIfAbsent(
            publicId = UUID.randomUUID(),
            definitionId = definition.id!!,
            memberId = checkedIn.id!!,
            groupId = null,
            attendanceDate = sunday,
            presence = CheckInPresence.UNCONFIRMED.name,
        )

        val count = repository.countQuickRecordsWithoutCheckIn(sunday, definition.id!!)

        assertEquals(2, count)
    }

    @Test
    fun `quick records of a day without check-ins stay on their definition after it is replaced`() {
        val original = sundayService()
        visit("길동", definition = original)
        visit("길순", definition = original)
        entityManager.flush()
        original.isActive = false
        val replacement = sundayService(title = "새 주일예배")
        entityManager.flush()

        assertEquals(2, repository.countQuickRecordsWithoutCheckIn(sunday, original.id!!))
        assertEquals(0, repository.countQuickRecordsWithoutCheckIn(sunday, replacement.id!!))
    }

    @Test
    fun `countRosteredQuickRecordsWithoutCheckIn counts only quick records of active 새가족 members`() {
        val definition = sundayService()
        val newcomers = ChurchGroup(division = "청년부", name = "새가족").also { entityManager.persist(it) }
        val other = ChurchGroup(division = "청년부", name = "다니엘조").also { entityManager.persist(it) }

        fun linkedVisit(
            firstName: String,
            group: ChurchGroup?,
            status: MemberStatus = MemberStatus.ACTIVE,
            deleted: Boolean = false,
        ) {
            val member =
                Member(lastName = "홍", firstName = firstName).also {
                    it.group = group
                    it.memberStatus = status
                    if (deleted) it.deletedAt = Instant.now()
                    entityManager.persist(it)
                }
            visit(firstName, profile = NewcomerProfile(member = member).also { entityManager.persist(it) }, definition = definition)
        }
        linkedVisit("길동", newcomers)
        linkedVisit("길순", newcomers, status = MemberStatus.PENDING)
        linkedVisit("길자", newcomers, deleted = true)
        linkedVisit("길남", other)
        linkedVisit("길수", null)
        visit("길영", definition = definition)
        entityManager.flush()

        assertEquals(6, repository.countQuickRecordsWithoutCheckIn(sunday, definition.id!!))
        assertEquals(1, repository.countRosteredQuickRecordsWithoutCheckIn(sunday, definition.id!!, newcomers.id!!))
    }

    @Test
    fun `findPinnedDefinitionsOn returns the day's definitions, first pinned first`() {
        val afternoon = sundayService(title = "오후예배", start = LocalTime.of(14, 0))
        val morning = sundayService()
        visit("길동", definition = afternoon)
        visit("길순", definition = morning)
        visit("길자")
        visit("길남", date = sunday.minusWeeks(1), definition = morning)
        entityManager.flush()

        val pinned = repository.findPinnedDefinitionsOn(sunday)

        assertEquals(listOf(afternoon.id, morning.id), pinned.map { it.id })
    }
}
