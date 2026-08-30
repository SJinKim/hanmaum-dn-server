package com.hanmaum.dn.app.features.announcements.repository

import com.hanmaum.dn.app.common.domainvalue.AnnouncementCategory
import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.announcements.domain.Announcement
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.OffsetDateTime
import java.time.ZoneOffset

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class AnnouncementRepositoryIT {
    @Autowired lateinit var repository: AnnouncementRepository

    @Autowired lateinit var entityManager: EntityManager

    @Test
    fun `view count increment is atomic and limited to active announcements`() {
        val now = OffsetDateTime.of(2026, 8, 30, 10, 0, 0, 0, ZoneOffset.UTC)
        val active = persistAnnouncement("활성 공지", now.minusDays(1), now.plusDays(1), viewCount = 7)
        val future = persistAnnouncement("예정 공지", now.plusDays(1), null)
        entityManager.flush()
        entityManager.clear()

        assertEquals(1, repository.incrementActiveViewCount(active.publicId, now))
        assertEquals(0, repository.incrementActiveViewCount(future.publicId, now))

        assertEquals(8, repository.findByPublicIdAndDeleteEntryAtIsNull(active.publicId).get().viewCount)
        assertEquals(0, repository.findByPublicIdAndDeleteEntryAtIsNull(future.publicId).get().viewCount)
    }

    private fun persistAnnouncement(
        title: String,
        startAt: OffsetDateTime,
        endAt: OffsetDateTime?,
        viewCount: Long = 0,
    ): Announcement =
        Announcement(
            category = AnnouncementCategory.NOTICE,
            title = title,
            body = "내용",
            startAt = startAt,
            endAt = endAt,
            viewCount = viewCount,
        ).also(entityManager::persist)
}
