package com.hanmaum.dn.app.features.events.service

import com.hanmaum.dn.app.common.domainvalue.AnnouncementCategory
import com.hanmaum.dn.app.features.announcements.domain.Announcement
import com.hanmaum.dn.app.features.announcements.repository.AnnouncementRepository
import com.hanmaum.dn.app.features.events.api.v1.dto.CreateEventRsvpRequest
import com.hanmaum.dn.app.features.events.api.v1.dto.UpdateEventRsvpRequest
import com.hanmaum.dn.app.features.events.domain.EventRsvp
import com.hanmaum.dn.app.features.events.repository.EventRsvpLogRepository
import com.hanmaum.dn.app.features.events.repository.EventRsvpRepository
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.springframework.web.server.ResponseStatusException
import java.lang.reflect.Field
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class EventRsvpServiceTest {
    @Mock private lateinit var eventRsvpRepo: EventRsvpRepository

    @Mock private lateinit var eventRsvpLogRepo: EventRsvpLogRepository

    @Mock private lateinit var memberRepo: MemberRepository

    @Mock private lateinit var announcementRepo: AnnouncementRepository

    private lateinit var service: EventRsvpService

    private val berlinZone = ZoneId.of("Europe/Berlin")

    // Fixed clock: 2026-07-12T10:00:00+02:00 (Berlin summer time)
    private val fixedInstant = Instant.parse("2026-07-12T08:00:00Z")
    private val clock = Clock.fixed(fixedInstant, berlinZone)
    private val now = OffsetDateTime.ofInstant(fixedInstant, berlinZone)

    @BeforeEach
    fun setUp() {
        service = EventRsvpService(eventRsvpRepo, eventRsvpLogRepo, memberRepo, announcementRepo, clock)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun makeRsvp(
        id: Long = 1L,
        title: String = "여름 수련회",
        windowStart: OffsetDateTime = now.minusHours(1),
        windowEnd: OffsetDateTime = now.plusHours(2),
        isActive: Boolean = true,
        announcement: Announcement? = null,
    ): EventRsvp {
        val rsvp =
            EventRsvp(
                announcement = announcement,
                title = title,
                windowStart = windowStart,
                windowEnd = windowEnd,
                isActive = isActive,
            )
        setId(rsvp, id)
        return rsvp
    }

    private fun makeAnnouncement(
        id: Long = 10L,
        category: AnnouncementCategory = AnnouncementCategory.EVENT,
    ): Announcement {
        val a =
            Announcement(
                category = category,
                title = "여름 행사",
                body = "내용",
                startAt = now,
            )
        setId(a, id)
        return a
    }

    private fun makeGroup(id: Long = 7L): ChurchGroup {
        val g = ChurchGroup(division = "청년부", name = "다니엘조")
        setId(g, id)
        return g
    }

    private fun makeMember(
        id: Long = 1L,
        keycloakId: String = "kc-001",
        group: ChurchGroup? = null,
    ): Member {
        val m = Member(lastName = "김", firstName = "철수", group = group)
        setId(m, id)
        setField(m, Member::class.java, "keycloakId", keycloakId)
        return m
    }

    private fun setId(
        entity: Any,
        id: Long,
    ) {
        val field: Field = entity.javaClass.superclass.getDeclaredField("id")
        field.isAccessible = true
        field.set(entity, id)
    }

    private fun setField(
        entity: Any,
        clazz: Class<*>,
        name: String,
        value: Any?,
    ) {
        val field: Field = clazz.getDeclaredField(name)
        field.isAccessible = true
        field.set(entity, value)
    }

    // ─── createRsvp ───────────────────────────────────────────────────────────

    @Test
    fun `createRsvp saves and returns dto`() {
        val req =
            CreateEventRsvpRequest(
                title = "여름 수련회",
                windowStart = now.minusHours(1),
                windowEnd = now.plusHours(2),
            )
        val saved = makeRsvp()
        `when`(eventRsvpRepo.save(any())).thenReturn(saved)

        val result = service.createRsvp(req)

        assertEquals("여름 수련회", result.title)
        verify(eventRsvpRepo).save(any())
    }

    @Test
    fun `createRsvp rejects invalid window`() {
        val req =
            CreateEventRsvpRequest(
                title = "잘못된",
                windowStart = now.plusHours(2),
                windowEnd = now.minusHours(1),
            )

        val ex = assertThrows<ResponseStatusException> { service.createRsvp(req) }

        assertEquals(400, ex.statusCode.value())
        verify(eventRsvpRepo, never()).save(any())
    }

    @Test
    fun `createRsvp links valid EVENT announcement`() {
        val announcement = makeAnnouncement()
        val req =
            CreateEventRsvpRequest(
                title = "여름 수련회",
                windowStart = now.minusHours(1),
                windowEnd = now.plusHours(2),
                announcementId = announcement.publicId,
            )
        val saved = makeRsvp(announcement = announcement)
        `when`(announcementRepo.findByPublicIdAndDeleteEntryAtIsNull(announcement.publicId))
            .thenReturn(Optional.of(announcement))
        `when`(eventRsvpRepo.save(any())).thenReturn(saved)

        val result = service.createRsvp(req)

        assertEquals(announcement.publicId.toString(), result.announcementPublicId)
    }

    @Test
    fun `createRsvp rejects non-EVENT announcement category`() {
        val announcement = makeAnnouncement(category = AnnouncementCategory.NOTICE)
        val req =
            CreateEventRsvpRequest(
                title = "여름 수련회",
                windowStart = now.minusHours(1),
                windowEnd = now.plusHours(2),
                announcementId = announcement.publicId,
            )
        `when`(announcementRepo.findByPublicIdAndDeleteEntryAtIsNull(announcement.publicId))
            .thenReturn(Optional.of(announcement))

        val ex = assertThrows<ResponseStatusException> { service.createRsvp(req) }

        assertEquals(400, ex.statusCode.value())
        verify(eventRsvpRepo, never()).save(any())
    }

    @Test
    fun `createRsvp rejects announcement ID that does not exist in DB`() {
        val unknownId = UUID.randomUUID()
        val req =
            CreateEventRsvpRequest(
                title = "여름 수련회",
                windowStart = now.minusHours(1),
                windowEnd = now.plusHours(2),
                announcementId = unknownId,
            )
        `when`(announcementRepo.findByPublicIdAndDeleteEntryAtIsNull(unknownId))
            .thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { service.createRsvp(req) }

        verify(eventRsvpRepo, never()).save(any())
    }

    // ─── updateRsvp ───────────────────────────────────────────────────────────

    @Test
    fun `updateRsvp patches only non-null fields`() {
        val rsvp = makeRsvp()
        `when`(eventRsvpRepo.findByPublicIdAndDeletedAtIsNull(rsvp.publicId))
            .thenReturn(Optional.of(rsvp))

        val result = service.updateRsvp(rsvp.publicId, UpdateEventRsvpRequest(title = "새 이름"))

        assertEquals("새 이름", result.title)
        assertEquals(rsvp.windowStart, result.windowStart)
    }

    @Test
    fun `updateRsvp rejects window where end is not after start`() {
        val rsvp = makeRsvp()
        `when`(eventRsvpRepo.findByPublicIdAndDeletedAtIsNull(rsvp.publicId))
            .thenReturn(Optional.of(rsvp))

        val ex =
            assertThrows<ResponseStatusException> {
                service.updateRsvp(
                    rsvp.publicId,
                    UpdateEventRsvpRequest(windowEnd = rsvp.windowStart.minusMinutes(1)),
                )
            }

        assertEquals(400, ex.statusCode.value())
    }

    // ─── deactivateRsvp ───────────────────────────────────────────────────────

    @Test
    fun `deactivateRsvp sets isActive to false without soft-deleting`() {
        val rsvp = makeRsvp()
        `when`(eventRsvpRepo.findByPublicIdAndDeletedAtIsNull(rsvp.publicId))
            .thenReturn(Optional.of(rsvp))

        service.deactivateRsvp(rsvp.publicId)

        assertFalse(rsvp.isActive)
        assertNull(rsvp.deletedAt)
    }

    // ─── getActiveRsvps ───────────────────────────────────────────────────────

    @Test
    fun `getActiveRsvps returns only events with open window`() {
        val active = makeRsvp(title = "열린 행사")
        `when`(eventRsvpRepo.findActiveNow(now)).thenReturn(listOf(active))

        val result = service.getActiveRsvps()

        assertEquals(1, result.size)
        assertEquals("열린 행사", result[0].title)
    }

    @Test
    fun `getActiveRsvps returns empty list when no window is open`() {
        `when`(eventRsvpRepo.findActiveNow(now)).thenReturn(emptyList())

        val result = service.getActiveRsvps()

        assertEquals(0, result.size)
    }
}
