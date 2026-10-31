package com.hanmaum.dn.app.common.pii

import com.hanmaum.dn.app.features.members.domain.Member
import db.migration.V20261031120100__SnapshotAbsenceFollowupChanges
import jakarta.persistence.EntityManager
import org.flywaydb.core.api.configuration.Configuration
import org.flywaydb.core.api.migration.Context
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.test.context.ActiveProfiles
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Flyway has already applied the migration to the empty test database. Each test seeds follow-ups
 * as HDN-375 wrote them, inside its rolled-back transaction, and runs the migration again (HDN-379).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class SnapshotAbsenceFollowupChangesMigrationIT {
    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Autowired lateinit var dataSource: DataSource

    private val contactedAt = Instant.parse("2026-09-01T08:30:00Z")

    @Test
    fun `snapshots the current contact with its actor and the memo without, re-encrypted for the history`() {
        val id = followup(contactedBy = "kc-pastor", memo = "회사 일로 바쁨")
        val untouched = followup(contactedBy = null, memo = null)

        migrate()

        val contact = change(id, "CONTACT_CHANGED")
        assertEquals("LEGACY_SNAPSHOT", contact["origin"])
        assertEquals(0L, contact["revision"])
        assertEquals(true, contact["contacted"])
        assertEquals(Timestamp.from(contactedAt).toInstant(), (contact["occurred_at"] as Timestamp).toInstant())
        val actor = contact["actor_subject"] as String
        assertEquals("kc-pastor", PiiCryptoContext.decrypt(actor, ABSENCE_CHANGE_ACTOR_CONTEXT))
        assertThrows<RuntimeException> { PiiCryptoContext.decrypt(actor, ABSENCE_FOLLOWUP_CONTACTED_BY_CONTEXT) }

        val memo = change(id, "MEMO_CHANGED")
        assertNull(memo["occurred_at"], "when the memo was written is unknown")
        assertNull(memo["actor_subject"], "who wrote the memo is unknown")
        val text = memo["memo"] as String
        assertNotEquals("회사 일로 바쁨", text)
        assertEquals("회사 일로 바쁨", PiiCryptoContext.decrypt(text, ABSENCE_CHANGE_MEMO_CONTEXT))
        assertEquals(PiiCryptoContext.storageKeyId(), memo["pii_key_id"])

        assertEquals(0, changes(untouched), "contacted = false without memo records nothing")
        val current = jdbcTemplate.queryForMap("SELECT contacted, memo FROM attendance_absence_followups WHERE id = ?", id)
        assertEquals(true, current["contacted"])
        assertEquals("회사 일로 바쁨", PiiCryptoContext.decrypt(current["memo"] as String, ABSENCE_FOLLOWUP_MEMO_CONTEXT))
    }

    @Test
    fun `a rerun adds nothing`() {
        val id = followup(contactedBy = "kc-pastor", memo = "메모")

        migrate()
        migrate()

        assertEquals(2, changes(id))
    }

    @Test
    fun `an undecryptable memo fails the migration without quoting the value`() {
        val id = followup(contactedBy = null, memo = null)
        jdbcTemplate.update(
            "UPDATE attendance_absence_followups SET memo = ? WHERE id = ?",
            PiiCryptoContext.encrypt("SENTINEL-379", "some.other_column"),
            id,
        )

        val failure = assertThrows<IllegalStateException> { migrate() }

        assertTrue(failure.message!!.contains("id=$id"))
        assertFalse(failure.message!!.contains("SENTINEL"))
        assertNull(failure.cause, "a crypto failure message may quote the stored value")
    }

    /** A follow-up as V20261031120000 leaves an HDN-375 row: anchor = last Sunday, generation 0. */
    private fun followup(
        contactedBy: String?,
        memo: String?,
    ): Long {
        val member = Member(lastName = "홍", firstName = "길동-${UUID.randomUUID()}")
        entityManager.persist(member)
        entityManager.flush()
        val sunday = LocalDate.of(2026, 1, 4)
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO attendance_absence_followups
                (public_id, member_id, last_sunday_date, absence_anchor_date, contacted, contacted_by, contacted_at, memo, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 2)
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            UUID.randomUUID(),
            member.id,
            sunday,
            sunday,
            contactedBy != null,
            contactedBy?.let { PiiCryptoContext.encrypt(it, ABSENCE_FOLLOWUP_CONTACTED_BY_CONTEXT) },
            contactedBy?.let { Timestamp.from(contactedAt) },
            memo?.let { PiiCryptoContext.encrypt(it, ABSENCE_FOLLOWUP_MEMO_CONTEXT) },
        )!!
    }

    private fun change(
        followupId: Long,
        kind: String,
    ): Map<String, Any?> =
        jdbcTemplate.queryForMap("SELECT * FROM attendance_absence_changes WHERE followup_id = ? AND kind = ?", followupId, kind)

    private fun changes(followupId: Long): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM attendance_absence_changes WHERE followup_id = ?", Int::class.java, followupId)!!

    private fun migrate() {
        val connection = DataSourceUtils.getConnection(dataSource)
        V20261031120100__SnapshotAbsenceFollowupChanges().migrate(
            object : Context {
                override fun getConfiguration(): Configuration = throw UnsupportedOperationException()

                override fun getConnection(): Connection = connection
            },
        )
        entityManager.clear()
    }
}
