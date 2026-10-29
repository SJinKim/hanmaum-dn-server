package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.attendance.LongAbsenceFixtures
import com.hanmaum.dn.app.features.attendance.MutableClock
import com.hanmaum.dn.app.features.attendance.NAMED_SERVICE_PROPERTY
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceContactStatusRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceMemoRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceMemoUpdate
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceResponse
import com.hanmaum.dn.app.features.attendance.berlin
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerLifecycle
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerProfile
import com.hanmaum.dn.app.features.newcomers.domain.PostAssignmentAttendance
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HDN-375 against PostgreSQL: which check-ins count, who is listed, and how the follow-up of a
 * phase is written, closed and purged. Today is Monday 2026-10-26 unless a test moves the clock,
 * so a member is listed when the last Sunday is 2026-07-26 or earlier.
 */
@DataJpaTest(properties = ["spring.jpa.show-sql=false", NAMED_SERVICE_PROPERTY])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
@Import(
    PiiCryptoConfiguration::class,
    LongAbsenceService::class,
    LongAbsenceRetentionService::class,
    SundayAttendancePolicy::class,
    LongAbsenceServiceIT.ClockConfig::class,
)
class LongAbsenceServiceIT {
    @TestConfiguration
    class ClockConfig {
        @Bean
        fun clock() = MutableClock(berlin("2026-10-26", "10:00"))
    }

    @Autowired private lateinit var service: LongAbsenceService

    @Autowired private lateinit var retention: LongAbsenceRetentionService

    @Autowired private lateinit var clock: MutableClock

    @Autowired private lateinit var entityManager: EntityManager

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var fx: LongAbsenceFixtures

    @BeforeEach
    fun setUp() {
        clock.setToday(LocalDate.of(2026, 10, 26))
        fx = LongAbsenceFixtures(entityManager, jdbcTemplate)
    }

    private fun listed(): Map<UUID, LongAbsenceResponse> = service.list().associateBy { it.memberPublicId }

    private fun d(text: String) = LocalDate.parse(text)

    /** A member whose January Sunday qualifies, so every test probe either moves L or not. */
    private fun absentee(): Member = fx.member().also { fx.log(it, "2026-01-04") }

    private fun followupRows(member: Member): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM attendance_absence_followups WHERE member_id = ?", Int::class.java, member.id)!!

    private fun memo(
        member: Member,
        value: String?,
        version: Long,
        lastSundayDate: String = "2026-01-04",
    ) = service.setMemo(member.publicId, LongAbsenceMemoRequest(d(lastSundayDate), version, value))

    private fun contact(
        member: Member,
        contacted: Boolean,
        version: Long,
        actor: String = "kc-pastor",
        memoUpdate: LongAbsenceMemoUpdate? = null,
    ) = service.setContactStatus(
        member.publicId,
        LongAbsenceContactStatusRequest(d("2026-01-04"), version, contacted, memoUpdate),
        actor,
    )

    // ─── Which check-ins count (AK 3) ───────────────────────────────────────

