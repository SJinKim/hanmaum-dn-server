package com.hanmaum.dn.app.features.bulletin.service

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.UpdateBulletinRequest
import com.hanmaum.dn.app.features.bulletin.domain.BulletinAnnouncement
import com.hanmaum.dn.app.features.bulletin.domain.BulletinSharingBlock
import com.hanmaum.dn.app.features.bulletin.domain.BulletinSharingBlockType
import com.hanmaum.dn.app.features.bulletin.domain.BulletinStatus
import com.hanmaum.dn.app.features.bulletin.repository.BulletinEditionRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** HDN-290: VOL assignment under concurrency and 지난주 불러오기 against Postgres. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class, BulletinEditionService::class, BulletinEditionServiceIT.ClockConfig::class)
@Tag("integration")
class BulletinEditionServiceIT {
    @TestConfiguration
    class ClockConfig {
        @Bean fun clock(): Clock = Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"), ZoneOffset.UTC)
    }

    @Autowired private lateinit var service: BulletinEditionService

    @Autowired private lateinit var editions: BulletinEditionRepository

    @Autowired private lateinit var entityManager: EntityManager

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    /** Far in the future so real rows in the test database never collide. */
    private val sunday: LocalDate = LocalDate.of(2099, 1, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

    /** Only this test's rows: the test database holds real data. Lists go with ON DELETE CASCADE. */
    @AfterEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM bulletin_edition WHERE service_date >= ?", LocalDate.of(2099, 1, 1))
    }

    @Test
    fun `deleted Sunday can be recreated repeatedly while the following Sunday remains taken`() {
        val date = LocalDate.of(2026, 10, 11)
        val first = service.createDraft(date, null, null, "kc-001")
        val following = service.createDraft(date.plusWeeks(1), null, null, "kc-001")
        service.delete(first.publicId)
        entityManager.flush()
        entityManager.clear()

        assertEquals(date, service.defaults().serviceDate)
        assertNull(
            service
                .defaults()
                .sundays
                .first()
                .editionPublicId,
        )
        val second = service.createDraft(null, null, null, "kc-001")
        assertEquals(date, second.serviceDate)
        assertNotEquals(first.publicId, second.publicId)
        assertEquals(date.plusWeeks(2), service.nextFreeSunday())
        service.delete(second.publicId)
        entityManager.flush()
        entityManager.clear()
        val third = service.createDraft(date, null, null, "kc-001")
        assertNotEquals(second.publicId, third.publicId)
        assertEquals(following.publicId, service.defaults().sundays[1].editionPublicId)
        assertEquals(3, jdbcTemplate.queryForObject("SELECT count(*) FROM bulletin_edition WHERE service_date = ?", Int::class.java, date))
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bulletin_edition WHERE service_date = ? AND deleted_at IS NULL",
                Int::class.java,
                date,
            ),
        )
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `two editions published at the same moment get two consecutive VOLs`() {
        val drafts = listOf(sunday, sunday.plusWeeks(1)).map { service.createDraft(it, null, null, "kc-001") }
        drafts.forEach {
            service.update(
                it.publicId,
                UpdateBulletinRequest(version = it.version, sermonTitle = "주일 말씀", sermonPreacher = "홍길동", songs = listOf("찬양 A")),
                "kc-001",
            )
        }
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val results =
                drafts.map { draft ->
                    executor.submit<Int> {
                        ready.countDown()
                        start.await(10, TimeUnit.SECONDS)
                        checkNotNull(service.publish(draft.publicId, "kc-001").volume)
                    }
                }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()

            val volumes = results.map { it.get(15, TimeUnit.SECONDS) }.sorted()
            assertEquals(volumes[0] + 1, volumes[1])
            assertEquals(volumes.last(), editions.findMaxVolume())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `a copied draft holds the source's lists in order as rows of its own`() {
        val source = service.createDraft(sunday, null, null, "kc-001")
        source.sermonTitle = "주일 말씀"
        source.sermonPreacher = "홍길동"
        source.songs.addAll(listOf("찬양 A", "찬양 B", "찬양 C"))
        source.announcements.addAll(listOf(BulletinAnnouncement("소식 1"), BulletinAnnouncement("소식 2", "본문")))
        source.sharingBlocks.add(BulletinSharingBlock(BulletinSharingBlockType.SCRIPTURE, "말씀", "요 3:16"))
        service.publish(source.publicId, "kc-001")

        val copy = service.createDraft(sunday.plusWeeks(1), null, source.publicId, "kc-002")
        entityManager.flush()
        entityManager.clear()
        val reloaded = editions.findByPublicIdAndDeletedAtIsNull(copy.publicId)!!

        assertEquals(sunday.plusWeeks(1), reloaded.serviceDate)
        assertEquals(BulletinStatus.DRAFT, reloaded.status)
        assertNull(reloaded.volume)
        assertEquals("주일 말씀", reloaded.sermonTitle)
        assertEquals(listOf("찬양 A", "찬양 B", "찬양 C"), reloaded.songs)
        assertEquals(listOf("소식 1", "소식 2"), reloaded.announcements.map { it.title })
        assertEquals("요 3:16", reloaded.sharingBlocks.single().reference)
        assertEquals(3, editions.findByPublicIdAndDeletedAtIsNull(source.publicId)!!.songs.size)
    }
}
