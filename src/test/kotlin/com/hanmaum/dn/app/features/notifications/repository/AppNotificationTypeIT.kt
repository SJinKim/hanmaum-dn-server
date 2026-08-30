package com.hanmaum.dn.app.features.notifications.repository

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.notifications.domain.AppNotification
import com.hanmaum.dn.app.features.notifications.domain.NotificationReferenceType
import com.hanmaum.dn.app.features.notifications.domain.NotificationType
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

/**
 * HDN-119, against real SQL and a real Hibernate load.
 *
 * The reported 500 happened while *loading* the page, so the unit tests cannot reach it:
 * `@Enumerated(STRING)` threw before any mapping code ran. These pin that a value this
 * build does not know loads cleanly and survives a write to the same row.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class AppNotificationTypeIT {
    @Autowired lateinit var repository: AppNotificationRepository

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    private fun newMember(): Member = Member(lastName = "김", firstName = "철수").also { entityManager.persist(it) }

    private fun notification(
        member: Member,
        type: NotificationType = NotificationType.ANNOUNCEMENT,
    ): AppNotification =
        AppNotification(
            member = member,
            type = type,
            title = "제목",
            body = "내용",
            referenceType = NotificationReferenceType.ANNOUNCEMENT,
        ).also { entityManager.persist(it) }

    /** Writes a value no enum constant matches, the way a newer instance would. */
    private fun storeRawType(
        notification: AppNotification,
        rawType: String,
        rawReferenceType: String,
    ) {
        jdbcTemplate.update(
            "UPDATE notifications SET type = ?, reference_type = ? WHERE id = ?",
            rawType,
            rawReferenceType,
            notification.id,
        )
    }

    @Test
    fun `the page loads when a row carries a type this build does not know`() {
        val member = newMember()
        val known = notification(member)
        val unknown = notification(member)
        entityManager.flush()
        storeRawType(unknown, "ANNOUNCEMENT_CREATED", "SOMETHING_NEWER")
        entityManager.clear()

        val page = repository.findAllByMemberIdOrderByCreatedAtDesc(member.id!!, PageRequest.of(0, 20))

        // Before HDN-119 this threw and took the known row down with it.
        assertEquals(2, page.totalElements)
        val loaded = page.content.single { it.publicId == unknown.publicId }
        assertEquals("ANNOUNCEMENT_CREATED", loaded.typeName)
        assertEquals("SOMETHING_NEWER", loaded.referenceTypeName)
        assertEquals(NotificationType.UNKNOWN, loaded.type)
        assertEquals(NotificationReferenceType.UNKNOWN, loaded.referenceType)
        assertNotNull(page.content.single { it.publicId == known.publicId })
    }

    @Test
    fun `the unseen count survives a row this build does not know`() {
        val member = newMember()
        val unknown = notification(member)
        entityManager.flush()
        storeRawType(unknown, "ANNOUNCEMENT_CREATED", "SOMETHING_NEWER")
        entityManager.clear()

        assertEquals(1, repository.countByMemberIdAndSeenAtIsNull(member.id!!))
    }

    @Test
    fun `marking such a notification read does not overwrite its stored type`() {
        val member = newMember()
        val unknown = notification(member)
        entityManager.flush()
        storeRawType(unknown, "ANNOUNCEMENT_CREATED", "SOMETHING_NEWER")
        entityManager.clear()

        // What NotificationService.markRead does: load, mutate, let Hibernate flush. It
        // rewrites every column of a dirty row, so a settable parsed enum would have
        // written UNKNOWN back over the real value here.
        val loaded = repository.findByPublicIdAndMemberId(unknown.publicId, member.id!!)!!
        loaded.readAt = Instant.now()
        loaded.seenAt = Instant.now()
        entityManager.flush()
        entityManager.clear()

        val storedType =
            jdbcTemplate.queryForObject(
                "SELECT type FROM notifications WHERE id = ?",
                String::class.java,
                unknown.id,
            )
        val storedReferenceType =
            jdbcTemplate.queryForObject(
                "SELECT reference_type FROM notifications WHERE id = ?",
                String::class.java,
                unknown.id,
            )

        assertEquals("ANNOUNCEMENT_CREATED", storedType)
        assertEquals("SOMETHING_NEWER", storedReferenceType)
    }

    @Test
    fun `every new type persists and reads back`() {
        val member = newMember()
        val persisted = NotificationType.entries.filter { it != NotificationType.UNKNOWN }.map { notification(member, it) }
        entityManager.flush()
        entityManager.clear()

        val page = repository.findAllByMemberIdOrderByCreatedAtDesc(member.id!!, PageRequest.of(0, 50))

        assertEquals(persisted.size, page.totalElements.toInt())
        assertEquals(
            NotificationType.entries
                .filter { it != NotificationType.UNKNOWN }
                .map { it.name }
                .toSet(),
            page.content.map { it.typeName }.toSet(),
        )
    }
}
