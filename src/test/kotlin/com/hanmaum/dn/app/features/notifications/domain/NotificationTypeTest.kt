package com.hanmaum.dn.app.features.notifications.domain

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.notifications.api.toDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * HDN-119: one row with a type this build does not know used to fail the whole page query
 * with `No enum constant …NotificationType.X`. The stored value is now text, parsed on
 * demand, so an unfamiliar type costs nothing and still reaches the client.
 */
class NotificationTypeTest {
    private fun member() = Member(lastName = "김", firstName = "철수")

    private fun notification(
        type: NotificationType = NotificationType.ANNOUNCEMENT,
        referenceType: NotificationReferenceType? = NotificationReferenceType.ANNOUNCEMENT,
    ) = AppNotification(
        member = member(),
        type = type,
        title = "제목",
        body = "내용",
        referenceType = referenceType,
    ).also { it.createdAt = Instant.parse("2026-08-31T08:00:00Z") }

    // ─── The vocabulary the ticket asks for ───────────────────────────────────

    @Test
    fun `every occasion in the ticket has a type and a reference type`() {
        val required = listOf("ANNOUNCEMENT", "EVENT", "MINISTRY", "TRAINING", "ATTENDANCE", "MEMBER")

        assertTrue(NotificationType.entries.map { it.name }.containsAll(required))
        assertTrue(NotificationReferenceType.entries.map { it.name }.containsAll(required))
    }

    @Test
    fun `each type round-trips through the stored string`() {
        NotificationType.entries
            .filter { it != NotificationType.UNKNOWN }
            .forEach { type ->
                val stored = notification(type = type)
                assertEquals(type.name, stored.typeName)
                assertEquals(type, stored.type)
            }
    }

    @Test
    fun `each reference type round-trips through the stored string`() {
        NotificationReferenceType.entries
            .filter { it != NotificationReferenceType.UNKNOWN }
            .forEach { referenceType ->
                val stored = notification(referenceType = referenceType)
                assertEquals(referenceType.name, stored.referenceTypeName)
                assertEquals(referenceType, stored.referenceType)
            }
    }

    // ─── The failure the ticket reported ──────────────────────────────────────

    @Test
    fun `a stored type this build does not know parses to UNKNOWN instead of throwing`() {
        val stored = notification()
        // Exactly the value from the ticket's stack trace.
        stored.typeName = "ANNOUNCEMENT_CREATED"

        assertEquals(NotificationType.UNKNOWN, stored.type)
    }

    @Test
    fun `an unrecognised type still reaches the client verbatim`() {
        val stored = notification()
        stored.typeName = "ANNOUNCEMENT_CREATED"
        stored.referenceTypeName = "SOMETHING_NEWER"

        val dto = stored.toDto()

        // Not "UNKNOWN": a newer client may know these, and it renders neutrally otherwise.
        assertEquals("ANNOUNCEMENT_CREATED", dto.type)
        assertEquals("SOMETHING_NEWER", dto.referenceType)
    }

    @Test
    fun `a missing reference type stays null rather than becoming UNKNOWN`() {
        val stored = notification(referenceType = null)

        assertNull(stored.referenceTypeName)
        assertNull(stored.referenceType)
        assertNull(stored.toDto().referenceType)
    }

    @Test
    fun `marking an unrecognised notification read leaves its stored type alone`() {
        val stored = notification()
        stored.typeName = "ANNOUNCEMENT_CREATED"

        // What NotificationService.markRead does. The parsed view reads UNKNOWN, but there
        // is no setter behind it, so the flush cannot write UNKNOWN over the real value.
        stored.readAt = Instant.parse("2026-08-31T09:00:00Z")
        stored.seenAt = Instant.parse("2026-08-31T09:00:00Z")

        assertEquals("ANNOUNCEMENT_CREATED", stored.typeName)
        assertEquals(NotificationType.UNKNOWN, stored.type)
    }
}