    @Test
    fun `plain check-ins count only on Sunday 08-00 to 15-00 Berlin on the visit day, named services always`() {
        val probes =
            listOf(
                // date, created at (Berlin), expected last Sunday
                Triple("2026-06-14", berlin("2026-06-14", "08:00:00"), "2026-06-14"),
                Triple("2026-06-14", berlin("2026-06-14", "07:59:59"), "2026-01-04"),
                Triple("2026-06-14", berlin("2026-06-14", "15:00:00"), "2026-06-14"),
                Triple("2026-06-14", berlin("2026-06-14", "15:00:00.001"), "2026-01-04"),
                Triple("2026-06-14", berlin("2026-06-14", "16:00"), "2026-01-04"),
                // a Saturday visit inside the hours
                Triple("2026-06-13", berlin("2026-06-13", "10:00"), "2026-01-04"),
                // back-filled a week later, within Sunday hours: no time of the visit itself
                Triple("2026-06-14", berlin("2026-06-21", "10:00"), "2026-01-04"),
                // DST starts on 2026-03-29 (08:00 Berlin = 06:00 UTC)
                Triple("2026-03-29", Instant.parse("2026-03-29T06:00:00Z"), "2026-03-29"),
                Triple("2026-03-29", Instant.parse("2026-03-29T05:59:59Z"), "2026-01-04"),
            )
        val members = probes.map { (date, createdAt, _) -> absentee().also { fx.log(it, date, createdAt, fx.other) } }
        // A named 2부 log counts although it was back-filled on a Wednesday evening.
        val backFilled = absentee().also { fx.log(it, "2026-06-21", berlin("2026-06-24", "20:00")) }

        val rows = listed()

        members.zip(probes).forEach { (member, probe) ->
            assertEquals(d(probe.third), rows.getValue(member.publicId).lastSundayDate, "probe ${probe.first} ${probe.second}")
        }
        assertEquals(d("2026-06-21"), rows.getValue(backFilled.publicId).lastSundayDate)
    }

    @Test
    fun `a recent check-in on the day DST ends removes the member, one minute early does not`() {
        // 2026-10-25: 08:00 Berlin is 07:00 UTC after the switch back.
        val returned = absentee().also { fx.log(it, "2026-10-25", Instant.parse("2026-10-25T07:00:00Z"), fx.other) }
        val early = absentee().also { fx.log(it, "2026-10-25", Instant.parse("2026-10-25T06:59:00Z"), fx.other) }

        val rows = listed()

        assertNull(rows[returned.publicId])
        assertEquals(d("2026-01-04"), rows.getValue(early.publicId).lastSundayDate)
    }

    @Test
    fun `deactivated or deleted named definitions keep their logs, unaccepted or deleted logs never count`() {
        val retired = fx.named
        val kept = absentee()
        jdbcTemplate.update("UPDATE attendance_definitions SET is_active = FALSE, deleted_at = now() WHERE id = ?", retired.id)
        val notAttended = fx.member().also { fx.log(it, "2026-01-04", attended = false) }
        val deletedLog = fx.member().also { fx.log(it, "2026-01-04") }
        jdbcTemplate.update("UPDATE attendance_logs SET deleted_at = now() WHERE member_id = ?", deletedLog.id)

        val rows = listed()

        assertNotNull(rows[kept.publicId])
        assertNull(rows[notAttended.publicId])
        assertNull(rows[deletedLog.publicId])
    }

    // ─── Who is listed (AK 1, 2, 4–7, 9) ────────────────────────────────────

    @Test
    fun `boundary is three calendar months, today included`() {
        val onBoundary = fx.member().also { fx.log(it, "2026-07-26") }
        val dayAfter = fx.member().also { fx.log(it, "2026-08-02", berlin("2026-08-02", "10:00")) }

        val rows = listed()

        val row = rows.getValue(onBoundary.publicId)
        assertEquals(d("2026-07-27"), row.absentSince)
        assertEquals(92, row.absenceDays)
        assertEquals(3, row.absenceMonths)
        assertNull(rows[dayAfter.publicId])
    }

