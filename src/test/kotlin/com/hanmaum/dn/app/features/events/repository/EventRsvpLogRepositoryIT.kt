package com.hanmaum.dn.app.features.events.repository

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.events.domain.EventRsvp
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class EventRsvpLogRepositoryIT {
    @Autowired lateinit var repository: EventRsvpLogRepository

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `upsert changes status without resetting reminders and identical status is idempotent`() {
        val member = Member(lastName = "김", firstName = "철수")
        val responseTime = OffsetDateTime.of(2026, 8, 30, 10, 0, 0, 0, ZoneOffset.UTC)
        val rsvp =
            EventRsvp(
                title = "여름 수련회",
                windowStart = responseTime.minusHours(1),
                windowEnd = responseTime.plusHours(2),
            )
        entityManager.persist(member)
        entityManager.persist(rsvp)
        entityManager.flush()

        assertEquals(
            1,
            repository.upsertResponse(
                UUID.randomUUID(),
                rsvp.id!!,
                member.id!!,
                null,
                responseTime,
                "MAYBE",
            ),
        )
        jdbcTemplate.update(
            "UPDATE event_rsvp_logs SET reminder_count = 2 WHERE event_rsvp_id = ? AND member_id = ?",
            rsvp.id,
            member.id,
        )

        val changedAt = responseTime.plusMinutes(5)
        assertEquals(
            1,
            repository.upsertResponse(
                UUID.randomUUID(),
                rsvp.id!!,
                member.id!!,
                null,
                changedAt,
                "GOING",
            ),
        )
        val changed = loadResponse(rsvp.id!!, member.id!!)
        assertEquals("GOING", changed["status"])
        assertEquals(2, changed["reminder_count"])
        assertEquals(changedAt.toEpochSecond().toDouble(), changed["responded_at_epoch"])

        assertEquals(
            1,
            repository.upsertResponse(
                UUID.randomUUID(),
                rsvp.id!!,
                member.id!!,
                null,
                changedAt.plusMinutes(5),
                "GOING",
            ),
        )
        val idempotent = loadResponse(rsvp.id!!, member.id!!)
        assertEquals(2, idempotent["reminder_count"])
        assertEquals(changedAt.toEpochSecond().toDouble(), idempotent["responded_at_epoch"])
    }

    private fun loadResponse(
        eventRsvpId: Long,
        memberId: Long,
    ): Map<String, Any?> =
        jdbcTemplate.queryForMap(
            """
            SELECT status, reminder_count, EXTRACT(EPOCH FROM checked_in_at)::double precision AS responded_at_epoch
            FROM event_rsvp_logs
            WHERE event_rsvp_id = ? AND member_id = ?
            """.trimIndent(),
            eventRsvpId,
            memberId,
        )
}
