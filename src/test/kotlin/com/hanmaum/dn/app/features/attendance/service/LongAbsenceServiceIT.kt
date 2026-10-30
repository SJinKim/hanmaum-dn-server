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
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceReturnRequest
import com.hanmaum.dn.app.features.attendance.berlin
import com.hanmaum.dn.app.features.attendance.domain.AbsenceChangeKind
import com.hanmaum.dn.app.features.attendance.domain.AbsenceChangeOrigin
import com.hanmaum.dn.app.features.attendance.domain.AbsencePhaseEndReason
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
 * HDN-375 and HDN-379 against PostgreSQL: which check-ins count, who is listed, how the follow-up
 * of a phase is written, ended by a check-in or a manual return, read back as history and purged. Today is Monday 2026-10-26 unless a test moves the clock,
 * so a member is listed when the last Sunday is 2026-07-26 or earlier.
 */
@DataJpaTest(properties = ["spring.jpa.show-sql=false", NAMED_SERVICE_PROPERTY])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
@Import(
    PiiCryptoConfiguration::class,
    LongAbsenceService::class,
    LongAbsenceHistoryService::class,
    LongAbsencePhases::class,
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

    @Autowired private lateinit var history: LongAbsenceHistoryService

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
        actor: String = "kc-pastor",
    ) = service.setMemo(member.publicId, LongAbsenceMemoRequest(d(lastSundayDate), version, value), actor)

    private fun markReturned(
        member: Member,
        version: Long,
        lastSundayDate: String = "2026-01-04",
        actor: String = "kc-pastor",
    ) = service.markReturned(member.publicId, LongAbsenceReturnRequest(d(lastSundayDate), version), actor)

    /** What both check-in writers do around their insert, with the member locked by the test transaction. */
    private fun checkIn(
        member: Member,
        date: String,
        createdAt: Instant = berlin(date, "10:00"),
    ) {
        val baseline = retention.beforeCheckIn(member)
        fx.log(member, date, createdAt)
        retention.afterCheckIn(member, baseline)
        entityManager.flush()
    }

    private fun countRows(
        sql: String,
        vararg args: Any,
    ): Int = jdbcTemplate.queryForObject(sql, Int::class.java, *args)!!

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
                service.setMemo(UUID.randomUUID(), LongAbsenceMemoRequest(d("2026-01-04"), 0, "x"), "kc-pastor")
            }
        assertEquals(LongAbsenceException.MEMBER_NOT_FOUND, unknown.code)

        entityManager.clear()
        val row = listed().getValue(member.publicId)
        assertEquals("first", row.memo)
        assertEquals(false, row.contacted)
        assertEquals(1, row.version)
    }

    // ─── Phases, return and history (HDN-379) ───────────────────────────────

    @Test
    fun `a check-in closes the phase with the visit date, the next absence starts empty and keeps no memo`() {
        val member = absentee()
        val written = memo(member, "old phase", 0)
        checkIn(member, "2026-07-05")

        val next = listed().getValue(member.publicId)
        assertEquals(d("2026-07-05"), next.lastSundayDate)
        assertNull(next.memo)
        assertNull(next.phasePublicId)
        assertTrue(next.version > written.version, "the check-in makes the old token stale")
        val closed = history.history(member.publicId, 0, 20).items.single()
        assertEquals(written.phasePublicId, closed.phasePublicId)
        assertEquals(d("2026-07-05"), closed.endedOn)
        assertEquals(AbsencePhaseEndReason.CHECK_IN, closed.endReason)
        assertNull(closed.returnedBy)
        assertNull(closed.returnedAt)
    }

    @Test
    fun `a check-in ending a listed absence with nothing recorded stores it for the history`() {
        val member = absentee()
        checkIn(member, "2026-10-25")

        val closed = history.history(member.publicId, 0, 20).items.single()
        assertEquals(d("2026-01-04"), closed.absenceAnchorDate)
        assertEquals(d("2026-10-25"), closed.endedOn)
        assertEquals(false, closed.contacted)
        // A non-listed member's check-in stores nothing.
        val recent = fx.member().also { fx.log(it, "2026-10-04") }
        checkIn(recent, "2026-10-25")
        assertEquals(0, followupRows(recent))
    }

    @Test
    fun `manual return ends the phase atomically with actor and server time and writes no attendance log`() {
        val pastor = fx.member(firstName = "목사")
        val member = absentee()
        val before = listed().getValue(member.publicId)
        val logs = countRows("SELECT COUNT(*) FROM attendance_logs WHERE member_id = ?", member.id!!)
        val baseline = service.summary()

        val returned = markReturned(member, before.version, actor = pastor.keycloakId!!)

        assertEquals(d("2026-10-26"), returned.endedOn)
        assertEquals(clock.instant, returned.returnedAt)
        assertEquals(pastor.keycloakId, returned.returnedBy)
        assertEquals(pastor.publicId, returned.returnedByMember?.publicId)
        assertEquals(before.version + 1, returned.version)
        assertNull(listed()[member.publicId])
        assertEquals(baseline.totalCount - 1, service.summary().totalCount)
        assertEquals(logs, countRows("SELECT COUNT(*) FROM attendance_logs WHERE member_id = ?", member.id!!))
        val raw =
            jdbcTemplate.queryForObject(
                "SELECT returned_by FROM attendance_absence_followups WHERE member_id = ?",
                String::class.java,
                member.id,
            )
        assertNotEquals(pastor.keycloakId, raw)

        val phase = history.phase(member.publicId, returned.phasePublicId)
        assertEquals(AbsencePhaseEndReason.MANUAL_RETURN, phase.endReason)
        assertEquals(pastor.keycloakId, phase.returnedBy)
        assertEquals(false, phase.isCurrent)

        // Repeating the action with the old token is a 409, not a second close.
        val again = assertThrows<LongAbsenceException> { markReturned(member, before.version) }
        assertEquals(LongAbsenceException.NOT_CURRENT, again.code)
        assertEquals(1, followupRows(member))
    }

    @Test
    fun `after a manual return the absence counts from it, three months later empty again with a new phase id`() {
        val member = absentee()
        val first = memo(member, "첫 번째", 0)
        val returned = markReturned(member, first.version)

        clock.setToday(d("2027-01-25"))
        assertNull(listed()[member.publicId], "one day before three months")
        clock.setToday(d("2027-01-26"))
        val again = listed().getValue(member.publicId)
        assertEquals(d("2026-01-04"), again.lastSundayDate, "lastSundayDate stays the check-in date")
        assertEquals(d("2026-10-26"), again.absenceAnchorDate)
        assertEquals(d("2026-10-27"), again.absentSince)
        assertEquals(3, again.absenceMonths)
        assertNull(again.memo)
        assertEquals(false, again.contacted)
        assertNull(again.phasePublicId)
        assertEquals(returned.version, again.version)

        val second = memo(member, "두 번째", again.version)
        assertNotNull(second.phasePublicId)
        assertNotEquals(first.phasePublicId, second.phasePublicId)
        assertEquals(2, followupRows(member))

        // A back-filled visit before the return does not move the anchor back.
        fx.log(member, "2026-10-04")
        assertEquals(d("2026-10-26"), listed().getValue(member.publicId).absenceAnchorDate)
        // A check-in after the return does, and the return date is no longer needed.
        checkIn(member, "2027-01-24")
        assertNull(member.absenceManualReturnOn)
        assertNull(listed()[member.publicId])
    }

    @Test
    fun `contact and memo changes are versioned with actor and time, a no-op records nothing`() {
        val member = absentee()
        val a = memo(member, "A", 0, actor = "kc-one")
        clock.instant = clock.instant.plusSeconds(60)
        val b = memo(member, "B", a.version, actor = "kc-two")
        val contacted = contact(member, true, b.version, actor = "kc-one")
        val uncontacted = contact(member, false, contacted.version, actor = "kc-two")
        val same = contact(member, false, uncontacted.version, actor = "kc-three")
        assertEquals(uncontacted.version, same.version)
        val both = contact(member, true, uncontacted.version, actor = "kc-three", memoUpdate = LongAbsenceMemoUpdate("C"))

        val page = history.changes(member.publicId, both.phasePublicId!!, 0, 20)
        assertEquals(6, page.totalElements)
        val items = page.items
        assertEquals(
            listOf(both.version, both.version, uncontacted.version, contacted.version, b.version, a.version),
            items.map { it.revision },
        )
        assertEquals(listOf(AbsenceChangeKind.CONTACT_CHANGED, AbsenceChangeKind.MEMO_CHANGED), items.take(2).map { it.kind })
        assertEquals(listOf("kc-three", "kc-three", "kc-two", "kc-one", "kc-two", "kc-one"), items.map { it.actorSubject })
        assertEquals(listOf(true, null, false, true, null, null), items.map { it.contacted })
        assertEquals(listOf(null, "C", null, null, "B", "A"), items.map { it.memo })
        assertTrue(items.all { it.origin == AbsenceChangeOrigin.ACTION && it.occurredAt != null })
        assertEquals(items[0].occurredAt, items[1].occurredAt, "one write, one time")

        val rawMemos = jdbcTemplate.queryForList("SELECT memo FROM attendance_absence_changes WHERE memo IS NOT NULL", String::class.java)
        assertTrue(rawMemos.none { it in setOf("A", "B", "C") }, "memo versions are encrypted")
        val rawActors = jdbcTemplate.queryForList("SELECT actor_subject FROM attendance_absence_changes", String::class.java)
        assertTrue(rawActors.none { it.startsWith("kc-") }, "actors are encrypted")
    }

    @Test
    fun `clearing the memo removes text and every memo version, contact history stays, a new text starts fresh`() {
        val member = absentee()
        val a = memo(member, "A", 0)
        val b = memo(member, "B", a.version)
        val contacted = contact(member, true, b.version)
        val cleared = memo(member, null, contacted.version)
        assertTrue(cleared.version > contacted.version, "clearing raises the counter")
        assertNull(cleared.memo)
        val phaseId = cleared.phasePublicId!!

        entityManager.flush()
        entityManager.clear()
        assertEquals(
            0,
            countRows(
                "SELECT COUNT(*) FROM attendance_absence_changes WHERE kind = 'MEMO_CHANGED' AND followup_id IN (SELECT id FROM attendance_absence_followups WHERE member_id = ?)",
                member.id!!,
            ),
        )
        assertNull(
            jdbcTemplate.queryForObject("SELECT memo FROM attendance_absence_followups WHERE member_id = ?", String::class.java, member.id),
        )
        val afterClear = history.changes(member.publicId, phaseId, 0, 20).items
        assertEquals(listOf(AbsenceChangeKind.CONTACT_CHANGED), afterClear.map { it.kind })
        assertNull(history.phase(member.publicId, phaseId).memo)

        memo(member, "D", cleared.version)
        assertEquals(listOf("D"), history.changes(member.publicId, phaseId, 0, 20).items.mapNotNull { it.memo })
    }

    @Test
    fun `history pages are stable, total counts are right and a page past the end is empty`() {
        val absentees = (1..3).map { absentee() }
        absentees.forEach { markReturned(it, listed().getValue(it.publicId).version) }

        val all = history.history(null, 0, 100)
        val mine = all.items.filter { item -> absentees.any { it.publicId == item.memberPublicId } }
        assertEquals(3, mine.size)
        val firstTwo = history.history(null, 0, 2)
        assertEquals(all.totalElements, firstTwo.totalElements)
        assertEquals(((all.totalElements + 1) / 2).toInt(), firstTwo.totalPages)
        val ordered = all.items.map { it.phasePublicId }
        assertEquals(ordered.take(2), firstTwo.items.map { it.phasePublicId })
        assertEquals(ordered.drop(2).take(2), history.history(null, 1, 2).items.map { it.phasePublicId })
        // Same endedOn: ties by phase id, descending.
        // PostgreSQL orders uuids bytewise, which is the order of their lowercase text, not UUID.compareTo.
        val sameDay = all.items.filter { it.endedOn == d("2026-10-26") }.map { it.phasePublicId.toString() }
        assertEquals(sameDay.sortedDescending(), sameDay)
        val past = history.history(null, 1000, 20)
        assertTrue(past.items.isEmpty())
        assertEquals(all.totalElements, past.totalElements)

        val stranger = fx.member()
        assertTrue(history.history(stranger.publicId, 0, 20).items.isEmpty())
        val unknown = assertThrows<LongAbsenceException> { history.history(UUID.randomUUID(), 0, 20) }
        assertEquals(LongAbsenceException.MEMBER_NOT_FOUND, unknown.code)
        val softDeleted = absentees.first().also { jdbcTemplate.update("UPDATE members SET deleted_at = now() WHERE id = ?", it.id) }
        entityManager.clear()
        assertTrue(history.history(null, 0, 100).items.none { it.memberPublicId == softDeleted.publicId })
    }

    @Test
    fun `a phase is only found with its own member, otherwise it reads as unknown`() {
        val member = absentee()
        val other = absentee()
        val phaseId = memo(member, "x", 0).phasePublicId!!

        assertEquals(true, history.phase(member.publicId, phaseId).isCurrent)
        val foreign = assertThrows<LongAbsenceException> { history.phase(other.publicId, phaseId) }
        assertEquals(LongAbsenceException.PHASE_NOT_FOUND, foreign.code)
        val unknown = assertThrows<LongAbsenceException> { history.changes(member.publicId, UUID.randomUUID(), 0, 20) }
        assertEquals(LongAbsenceException.PHASE_NOT_FOUND, unknown.code)
    }

    // ─── Retention (#376 F3, #380) ──────────────────────────────────────────

    @Test
    fun `90 days after the end memo and memo versions go, ten years after the phase, the open phase stays`() {
        val member = absentee()
        val a = memo(member, "A", 0)
        val contacted = contact(member, true, a.version)
        val phaseId = markReturned(member, contacted.version).phasePublicId
        val open = absentee().also { memo(it, "open", 0) }
        entityManager.flush()

        clock.setToday(d("2027-01-23"))
        assertEquals("A", history.phase(member.publicId, phaseId).memo, "day 89")
        clock.setToday(d("2027-01-24"))
        // Hidden on day 90 already before the job ran.
        assertNull(history.phase(member.publicId, phaseId).memo)
        assertEquals(listOf(AbsenceChangeKind.CONTACT_CHANGED), history.changes(member.publicId, phaseId, 0, 20).items.map { it.kind })
        assertEquals(1, history.changes(member.publicId, phaseId, 0, 20).totalElements)

        retention.purgeExpired()
        entityManager.clear()
        assertNull(
            jdbcTemplate.queryForObject("SELECT memo FROM attendance_absence_followups WHERE public_id = ?", String::class.java, phaseId),
        )
        assertEquals(
            0,
            countRows(
                "SELECT COUNT(*) FROM attendance_absence_changes c JOIN attendance_absence_followups f ON f.id = c.followup_id WHERE f.public_id = ? AND c.kind = 'MEMO_CHANGED'",
                phaseId,
            ),
        )
        assertEquals(
            1,
            countRows(
                "SELECT COUNT(*) FROM attendance_absence_changes c JOIN attendance_absence_followups f ON f.id = c.followup_id WHERE f.public_id = ?",
                phaseId,
            ),
        )
        assertEquals(
            1,
            countRows("SELECT COUNT(*) FROM attendance_absence_followups WHERE member_id = ? AND memo IS NOT NULL", open.id!!),
            "open memo stays",
        )
        assertEquals(0, retention.purgeExpired(), "idempotent")

        clock.setToday(d("2036-10-25"))
        assertEquals(1, history.history(member.publicId, 0, 20).items.size, "one day before ten years")
        clock.setToday(d("2036-10-26"))
        assertTrue(history.history(member.publicId, 0, 20).items.isEmpty(), "hidden on the day before the job ran")
        assertEquals(
            LongAbsenceException.PHASE_NOT_FOUND,
            assertThrows<LongAbsenceException> { history.phase(member.publicId, phaseId) }.code,
        )
        retention.purgeExpired()
        assertEquals(0, countRows("SELECT COUNT(*) FROM attendance_absence_followups WHERE public_id = ?", phaseId))
        assertEquals(1, followupRows(open), "an open phase has no retention")
    }

    @Test
    fun `the daily job catches up on a return nobody closed and takes the earliest visit`() {
        val member = absentee()
        memo(member, "missed", 0)
        fx.log(member, "2026-07-12")
        fx.log(member, "2026-07-05", berlin("2026-07-20", "18:00"))
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
        assertEquals(
            "CHECK_IN",
            jdbcTemplate.queryForObject(
                "SELECT end_reason FROM attendance_absence_followups WHERE member_id = ?",
                String::class.java,
                member.id,
            ),
        )
    }

    @Test
    fun `a corrected log never reopens a closed phase, the next write starts a new one`() {
        val member = absentee()
        val written = memo(member, "before correction", 0)
        checkIn(member, "2026-07-05")
        jdbcTemplate.update(
            "UPDATE attendance_logs SET deleted_at = now() WHERE member_id = ? AND attendance_date > DATE '2026-01-04'",
            member.id,
        )
        entityManager.clear()

        val again = listed().getValue(member.publicId)
        assertEquals(d("2026-01-04"), again.lastSundayDate)
        assertNull(again.memo)
        assertNull(again.phasePublicId)
        val rewritten = memo(member, "after correction", again.version)
        assertNotEquals(written.phasePublicId, rewritten.phasePublicId)
        assertEquals(2, followupRows(member))
        val old = history.phase(member.publicId, written.phasePublicId!!)
        assertEquals(d("2026-07-05"), old.endedOn, "the closed phase stays closed")
        assertEquals(false, old.isCurrent)
    }

    @Test
    fun `leaving the church removes every phase and history entry and keeps the counter`() {
        val member = absentee()
        val written = memo(member, "to be removed", 0)
        markReturned(member, written.version)
        val revision = member.absenceRevision

        assertEquals(1, retention.deleteForMember(member))
        entityManager.flush()

        assertEquals(0, followupRows(member))
        assertEquals(0, countRows("SELECT COUNT(*) FROM attendance_absence_changes"))
        assertNull(member.absenceManualReturnOn)
        assertTrue(member.absenceRevision > revision)
    }

    @Test
    fun `phase cutoff counts ten years like plusYears, also from 29 February`() {
        assertEquals(d("2024-02-29"), LongAbsenceRetentionService.phaseCutoff(d("2034-02-28")))
        assertEquals(d("2016-10-26"), LongAbsenceRetentionService.phaseCutoff(d("2026-10-26")))
        assertEquals(d("2026-07-28"), LongAbsenceRetentionService.memoCutoff(d("2026-10-26")))
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