    @Test
    fun `only active app members outside newcomer care and not marked as moved on are listed`() {
        val listedMember = absentee()
        val notLinked = fx.member(linked = false).also { fx.log(it, "2026-01-04") }
        val pending = fx.member(status = MemberStatus.PENDING).also { fx.log(it, "2026-01-04") }
        val inactive = fx.member(status = MemberStatus.INACTIVE).also { fx.log(it, "2026-01-04") }
        val softDeleted = absentee().also { jdbcTemplate.update("UPDATE members SET deleted_at = now() WHERE id = ?", it.id) }
        val newcomerGroup = newcomerGroup()
        val inNewcomerGroup = fx.member(group = newcomerGroup).also { fx.log(it, "2026-01-04") }
        val submitted = absentee().also { profile(it, NewcomerLifecycle.SUBMITTED, null) }
        val inCare = absentee().also { profile(it, NewcomerLifecycle.IN_CARE, null) }
        val graduatedRegular = absentee().also { profile(it, NewcomerLifecycle.GRADUATED, PostAssignmentAttendance.REGULAR) }
        val changedChurch = absentee().also { profile(it, NewcomerLifecycle.GRADUATED, PostAssignmentAttendance.CHANGED_CHURCH) }
        val moved = absentee().also { profile(it, NewcomerLifecycle.GRADUATED, PostAssignmentAttendance.RETURNED_OR_MOVED) }
        val neverOnSunday = fx.member().also { fx.log(it, "2026-01-05", berlin("2026-01-05", "10:00"), fx.other) }
        val noGroup = fx.member(group = null).also { fx.log(it, "2026-01-04") }
        entityManager.flush()

        val rows = listed()

        assertEquals(fx.group.publicId, rows.getValue(listedMember.publicId).group?.publicId)
        assertNull(rows.getValue(listedMember.publicId).postAssignmentAttendance)
        assertEquals(PostAssignmentAttendance.REGULAR, rows.getValue(graduatedRegular.publicId).postAssignmentAttendance)
        assertNull(rows.getValue(noGroup.publicId).group)
        listOf(notLinked, pending, inactive, softDeleted, inNewcomerGroup, submitted, inCare, changedChurch, moved, neverOnSunday)
            .forEach { assertNull(rows[it.publicId], "member ${it.firstName} must not be listed") }
    }

    @Test
    fun `longest absence first, ties by member public id`() {
        val newer = fx.member().also { fx.log(it, "2026-06-14") }
        val older = fx.member().also { fx.log(it, "2025-12-28") }
        val tieA = fx.member().also { fx.log(it, "2026-03-01") }
        val tieB = fx.member().also { fx.log(it, "2026-03-01") }

        val mine = setOf(newer, older, tieA, tieB).map { it.publicId }.toSet()
        val order = service.list().map { it.memberPublicId }.filter { it in mine }

        val ties = listOf(tieA.publicId, tieB.publicId).sorted()
        assertEquals(listOf(older.publicId) + ties + newer.publicId, order)
    }

    @Test
    fun `summary counts the same candidates without names, contacted ones and six-month cut inclusive`() {
        val baseline = service.summary()
        val sixMonths = fx.member().also { fx.log(it, "2026-04-26") }
        val almostSix = fx.member().also { fx.log(it, "2026-05-03") }
        val contacted = absentee()
        fx.member().also { fx.log(it, "2026-10-04") }
        contact(contacted, true, 0)

        val summary = service.summary()

        assertEquals(d("2026-10-26"), summary.asOfDate)
        assertEquals(service.list().size.toLong(), summary.totalCount)
        assertEquals(3, summary.totalCount - baseline.totalCount)
        assertEquals(2, summary.uncontactedCount - baseline.uncontactedCount)
        assertEquals(1, summary.atLeastSixMonthsCount - baseline.atLeastSixMonthsCount)
        assertNotNull(listed()[sixMonths.publicId])
        assertNotNull(listed()[almostSix.publicId])
    }

    // ─── Follow-up writes (AK 10–14, 13a) ───────────────────────────────────

    @Test
    fun `reads write nothing, the first change creates version 1 and the memo is stored encrypted`() {
        val member = absentee()
        val before = listed().getValue(member.publicId)
        assertEquals(0, before.version)
        assertEquals(0, followupRows(member))

        val saved = memo(member, "회사 일로 바쁨", version = 0)

        assertEquals(1, saved.version)
        assertEquals("회사 일로 바쁨", saved.memo)
        val raw =
            jdbcTemplate.queryForObject(
                "SELECT memo FROM attendance_absence_followups WHERE member_id = ?",
                String::class.java,
                member.id,
            )
        assertNotEquals("회사 일로 바쁨", raw)
        assertTrue(raw!!.isNotBlank())
        entityManager.clear()
        assertEquals("회사 일로 바쁨", listed().getValue(member.publicId).memo)
    }

