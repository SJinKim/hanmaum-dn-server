package com.hanmaum.dn.app.features.bulletin.service

import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinAnnouncementDto
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinSharingBlockDto
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.UpdateBulletinRequest
import com.hanmaum.dn.app.features.bulletin.domain.BulletinAnnouncement
import com.hanmaum.dn.app.features.bulletin.domain.BulletinEdition
import com.hanmaum.dn.app.features.bulletin.domain.BulletinService
import com.hanmaum.dn.app.features.bulletin.domain.BulletinSharingBlock
import com.hanmaum.dn.app.features.bulletin.domain.BulletinSharingBlockType
import com.hanmaum.dn.app.features.bulletin.domain.BulletinStatus
import com.hanmaum.dn.app.features.bulletin.repository.BulletinEditionRepository
import com.hanmaum.dn.app.features.bulletin.repository.BulletinSectionTitleRepository
import com.hanmaum.dn.app.features.bulletin.repository.BulletinServiceRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExtendWith(MockitoExtension::class)
class BulletinEditionServiceTest {
    @Mock private lateinit var editions: BulletinEditionRepository

    @Mock private lateinit var services: BulletinServiceRepository

    @Mock private lateinit var sectionTitles: BulletinSectionTitleRepository

    @Mock private lateinit var jdbcTemplate: JdbcTemplate

    private val service3 = BulletinService(name = "3부 예배", startTime = LocalTime.of(13, 30), sortOrder = 3, isBulletinDefault = true)

