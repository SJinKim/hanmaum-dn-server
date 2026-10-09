package com.hanmaum.dn.app.features.bulletin.service

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.bulletin.domain.BulletinEdition
import com.hanmaum.dn.app.features.bulletin.domain.BulletinStatus
import com.hanmaum.dn.app.features.bulletin.repository.BulletinEditionRepository
import com.hanmaum.dn.app.features.bulletin.repository.BulletinSectionTitleRepository
import com.hanmaum.dn.app.features.bulletin.repository.BulletinServiceRepository
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real member queries and counts; every fixture rolls back with the test transaction. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class BulletinVisibilityIT {
    @Autowired private lateinit var editions: BulletinEditionRepository

    @Autowired private lateinit var services: BulletinServiceRepository

    @Autowired private lateinit var sectionTitles: BulletinSectionTitleRepository

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val sunday = LocalDate.of(2099, 1, 4)
    private val friday = sunday.minusDays(2).atStartOfDay(BulletinEditionService.BERLIN).toInstant()

    private fun serviceAt(instant: Instant) =
        BulletinEditionService(editions, services, sectionTitles, jdbcTemplate, Clock.fixed(instant, ZoneOffset.UTC), 0)

    private fun save(
        date: LocalDate,
        status: BulletinStatus = BulletinStatus.PUBLISHED,
        deleted: Boolean = false,
    ) = editions.saveAndFlush(
        BulletinEdition(date, checkNotNull(services.findByIsBulletinDefaultTrueAndDeletedAtIsNull()), "visibility-test").apply {
            this.status = status
            publishedAt = if (status == BulletinStatus.DRAFT) null else friday.minusSeconds(86400)
            withdrawnAt = if (status == BulletinStatus.WITHDRAWN) friday else null
            deletedAt = if (deleted) friday else null
            snapshotService()
        },
    )

    @Test
    fun `current and direct member reads open Friday while admin reads stay unrestricted`() {
        val past = save(sunday.minusWeeks(1))
        val upcoming = save(sunday)
        val future = save(sunday.plusWeeks(1))
        val thursday = serviceAt(friday.minusSeconds(1))
        val fridayService = serviceAt(friday)

        assertEquals(past.publicId, thursday.currentView()?.publicId)
        assertEquals(HttpStatus.NOT_FOUND, assertThrows<ResponseStatusException> { thursday.publishedView(sunday) }.statusCode)
        assertEquals(upcoming.publicId, fridayService.currentView()?.publicId)
        assertEquals(upcoming.publicId, fridayService.publishedView(sunday).publicId)
        assertEquals(past.publicId, fridayService.publishedView(past.serviceDate).publicId)
        assertEquals(
            HttpStatus.NOT_FOUND,
            assertThrows<ResponseStatusException> { fridayService.publishedView(future.serviceDate) }.statusCode,
        )
        assertEquals(future.publicId, fridayService.view(future.publicId).publicId)
        assertTrue(fridayService.list(BulletinStatus.PUBLISHED, 0, 20).content.any { it.publicId == future.publicId })
    }

    @Test
    fun `history filters in the database before page slicing and totals`() {
        val service = serviceAt(friday)
        val baseline = service.history(0, 2).totalElements
        val oldest = save(sunday.minusWeeks(2))
        val previous = save(sunday.minusWeeks(1))
        val current = save(sunday)
        save(sunday.plusWeeks(1))
        save(sunday.plusWeeks(2))
        save(sunday.minusWeeks(3), BulletinStatus.DRAFT)
        save(sunday.minusWeeks(4), BulletinStatus.WITHDRAWN)
        save(sunday.minusWeeks(5), deleted = true)

        val first = service.history(0, 2)
        val second = service.history(1, 2)
        assertEquals(listOf(current.publicId, previous.publicId), first.content.map { it.publicId })
        assertEquals(oldest.publicId, second.content.first().publicId)
        assertEquals(baseline + 3, first.totalElements)
        assertEquals(first.totalElements, second.totalElements)
        assertEquals(((baseline + 4) / 2).toInt(), first.totalPages)
        assertTrue(first.hasNext())
        assertTrue((first.content + second.content).all { it.serviceDate <= sunday })
        val thursday = serviceAt(friday.minusSeconds(1)).history(0, 2)
        assertEquals(listOf(previous.publicId, oldest.publicId), thursday.content.map { it.publicId })
        assertEquals(baseline + 2, thursday.totalElements)
    }
}
