package com.hanmaum.dn.app.features.announcements.service

import com.hanmaum.dn.app.common.domainvalue.AnnouncementCategory
import com.hanmaum.dn.app.features.announcements.api.v1.dto.CreateAnnouncementRequest
import com.hanmaum.dn.app.features.announcements.domain.Announcement
import com.hanmaum.dn.app.features.announcements.repository.AnnouncementRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import java.time.OffsetDateTime
import java.time.ZoneOffset

@ExtendWith(MockitoExtension::class)
class AnnouncementServiceTest {
    @Mock
    private lateinit var announcementRepository: AnnouncementRepository

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
                isPinned = true,
                category = AnnouncementCategory.EVENT.name,
            )
        `when`(announcementRepository.save(any<Announcement>())).thenAnswer { it.arguments[0] }

        val result = announcementService.createAnnouncement(req)

        assertEquals(req.title, result.title)
        assertEquals(req.body, result.body)
        assertEquals(startAt, result.startAt)
        assertEquals(endAt, result.endAt)
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
}
