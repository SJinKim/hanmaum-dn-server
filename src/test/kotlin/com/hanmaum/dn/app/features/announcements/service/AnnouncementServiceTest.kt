package com.hanmaum.dn.app.features.announcements.service

import com.hanmaum.dn.app.common.domainvalue.AnnouncementCategory
import com.hanmaum.dn.app.features.announcements.api.v1.dto.CreateAnnouncementRequest
import com.hanmaum.dn.app.features.announcements.api.v1.dto.UpdateAnnouncementRequest
import com.hanmaum.dn.app.features.announcements.domain.Announcement
import com.hanmaum.dn.app.features.announcements.repository.AnnouncementRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.springframework.context.ApplicationEventPublisher
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AnnouncementServiceTest {
    @Mock
    private lateinit var announcementRepository: AnnouncementRepository

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    @InjectMocks
    private lateinit var announcementService: AnnouncementService

    // --- getActiveAnnouncements ---

    @Test
    fun `getActiveAnnouncements returns list from repository`() {
        val announcements =
            listOf(
                Announcement(
                    category = AnnouncementCategory.NOTICE,
                    title = "공지사항",
                    body = "내용",
                    startAt = OffsetDateTime.now().minusDays(1),
                ),
            )
        `when`(announcementRepository.findActiveAnnouncements(any<OffsetDateTime>()))
            .thenReturn(announcements)

        val result = announcementService.getActiveAnnouncements()

        assertEquals(announcements, result)
        verify(announcementRepository).findActiveAnnouncements(any<OffsetDateTime>())
    }

    @Test
    fun `getActiveAnnouncements returns empty list when no active announcements`() {
        `when`(announcementRepository.findActiveAnnouncements(any<OffsetDateTime>()))
            .thenReturn(emptyList())

        val result = announcementService.getActiveAnnouncements()

        assertEquals(emptyList<Announcement>(), result)
    }

    @Test
    fun `getActiveAnnouncements passes a timestamp close to now`() {
        val before = OffsetDateTime.now(ZoneOffset.UTC)
        `when`(announcementRepository.findActiveAnnouncements(any<OffsetDateTime>()))
            .thenReturn(emptyList())

        announcementService.getActiveAnnouncements()

        val after = OffsetDateTime.now(ZoneOffset.UTC)
        val captor = argumentCaptor<OffsetDateTime>()
        verify(announcementRepository).findActiveAnnouncements(captor.capture())
        val captured = captor.firstValue.toInstant()
        assert(!captured.isBefore(before.toInstant())) { "timestamp should be >= before" }
        assert(!captured.isAfter(after.toInstant())) { "timestamp should be <= after" }
    }

    @Test
    fun `getActiveAnnouncement increments view count before returning announcement`() {
        val publicId = UUID.randomUUID()
        val announcement =
            Announcement(
                category = AnnouncementCategory.EVENT,
                title = "여름 수련회",
                body = "수련회 안내",
                startAt = OffsetDateTime.now().minusDays(1),
                imageUrl = "https://cdn.example.org/retreat.jpg",
                location = "교회 본당",
                viewCount = 8,
            )
        `when`(announcementRepository.incrementActiveViewCount(org.mockito.kotlin.eq(publicId), any()))
            .thenReturn(1)
        `when`(announcementRepository.findByPublicIdAndDeleteEntryAtIsNull(publicId))
            .thenReturn(Optional.of(announcement))

        val result = announcementService.getActiveAnnouncement(publicId)

        assertEquals(announcement, result)
        verify(announcementRepository).incrementActiveViewCount(org.mockito.kotlin.eq(publicId), any())
        verify(announcementRepository).findByPublicIdAndDeleteEntryAtIsNull(publicId)
    }

    @Test
    fun `getActiveAnnouncement rejects announcements outside active window`() {
        val publicId = UUID.randomUUID()
        `when`(announcementRepository.incrementActiveViewCount(org.mockito.kotlin.eq(publicId), any()))
            .thenReturn(0)

        assertThrows<jakarta.persistence.EntityNotFoundException> {
            announcementService.getActiveAnnouncement(publicId)
        }

        verify(announcementRepository, never()).findByPublicIdAndDeleteEntryAtIsNull(publicId)
    }

    // --- createAnnouncement ---

    @Test
    fun `createAnnouncement saves and returns announcement`() {
        val req =
            CreateAnnouncementRequest(
                title = "새 공지",
                body = "공지 내용",
                startAt = OffsetDateTime.now(),
                endAt = null,
                isPinned = false,
                category = AnnouncementCategory.NOTICE.name,
            )
        val saved =
            Announcement(
                category = AnnouncementCategory.NOTICE,
                title = req.title,
                body = req.body,
                startAt = req.startAt,
            )
        `when`(announcementRepository.save(any<Announcement>())).thenReturn(saved)

        val result = announcementService.createAnnouncement(req)

        assertEquals(saved, result)
        verify(announcementRepository).save(any<Announcement>())
    }

    @Test
    fun `createAnnouncement maps request fields onto entity correctly`() {
        val startAt = OffsetDateTime.of(2026, 3, 24, 10, 0, 0, 0, ZoneOffset.UTC)
        val endAt = OffsetDateTime.of(2026, 4, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val req =
            CreateAnnouncementRequest(
                title = "행사 안내",
                body = "행사 내용입니다",
                startAt = startAt,
                endAt = endAt,
                imageUrl = "https://cdn.example.org/event.jpg",
                location = "교회 본당",
                isPinned = true,
                category = AnnouncementCategory.EVENT.name,
            )
        `when`(announcementRepository.save(any<Announcement>())).thenAnswer { it.arguments[0] }

        val result = announcementService.createAnnouncement(req)

        assertEquals(req.title, result.title)
        assertEquals(req.body, result.body)
        assertEquals(startAt, result.startAt)
        assertEquals(endAt, result.endAt)
        assertEquals(req.imageUrl, result.imageUrl)
        assertEquals(req.location, result.location)
        assertEquals(true, result.isPinned)
        assertEquals(AnnouncementCategory.EVENT, result.category)
    }

    @Test
    fun `createAnnouncement with invalid category throws IllegalArgumentException`() {
        val req =
            CreateAnnouncementRequest(
                title = "잘못된 카테고리",
                body = "내용",
                startAt = OffsetDateTime.now(),
                category = "INVALID_CATEGORY",
            )

        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            announcementService.createAnnouncement(req)
        }
    }

    @Test
    fun `createAnnouncement publishes event when startAt is in the past`() {
        val startAt = OffsetDateTime.now().minusDays(1)
        val req =
            CreateAnnouncementRequest(
                title = "지난 공지",
                body = "내용",
                startAt = startAt,
                category = AnnouncementCategory.NOTICE.name,
            )
        val saved =
            Announcement(
                category = AnnouncementCategory.NOTICE,
                title = req.title,
                body = req.body,
                startAt = startAt,
            )
        `when`(announcementRepository.save(any<Announcement>())).thenReturn(saved)

        announcementService.createAnnouncement(req)

        verify(eventPublisher).publishEvent(AnnouncementCreatedEvent(saved.publicId, saved.title))
    }

    @Test
    fun `createAnnouncement does not publish event when startAt is in the future`() {
        val startAt = OffsetDateTime.now().plusDays(1)
        val req =
            CreateAnnouncementRequest(
                title = "미래 공지",
                body = "내용",
                startAt = startAt,
                category = AnnouncementCategory.NOTICE.name,
            )
        val saved =
            Announcement(
                category = AnnouncementCategory.NOTICE,
                title = req.title,
                body = req.body,
                startAt = startAt,
            )
        `when`(announcementRepository.save(any<Announcement>())).thenReturn(saved)

        announcementService.createAnnouncement(req)

        verify(eventPublisher, never()).publishEvent(any<AnnouncementCreatedEvent>())
    }

    @Test
    fun `updateAnnouncement replaces image and location but preserves view count`() {
        val publicId = UUID.randomUUID()
        val announcement =
            Announcement(
                category = AnnouncementCategory.NOTICE,
                title = "기존 공지",
                body = "기존 내용",
                startAt = OffsetDateTime.now().minusDays(1),
                viewCount = 12,
            )
        val request =
            UpdateAnnouncementRequest(
                title = "수정 공지",
                body = "수정 내용",
                startAt = announcement.startAt,
                imageUrl = "https://cdn.example.org/updated.jpg",
                location = "교육관",
                category = AnnouncementCategory.EVENT.name,
            )
        `when`(announcementRepository.findByPublicIdAndDeleteEntryAtIsNull(publicId))
            .thenReturn(Optional.of(announcement))
        `when`(announcementRepository.save(announcement)).thenReturn(announcement)

        val result = announcementService.updateAnnouncement(publicId, request)

        assertEquals(request.imageUrl, result.imageUrl)
        assertEquals(request.location, result.location)
        assertEquals(12, result.viewCount)
    }
}
