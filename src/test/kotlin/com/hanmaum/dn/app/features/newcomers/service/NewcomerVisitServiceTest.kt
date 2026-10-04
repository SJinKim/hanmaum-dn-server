package com.hanmaum.dn.app.features.newcomers.service

import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.CreateNewcomerVisitRequest
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.UpdateNewcomerVisitRequest
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerLifecycle
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerProfile
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerVisit
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerVisitSource
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerVisitType
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerProfileRepository
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerVisitRepository
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
import org.mockito.kotlin.argumentCaptor
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class NewcomerVisitServiceTest {
    @Mock private lateinit var visitRepo: NewcomerVisitRepository

    @Mock private lateinit var profileRepo: NewcomerProfileRepository

    private lateinit var service: NewcomerVisitService

    // Sunday 2026-06-14, 10:30 in Berlin.
    private val sunday = LocalDate.of(2026, 6, 14)
    private val clock = Clock.fixed(Instant.parse("2026-06-14T08:30:00Z"), ZoneId.of("Europe/Berlin"))

    @BeforeEach
    fun setUp() {
        service = NewcomerVisitService(visitRepo, profileRepo, clock)
    }

    private fun profile(lifecycle: NewcomerLifecycle = NewcomerLifecycle.SUBMITTED) =
        NewcomerProfile(member = Member(lastName = "홍", firstName = "길동"), lifecycleStatus = lifecycle)

    private fun visit(
        date: LocalDate = sunday,
        type: NewcomerVisitType = NewcomerVisitType.FIRST,
        source: NewcomerVisitSource? = null,
        profile: NewcomerProfile? = null,
    ) = NewcomerVisit(
        visitDate = date,
        lastName = "홍",
        firstName = "길동",
        visitType = type,
        source = source,
        newcomerProfile = profile,
    )

    // ─── create ───────────────────────────────────────────────────────────────

    @Test
    fun `create defaults the visit date to today and trims the names`() {
        `when`(visitRepo.save(any<NewcomerVisit>())).thenAnswer { it.arguments[0] }

        val row =
            service.create(
                CreateNewcomerVisitRequest(lastName = " 홍 ", firstName = "길동 ", gender = Gender.M, birthYear = 2000, note = "  "),
            )

        val saved = argumentCaptor<NewcomerVisit>()
        verify(visitRepo).save(saved.capture())
        assertEquals(sunday, saved.firstValue.visitDate)
        assertEquals("홍길동", row.fullName)
        assertEquals(2000, row.birthYear)
        assertNull(row.note)
        assertEquals(NewcomerVisitType.FIRST, row.visitType)
    }

    @Test
    fun `create for a future date is a 400 and saves nothing`() {
        val ex =
            assertThrows<ResponseStatusException> {
                service.create(CreateNewcomerVisitRequest(visitDate = sunday.plusDays(1), lastName = "홍", firstName = "길동"))
            }

        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        verify(visitRepo, never()).save(any<NewcomerVisit>())
    }

    // ─── list ─────────────────────────────────────────────────────────────────

    @Test
    fun `list without bounds covers everything up to today`() {
        `when`(visitRepo.findPageInRange(LocalDate.of(2000, 1, 1), sunday, PageRequest.of(0, 20)))
            .thenReturn(PageImpl(listOf(visit()), PageRequest.of(0, 20), 1))

        val page = service.list(null, null, 0, 20)

        assertEquals(1, page.totalElements)
        assertEquals("홍길동", page.content.single().fullName)
    }

    @Test
    fun `list has no one-year cap`() {
        val from = sunday.minusYears(3)
        `when`(visitRepo.findPageInRange(from, sunday, PageRequest.of(1, 50)))
            .thenReturn(PageImpl(emptyList(), PageRequest.of(1, 50), 0))

        assertEquals(0, service.list(from, sunday, 1, 50).totalElements)
    }

    @Test
    fun `list with from after to is a 400`() {
        val ex = assertThrows<ResponseStatusException> { service.list(sunday, sunday.minusDays(1), 0, 20) }

        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `list with a size over 100 or a negative page is a 400`() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows<ResponseStatusException> { service.list(null, null, 0, 101) }.statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows<ResponseStatusException> { service.list(null, null, -1, 20) }.statusCode)
    }

    // ─── update / delete ──────────────────────────────────────────────────────

    @Test
    fun `update changes only the given fields`() {
        val existing = visit(source = NewcomerVisitSource.FRIEND_FAMILY)
        `when`(visitRepo.findActive(existing.publicId)).thenReturn(Optional.of(existing))

        val row = service.update(existing.publicId, UpdateNewcomerVisitRequest(visitType = NewcomerVisitType.REVISIT))

        assertEquals(NewcomerVisitType.REVISIT, row.visitType)
        assertEquals(NewcomerVisitSource.FRIEND_FAMILY, row.source)
        assertEquals("길동", row.firstName)
    }

    @Test
    fun `update of an unknown visit is a 404`() {
        val unknown = UUID.randomUUID()
        `when`(visitRepo.findActive(unknown)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { service.update(unknown, UpdateNewcomerVisitRequest()) }
    }

    @Test
    fun `softDelete sets deletedAt`() {
        val existing = visit()
        `when`(visitRepo.findActive(existing.publicId)).thenReturn(Optional.of(existing))

        service.softDelete(existing.publicId)

        assertNotNull(existing.deletedAt)
    }

    // ─── linkProfile ──────────────────────────────────────────────────────────

    @Test
    fun `linkProfile attaches the 새가족 profile and null removes it`() {
        val existing = visit()
        val newcomer = profile(NewcomerLifecycle.IN_CARE)
        `when`(visitRepo.findActive(existing.publicId)).thenReturn(Optional.of(existing))
        `when`(profileRepo.findByPublicIdAndDeletedAtIsNull(newcomer.publicId)).thenReturn(Optional.of(newcomer))

        val linked = service.linkProfile(existing.publicId, newcomer.publicId)
        assertEquals(newcomer.publicId.toString(), linked.newcomerPublicId)
        assertEquals(NewcomerLifecycle.IN_CARE, linked.newcomerLifecycle)

        val unlinked = service.linkProfile(existing.publicId, null)
        assertNull(unlinked.newcomerPublicId)
    }

    @Test
    fun `linkProfile to an unknown newcomer is a 404`() {
        val existing = visit()
        val unknown = UUID.randomUUID()
        `when`(visitRepo.findActive(existing.publicId)).thenReturn(Optional.of(existing))
        `when`(profileRepo.findByPublicIdAndDeletedAtIsNull(unknown)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { service.linkProfile(existing.publicId, unknown) }
    }

    // ─── stats ────────────────────────────────────────────────────────────────

    @Test
    fun `stats counts the funnel, the sources and the days`() {
        val from = sunday.minusWeeks(1)
        val deletedProfile = profile(NewcomerLifecycle.GRADUATED).also { it.deletedAt = Instant.now() }
        `when`(visitRepo.findInRange(from, sunday)).thenReturn(
            listOf(
                visit(source = NewcomerVisitSource.ADVERTISEMENT, profile = profile(NewcomerLifecycle.GRADUATED)),
                visit(source = NewcomerVisitSource.ADVERTISEMENT, profile = profile(NewcomerLifecycle.IN_CARE)),
                visit(type = NewcomerVisitType.REVISIT, source = NewcomerVisitSource.FRIEND_FAMILY),
                visit(date = from, profile = deletedProfile),
            ),
        )

        val stats = service.stats(from, sunday)

        assertEquals(4, stats.visits)
        assertEquals(3, stats.firstVisits)
        assertEquals(1, stats.revisits)
        assertEquals(2, stats.registered)
        assertEquals(1, stats.graduated)
        assertEquals(NewcomerVisitSource.ADVERTISEMENT, stats.bySource.first().source)
        assertEquals(2, stats.bySource.first().count)
        assertEquals(listOf(from, sunday), stats.byDay.map { it.date })
        assertEquals(listOf(1, 3), stats.byDay.map { it.count })
    }
}
