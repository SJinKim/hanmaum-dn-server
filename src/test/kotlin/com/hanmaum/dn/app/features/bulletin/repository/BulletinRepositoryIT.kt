package com.hanmaum.dn.app.features.bulletin.repository

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.bulletin.domain.BulletinAnnouncement
import com.hanmaum.dn.app.features.bulletin.domain.BulletinEdition
import com.hanmaum.dn.app.features.bulletin.domain.BulletinSectionKey
import com.hanmaum.dn.app.features.bulletin.domain.BulletinSharingBlock
import com.hanmaum.dn.app.features.bulletin.domain.BulletinSharingBlockType
import com.hanmaum.dn.app.features.bulletin.domain.BulletinStatus
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** HDN-289: 주보 schema, seed and the constraints the editor relies on. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class BulletinRepositoryIT {
    @Autowired lateinit var editions: BulletinEditionRepository

    @Autowired lateinit var services: BulletinServiceRepository

    @Autowired lateinit var sectionTitles: BulletinSectionTitleRepository

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    /** Far in the future so real rows in the test database never collide. */
    private val sunday: LocalDate = LocalDate.of(2099, 1, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

    private fun defaultService() = checkNotNull(services.findByIsBulletinDefaultTrueAndDeletedAtIsNull())

    private fun newEdition(date: LocalDate = sunday) = BulletinEdition(serviceDate = date, service = defaultService(), createdBy = "kc-001")

    private fun reload(edition: BulletinEdition): BulletinEdition {
        entityManager.flush()
        entityManager.clear()
        return editions.findByPublicIdAndDeletedAtIsNull(edition.publicId)!!
    }

    @Test
    fun `seed creates three services with 3부 as the default`() {
        val seeded = services.findAllByDeletedAtIsNullOrderBySortOrderAsc()
        assertEquals(
            listOf("1부 예배" to LocalTime.of(9, 0), "2부 예배" to LocalTime.of(11, 0), "3부 예배" to LocalTime.of(14, 0)),
            seeded.map { it.name to it.startTime },
        )
        assertEquals("3부 예배", defaultService().name)
    }

    @Test
    fun `seed creates every section title with its default`() {
        val titles = sectionTitles.findAll().associate { it.key to (it.title to it.defaultTitle) }
        assertEquals(
            mapOf(
                BulletinSectionKey.SECTION_WORSHIP to ("경배와 찬양" to "경배와 찬양"),
                BulletinSectionKey.SECTION_OFFERING to ("봉헌" to "봉헌"),
                BulletinSectionKey.SECTION_SENDING to ("축복과 파송" to "축복과 파송"),
                BulletinSectionKey.FIXED_BLESSING_PRAYER to ("봉헌 및 축복기도" to "봉헌 및 축복기도"),
            ),
            titles,
        )
    }

    @Test
    fun `edition round-trips its fields and keeps list order`() {
        val edition =
            newEdition().apply {
                sermonTitle = "새 계명"
                sermonPreacher = "홍길동"
                scriptureReference = "요한복음 13:34-35"
                songs += listOf("곡 C", "곡 A", "곡 B")
                announcements += BulletinAnnouncement("새가족 환영", "예배 후 친교실")
                announcements += BulletinAnnouncement("수련회 안내")
                sharingBlocks += BulletinSharingBlock(BulletinSharingBlockType.HEADING, "서론")
                sharingBlocks += BulletinSharingBlock(BulletinSharingBlockType.SCRIPTURE, "서로 사랑하라", "요 13:34")
                sharingBlocks += BulletinSharingBlock(BulletinSharingBlockType.QUESTION, "어떻게 사랑할까?")
            }
        editions.save(edition)

        val loaded = reload(edition)

        assertEquals(BulletinStatus.DRAFT, loaded.status)
        assertNull(loaded.volume)
        assertEquals(0L, loaded.version)
        assertEquals("3부 예배", loaded.service.name)
        assertEquals("새 계명", loaded.sermonTitle)
        assertEquals(listOf("곡 C", "곡 A", "곡 B"), loaded.songs)
        assertEquals(listOf("새가족 환영" to "예배 후 친교실", "수련회 안내" to null), loaded.announcements.map { it.title to it.body })
        assertEquals(
            listOf(
                Triple(BulletinSharingBlockType.HEADING, "서론", null),
                Triple(BulletinSharingBlockType.SCRIPTURE, "서로 사랑하라", "요 13:34"),
                Triple(BulletinSharingBlockType.QUESTION, "어떻게 사랑할까?", null),
            ),
            loaded.sharingBlocks.map { Triple(it.type, it.text, it.reference) },
        )
        assertEquals(edition, editions.findByServiceDateAndDeletedAtIsNull(sunday))
    }

    @Test
    fun `reordering a list rewrites positions`() {
        val edition = newEdition().apply { songs += listOf("A", "B", "C") }
        editions.save(edition)
        val loaded = reload(edition)

        loaded.songs.removeAt(0)
        loaded.songs.add("A")
        val reordered = reload(loaded)

        assertEquals(listOf("B", "C", "A"), reordered.songs)
        assertEquals(1L, reordered.version)
    }

    @Test
    fun `deleting an edition cascades to its lists`() {
        val edition =
            newEdition().apply {
                songs += "A"
                announcements += BulletinAnnouncement("소식")
                sharingBlocks += BulletinSharingBlock(BulletinSharingBlockType.PARAGRAPH, "본문")
            }
        editions.saveAndFlush(edition)
        val id = edition.id!!

        jdbcTemplate.update("DELETE FROM bulletin_edition WHERE id = ?", id)

        for (table in listOf("bulletin_song", "bulletin_announcement", "bulletin_sharing_block")) {
            assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM $table WHERE edition_id = ?", Int::class.java, id))
        }
    }

    @Test
    fun `a second edition on the same Sunday is rejected`() {
        editions.saveAndFlush(newEdition())
        assertThrows<DataIntegrityViolationException> { editions.saveAndFlush(newEdition()) }
    }

    @Test
    fun `deleted editions release their date but keep their VOL reserved`() {
        val original =
            editions.saveAndFlush(
                newEdition().apply {
                    deletedAt = Instant.now()
                    volume = 9001
                },
            )
        val replacement = editions.saveAndFlush(newEdition())
        assertEquals(replacement, editions.findByServiceDateAndDeletedAtIsNull(sunday))
        assertEquals(listOf(sunday), editions.findServiceDatesFrom(sunday))
        assertEquals(9001, editions.findMaxVolume())
        assertEquals(original, editions.findById(original.id!!).orElseThrow())
        assertThrows<DataIntegrityViolationException> {
            editions.saveAndFlush(newEdition(sunday.plusWeeks(1)).apply { volume = 9001 })
        }
    }

    @Test
    fun `a volume is used only once`() {
        editions.saveAndFlush(newEdition().apply { volume = 9001 })
        assertThrows<DataIntegrityViolationException> {
            editions.saveAndFlush(newEdition(sunday.plusWeeks(1)).apply { volume = 9001 })
        }
    }

    @Test
    fun `an edition on a weekday is rejected`() {
        assertThrows<DataIntegrityViolationException> { editions.saveAndFlush(newEdition(sunday.plusDays(1))) }
    }

    @Test
    fun `a published edition needs a publish time`() {
        assertThrows<DataIntegrityViolationException> {
            editions.saveAndFlush(newEdition().apply { status = BulletinStatus.PUBLISHED })
        }
    }

    @Test
    fun `only a scripture block may carry a reference`() {
        assertThrows<DataIntegrityViolationException> {
            editions.saveAndFlush(
                newEdition().apply {
                    sharingBlocks += BulletinSharingBlock(BulletinSharingBlockType.PARAGRAPH, "본문", "요 1:1")
                },
            )
        }
    }

    @Test
    fun `a second default service is rejected`() {
        assertThrows<DataIntegrityViolationException> {
            jdbcTemplate.update(
                """
                INSERT INTO bulletin_service (public_id, name, start_time, sort_order, is_bulletin_default)
                VALUES (gen_random_uuid(), '4부 예배', '16:00', 4, TRUE)
                """.trimIndent(),
            )
        }
    }
}