    // UTC clocks on purpose: the service must still reckon in Berlin.
    private fun at(instant: String) = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)

    // Tuesday 2026-10-06, 10:00 Berlin.
    private val tuesday = at("2026-10-06T08:00:00Z")

    private fun serviceAt(
        clock: Clock = tuesday,
        volumeOffset: Int = 0,
    ) = BulletinEditionService(editions, services, sectionTitles, jdbcTemplate, clock, volumeOffset)

    private fun edition(
        date: LocalDate,
        status: BulletinStatus = BulletinStatus.DRAFT,
        volume: Int? = null,
    ) = BulletinEdition(serviceDate = date, service = service3, createdBy = "kc-001").also {
        it.status = status
        it.volume = volume
    }

    /** The fields publishing requires. */
    private fun BulletinEdition.complete() =
        apply {
            sermonTitle = "주일 말씀"
            sermonPreacher = "홍길동"
            songs.add("찬양 A")
        }

    private fun stubSave() {
        `when`(editions.saveAndFlush(any<BulletinEdition>())).thenAnswer { it.arguments[0] }
    }

    // ─── next free Sunday ───────────────────────────────────────────────────

    @Test
    fun `next free Sunday on a weekday is the coming Sunday`() {
        `when`(editions.findServiceDatesFrom(LocalDate.of(2026, 10, 11))).thenReturn(emptyList())
        assertEquals(LocalDate.of(2026, 10, 11), serviceAt().nextFreeSunday())
    }

    @Test
    fun `next free Sunday is today when today is a free Sunday`() {
        val sunday = LocalDate.of(2026, 10, 11)
        `when`(editions.findServiceDatesFrom(sunday)).thenReturn(emptyList())
        assertEquals(sunday, serviceAt(at("2026-10-11T07:00:00Z")).nextFreeSunday())
    }

    @Test
    fun `next free Sunday skips taken Sundays`() {
        val from = LocalDate.of(2026, 10, 11)
        `when`(editions.findServiceDatesFrom(from)).thenReturn(listOf(from, from.plusWeeks(1)))
        assertEquals(LocalDate.of(2026, 10, 25), serviceAt().nextFreeSunday())
    }

    @Test
    fun `just after midnight on the day summer time starts it is already Sunday in Berlin`() {
        // 2026-03-28T23:30Z is Saturday in UTC, Sunday 00:30 CET in Berlin.
        val sunday = LocalDate.of(2026, 3, 29)
        `when`(editions.findServiceDatesFrom(sunday)).thenReturn(emptyList())
        assertEquals(sunday, serviceAt(at("2026-03-28T23:30:00Z")).nextFreeSunday())
    }

    @Test
    fun `just after midnight on the day winter time starts it is already Sunday in Berlin`() {
        // 2026-10-24T22:30Z is Saturday in UTC, Sunday 00:30 CEST in Berlin.
        val sunday = LocalDate.of(2026, 10, 25)
        `when`(editions.findServiceDatesFrom(sunday)).thenReturn(emptyList())
        assertEquals(sunday, serviceAt(at("2026-10-24T22:30:00Z")).nextFreeSunday())
    }

    @Test
    fun `defaults pair the next free Sunday with the default service`() {
        `when`(editions.findServiceDatesFrom(any())).thenReturn(emptyList())
        `when`(services.findByIsBulletinDefaultTrueAndDeletedAtIsNull()).thenReturn(service3)
        assertEquals(BulletinDefaults(LocalDate.of(2026, 10, 11), service3), serviceAt().defaults())
    }

    // ─── create ─────────────────────────────────────────────────────────────

    @Test
    fun `a draft without a date lands on the next free Sunday with the default service snapshotted`() {
        `when`(editions.findServiceDatesFrom(any())).thenReturn(emptyList())
        `when`(services.findByIsBulletinDefaultTrueAndDeletedAtIsNull()).thenReturn(service3)
        stubSave()

        val draft = serviceAt().createDraft(null, null, null, "kc-001")

        assertEquals(LocalDate.of(2026, 10, 11), draft.serviceDate)
        assertEquals(BulletinStatus.DRAFT, draft.status)
        assertNull(draft.volume)
        assertEquals("3부 예배", draft.serviceName)
        assertEquals(LocalTime.of(13, 30), draft.serviceStartTime)
    }

    @Test
    fun `a date that is not a Sunday is rejected with 400`() {
        val e = assertThrows<ResponseStatusException> { serviceAt().createDraft(LocalDate.of(2026, 10, 10), null, null, "kc-001") }
        assertEquals(HttpStatus.BAD_REQUEST, e.statusCode)
        verify(editions, never()).saveAndFlush(any<BulletinEdition>())
    }

    @Test
    fun `a second bulletin for the same Sunday is rejected with 409`() {
        `when`(editions.existsByServiceDate(LocalDate.of(2026, 10, 11))).thenReturn(true)
        val e = assertThrows<ResponseStatusException> { serviceAt().createDraft(LocalDate.of(2026, 10, 11), null, null, "kc-001") }
        assertEquals(HttpStatus.CONFLICT, e.statusCode)
    }

    @Test
    fun `losing the race for a Sunday at insert time is a 409 too`() {
        `when`(services.findByIsBulletinDefaultTrueAndDeletedAtIsNull()).thenReturn(service3)
        `when`(editions.saveAndFlush(any<BulletinEdition>())).thenThrow(DataIntegrityViolationException("uq_bulletin_edition_service_date"))
        val e = assertThrows<ResponseStatusException> { serviceAt().createDraft(LocalDate.of(2026, 10, 11), null, null, "kc-001") }
        assertEquals(HttpStatus.CONFLICT, e.statusCode)
    }

    @Test
    fun `copyFrom takes over content and lists in order but not date, status, VOL or publish time`() {
        val source =
            edition(LocalDate.of(2026, 10, 4), BulletinStatus.PUBLISHED, volume = 7).apply {
                publishedAt = Instant.parse("2026-10-03T10:00:00Z")
                sermonTitle = "주일 말씀"
                sermonPreacher = "홍길동"
                openingPrayerBy = "Max Mustermann"
                songs.addAll(listOf("찬양 A", "찬양 B", "찬양 C"))
                announcements.addAll(listOf(BulletinAnnouncement("소식 1"), BulletinAnnouncement("소식 2", "본문")))
                sharingBlocks.add(BulletinSharingBlock(BulletinSharingBlockType.SCRIPTURE, "말씀", "요 3:16"))
            }
        `when`(editions.findByPublicIdAndDeletedAtIsNull(source.publicId)).thenReturn(source)
        `when`(editions.findServiceDatesFrom(any())).thenReturn(emptyList())
        `when`(services.findByIsBulletinDefaultTrueAndDeletedAtIsNull()).thenReturn(service3)
        stubSave()

        val copy = serviceAt().createDraft(null, null, source.publicId, "kc-002")

        assertEquals(LocalDate.of(2026, 10, 11), copy.serviceDate)
        assertEquals(BulletinStatus.DRAFT, copy.status)
        assertNull(copy.volume)
        assertNull(copy.publishedAt)
        assertEquals("kc-002", copy.createdBy)
        assertEquals("주일 말씀", copy.sermonTitle)
        assertEquals("홍길동", copy.sermonPreacher)
        assertEquals("Max Mustermann", copy.openingPrayerBy)
        assertEquals(listOf("찬양 A", "찬양 B", "찬양 C"), copy.songs)
        assertEquals(listOf("소식 1" to null, "소식 2" to "본문"), copy.announcements.map { it.title to it.body })
        assertEquals("요 3:16", copy.sharingBlocks.single().reference)
    }

    @Test
    fun `copyFrom an unknown edition is a 404`() {
        val e = assertThrows<ResponseStatusException> { serviceAt().createDraft(null, null, UUID.randomUUID(), "kc-001") }
        assertEquals(HttpStatus.NOT_FOUND, e.statusCode)
    }

    // ─── VOL ────────────────────────────────────────────────────────────────

    @Test
    fun `first publish takes max VOL plus one under the advisory lock`() {
        val draft = edition(LocalDate.of(2026, 10, 11)).complete()
        `when`(editions.findByPublicIdAndDeletedAtIsNull(draft.publicId)).thenReturn(draft)
        `when`(editions.findMaxVolume()).thenReturn(41)
        stubSave()

        serviceAt().publish(draft.publicId, "kc-001")

        assertEquals(42, draft.volume)
        assertEquals(BulletinStatus.PUBLISHED, draft.status)
        assertEquals(tuesday.instant(), draft.publishedAt)
        verify(jdbcTemplate).execute("SELECT pg_advisory_xact_lock(290001)")
    }

    @Test
    fun `the very first VOL starts after the configured offset`() {
        val draft = edition(LocalDate.of(2026, 10, 11)).complete()
        `when`(editions.findByPublicIdAndDeletedAtIsNull(draft.publicId)).thenReturn(draft)
        `when`(editions.findMaxVolume()).thenReturn(null)
        stubSave()

        serviceAt(volumeOffset = 500).publish(draft.publicId, "kc-001")

        assertEquals(501, draft.volume)
    }

    @Test
    fun `withdraw keeps the VOL and a republish neither changes nor recounts it`() {
        val published = edition(LocalDate.of(2026, 10, 11), BulletinStatus.PUBLISHED, volume = 42).complete()
        `when`(editions.findByPublicIdAndDeletedAtIsNull(published.publicId)).thenReturn(published)
        stubSave()

        serviceAt().withdraw(published.publicId, "kc-001")
        assertEquals(BulletinStatus.WITHDRAWN, published.status)
        assertEquals(42, published.volume)

        serviceAt().publish(published.publicId, "kc-001")
        assertEquals(BulletinStatus.PUBLISHED, published.status)
        assertEquals(42, published.volume)
        assertNull(published.withdrawnAt)
        verify(editions, never()).findMaxVolume()
    }

    @Test
    fun `publishing twice or withdrawing a draft is a 409`() {
        val published = edition(LocalDate.of(2026, 10, 11), BulletinStatus.PUBLISHED, volume = 1)
        val draft = edition(LocalDate.of(2026, 10, 18))
        `when`(editions.findByPublicIdAndDeletedAtIsNull(published.publicId)).thenReturn(published)
        `when`(editions.findByPublicIdAndDeletedAtIsNull(draft.publicId)).thenReturn(draft)

        assertEquals(HttpStatus.CONFLICT, assertThrows<ResponseStatusException> { serviceAt().publish(published.publicId, "x") }.statusCode)
        assertEquals(HttpStatus.CONFLICT, assertThrows<ResponseStatusException> { serviceAt().withdraw(draft.publicId, "x") }.statusCode)
    }

    @Test
    fun `an incomplete edition is a 422 naming every missing field and takes no VOL`() {
        val draft = edition(LocalDate.of(2026, 10, 11)).apply { sermonTitle = "주일 말씀" }
        `when`(editions.findByPublicIdAndDeletedAtIsNull(draft.publicId)).thenReturn(draft)

        val e = assertThrows<BulletinIncompleteException> { serviceAt().publish(draft.publicId, "kc-001") }

        assertEquals(setOf("sermonPreacher", "songs"), e.fieldErrors.keys)
        assertEquals(BulletinStatus.DRAFT, draft.status)
        assertNull(draft.volume)
        verify(editions, never()).findMaxVolume()
        verify(editions, never()).saveAndFlush(any<BulletinEdition>())
    }

    // ─── update ─────────────────────────────────────────────────────────────

    private fun request(
        version: Long = 0,
        sharingBlocks: List<BulletinSharingBlockDto> = emptyList(),
    ) = UpdateBulletinRequest(
        version = version,
        sermonTitle = "  주일 말씀 ",
        sermonPreacher = " ",
        songs = listOf(" 찬양 B ", "찬양 C"),
        announcements = listOf(BulletinAnnouncementDto(" 소식 ", " ")),
        sharingBlocks = sharingBlocks,
    )

    @Test
    fun `update replaces content and lists, trimming text and turning blanks into null`() {
        val draft =
            edition(LocalDate.of(2026, 10, 11)).complete().apply {
                openingPrayerBy = "Max Mustermann"
                announcements.add(BulletinAnnouncement("옛 소식"))
            }
        `when`(editions.findByPublicIdAndDeletedAtIsNull(draft.publicId)).thenReturn(draft)
        `when`(sectionTitles.findAll()).thenReturn(emptyList())
        stubSave()

        val view = serviceAt().update(draft.publicId, request(), "kc-002")

        assertEquals("주일 말씀", draft.sermonTitle)
        assertNull(draft.sermonPreacher)
        assertNull(draft.openingPrayerBy)
        assertEquals(listOf("찬양 B", "찬양 C"), draft.songs)
        assertEquals(listOf("소식" to null), draft.announcements.map { it.title to it.body })
        assertEquals("kc-002", draft.updatedBy)
        assertEquals(listOf("찬양 B", "찬양 C"), view.songs)
    }

    @Test
    fun `update with a stale version is an optimistic lock failure`() {
        val draft = edition(LocalDate.of(2026, 10, 11)).apply { version = 3 }
        `when`(editions.findByPublicIdAndDeletedAtIsNull(draft.publicId)).thenReturn(draft)

        assertThrows<ObjectOptimisticLockingFailureException> { serviceAt().update(draft.publicId, request(version = 2), "kc-001") }
        verify(editions, never()).saveAndFlush(any<BulletinEdition>())
    }

    @Test
    fun `a published edition must be withdrawn before it can be edited`() {
        val published = edition(LocalDate.of(2026, 10, 11), BulletinStatus.PUBLISHED, volume = 1)
        `when`(editions.findByPublicIdAndDeletedAtIsNull(published.publicId)).thenReturn(published)

        val e = assertThrows<ResponseStatusException> { serviceAt().update(published.publicId, request(), "kc-001") }
        assertEquals(HttpStatus.CONFLICT, e.statusCode)
    }

    @Test
    fun `a reference on anything but a SCRIPTURE block is a 400`() {
        val draft = edition(LocalDate.of(2026, 10, 11))
        `when`(editions.findByPublicIdAndDeletedAtIsNull(draft.publicId)).thenReturn(draft)
        val blocks =
            listOf(
                BulletinSharingBlockDto(
                    BulletinSharingBlockType.entries.first { it != BulletinSharingBlockType.SCRIPTURE },
                    "본문",
                    "요 3:16",
                ),
            )

        val e = assertThrows<ResponseStatusException> { serviceAt().update(draft.publicId, request(sharingBlocks = blocks), "kc-001") }
        assertEquals(HttpStatus.BAD_REQUEST, e.statusCode)
    }

    // ─── delete ─────────────────────────────────────────────────────────────

    @Test
    fun `a never published draft is soft-deleted`() {
        val draft = edition(LocalDate.of(2026, 10, 11))
        `when`(editions.findByPublicIdAndDeletedAtIsNull(draft.publicId)).thenReturn(draft)

        serviceAt().delete(draft.publicId)

        assertEquals(tuesday.instant(), draft.deletedAt)
        verify(editions).save(draft)
    }

    @Test
    fun `anything that ever had a VOL cannot be deleted`() {
        val withdrawn = edition(LocalDate.of(2026, 10, 11), BulletinStatus.WITHDRAWN, volume = 5)
        `when`(editions.findByPublicIdAndDeletedAtIsNull(withdrawn.publicId)).thenReturn(withdrawn)

        val e = assertThrows<ResponseStatusException> { serviceAt().delete(withdrawn.publicId) }
        assertEquals(HttpStatus.CONFLICT, e.statusCode)
        assertNull(withdrawn.deletedAt)
    }

    // ─── current edition ────────────────────────────────────────────────────

    private fun currentLooksUpTo(
        clock: Clock,
        comingSunday: LocalDate,
    ) {
        serviceAt(clock).currentEdition()
        verify(editions).findFirstByStatusAndServiceDateLessThanEqualAndDeletedAtIsNullOrderByServiceDateDesc(
            BulletinStatus.PUBLISHED,
            comingSunday,
        )
    }

    @Test
    fun `on the Saturday after publishing the current edition is tomorrow's`() {
        currentLooksUpTo(at("2026-10-10T16:00:00Z"), LocalDate.of(2026, 10, 11))
    }

    @Test
    fun `on the Sunday itself the current edition is today's`() {
        currentLooksUpTo(at("2026-10-11T20:00:00Z"), LocalDate.of(2026, 10, 11))
    }

    @Test
    fun `on the Monday after the lookup reaches to the next Sunday and falls back to the latest published`() {
        currentLooksUpTo(at("2026-10-12T06:00:00Z"), LocalDate.of(2026, 10, 18))
    }

    @Test
    fun `with nothing published there is no current edition`() {
        assertNull(serviceAt().currentEdition())
    }
}