    @Test
    fun `a no-op on a missing row records nothing`() {
        val member = absentee()

        val result = contact(member, false, 0)

        assertEquals(0, result.version)
        assertEquals(0, followupRows(member))
    }

    @Test
    fun `contact keeps the first actor on repeat, take-back keeps the memo, memoUpdate is atomic`() {
        val pastor = fx.member(firstName = "목사")
        val member = absentee()
        memo(member, "전화 부탁", 0)

        val first = contact(member, true, 1, actor = pastor.keycloakId!!)
        assertTrue(first.contacted)
        assertEquals(pastor.keycloakId, first.contactedBy)
        assertEquals(pastor.publicId, first.contactedByMember?.publicId)
        assertEquals(clock.instant, first.contactedAt)
        assertEquals(2, first.version)

        clock.instant = clock.instant.plusSeconds(3600)
        val repeated = contact(member, true, 2, actor = "kc-admin-only")
        assertEquals(pastor.keycloakId, repeated.contactedBy)
        assertEquals(first.contactedAt, repeated.contactedAt)
        assertEquals(2, repeated.version)

        val takenBack = contact(member, false, 2)
        assertEquals(false, takenBack.contacted)
        assertNull(takenBack.contactedBy)
        assertNull(takenBack.contactedAt)
        assertEquals("전화 부탁", takenBack.memo)

        val withDetails = contact(member, true, takenBack.version, actor = "kc-admin-only", memoUpdate = LongAbsenceMemoUpdate("새 소식 있음"))
        assertEquals("새 소식 있음", withDetails.memo)
        assertEquals("kc-admin-only", withDetails.contactedBy)
        assertNull(withDetails.contactedByMember)

        val cleared = contact(member, true, withDetails.version, memoUpdate = LongAbsenceMemoUpdate(null))
        assertNull(cleared.memo)
        assertTrue(cleared.contacted)
        val text = memo(member, "text", cleared.version)
        assertNull(memo(member, "   ", text.version).memo)
    }

    @Test
    fun `stale version or phase is a 409 and changes nothing, unknown member is a 404`() {
        val member = absentee()
        memo(member, "first", 0)

        val stale = assertThrows<LongAbsenceException> { contact(member, true, 0, memoUpdate = LongAbsenceMemoUpdate("second")) }
        assertEquals(LongAbsenceException.VERSION_CONFLICT, stale.code)
        val wrongPhase = assertThrows<LongAbsenceException> { memo(member, "x", 1, lastSundayDate = "2025-12-28") }
        assertEquals(LongAbsenceException.NOT_CURRENT, wrongPhase.code)
        val recent = fx.member().also { fx.log(it, "2026-10-18") }
        val notListed = assertThrows<LongAbsenceException> { memo(recent, "x", 0, lastSundayDate = "2026-10-18") }
        assertEquals(LongAbsenceException.NOT_CURRENT, notListed.code)
        val unknown =
            assertThrows<LongAbsenceException> {
                service.setMemo(UUID.randomUUID(), LongAbsenceMemoRequest(d("2026-01-04"), 0, "x"))
            }
        assertEquals(LongAbsenceException.MEMBER_NOT_FOUND, unknown.code)

        entityManager.clear()
        val row = listed().getValue(member.publicId)
        assertEquals("first", row.memo)
        assertEquals(false, row.contacted)
        assertEquals(1, row.version)
    }

    // ─── Phases and retention (AK 13, #376 F3) ──────────────────────────────

    @Test
    fun `a return closes the phase, the next absence starts empty and the old memo is purged after 90 days`() {
        val member = absentee()
        memo(member, "old phase", 0)
        fx.log(member, "2026-07-05")
        retention.markReturned(member.id!!)

        val next = listed().getValue(member.publicId)
        assertEquals(d("2026-07-05"), next.lastSundayDate)
        assertNull(next.memo)
        assertEquals(0, next.version)
        assertEquals(
            d("2026-07-05"),
            jdbcTemplate.queryForObject(
                "SELECT closed_on FROM attendance_absence_followups WHERE member_id = ?",
                LocalDate::class.java,
                member.id,
            ),
        )

        clock.setToday(d("2026-10-02"))
        assertEquals(0, retention.purgeExpired())
        assertEquals(1, followupRows(member))
        clock.setToday(d("2026-10-03"))
        assertEquals(1, retention.purgeExpired())
        assertEquals(0, followupRows(member))
        assertEquals(0, retention.purgeExpired())
    }

    @Test
    fun `the daily job catches up on a return nobody marked and takes the earliest one`() {
        val member = absentee()
        memo(member, "missed", 0)
        fx.log(member, "2026-07-12")
        fx.log(member, "2026-07-05", berlin("2026-07-20", "18:00"))
        // Within the 90 days after the return, so the row is closed but not purged yet.
        clock.setToday(d("2026-09-01"))

        retention.purgeExpired()

        assertEquals(
            d("2026-07-05"),
            jdbcTemplate.queryForObject(
                "SELECT closed_on FROM attendance_absence_followups WHERE member_id = ?",
                LocalDate::class.java,
                member.id,
            ),
        )
    }

    @Test
    fun `a corrected log reopens the old anchor empty, and the next write discards the old content`() {
        val member = absentee()
        memo(member, "before correction", 0)
        contact(member, true, 1)
        val returnLog = fx.log(member, "2026-07-05")
        retention.markReturned(member.id!!)
        jdbcTemplate.update("UPDATE attendance_logs SET deleted_at = now() WHERE id = ?", returnLog.id)
        entityManager.clear()
        // 2026-10-01 is before the closed phase's retention ends (07-05 + 90 days).
        clock.setToday(d("2026-10-01"))

        val reopened = listed().getValue(member.publicId)
        assertEquals(d("2026-01-04"), reopened.lastSundayDate)
        assertNull(reopened.memo)
        assertEquals(false, reopened.contacted)
        assertEquals(2, reopened.version)

        val rewritten = memo(member, "after correction", reopened.version)
        assertEquals("after correction", rewritten.memo)
        assertEquals(false, rewritten.contacted)
        assertNull(
            jdbcTemplate.queryForObject(
                "SELECT closed_on FROM attendance_absence_followups WHERE member_id = ?",
                LocalDate::class.java,
                member.id,
            ),
        )
    }

    @Test
    fun `a closed phase past retention reads as never written even before the job ran`() {
        val member = absentee()
        memo(member, "expired", 0)
        val returnLog = fx.log(member, "2026-07-05")
        retention.markReturned(member.id!!)
        jdbcTemplate.update("UPDATE attendance_logs SET deleted_at = now() WHERE id = ?", returnLog.id)
        entityManager.clear()

        assertEquals(0, listed().getValue(member.publicId).version)
        assertEquals("fresh", memo(member, "fresh", 0).memo)
    }

    @Test
    fun `leaving the church removes every follow-up of the member`() {
        val member = absentee()
        memo(member, "to be removed", 0)

        assertEquals(1, retention.deleteForMember(member.id!!))

        assertEquals(0, followupRows(member))
    }

    /** At most one 새가족 순 may exist; reuse a seeded one. */
    private fun newcomerGroup(): ChurchGroup =
        entityManager
            .createQuery("SELECT g FROM ChurchGroup g WHERE g.newcomerGroup = true AND g.deletedAt IS NULL", ChurchGroup::class.java)
            .resultList
            .firstOrNull()
            ?: ChurchGroup(division = "새가족", name = "새가족-${UUID.randomUUID()}").also {
                it.newcomerGroup = true
                entityManager.persist(it)
            }

    private fun profile(
        member: Member,
        lifecycle: NewcomerLifecycle,
        attendance: PostAssignmentAttendance?,
    ) = NewcomerProfile(member = member, lifecycleStatus = lifecycle, postAssignmentAttendance = attendance).also(entityManager::persist)
}
