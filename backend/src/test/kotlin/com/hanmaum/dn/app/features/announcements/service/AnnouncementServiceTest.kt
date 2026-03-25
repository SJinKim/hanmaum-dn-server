package com.hanmaum.dn.app.features.announcements.service

import com.hanmaum.dn.app.common.domainvalue.AnnouncementCategory
import com.hanmaum.dn.app.features.announcements.api.v1.dto.CreateAnnouncementRequest
import com.hanmaum.dn.app.features.announcements.domain.Announcement
import com.hanmaum.dn.app.features.announcements.repository.AnnouncementRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class AnnouncementServiceTest {

    @Mock
    private lateinit var announcementRepository: AnnouncementRepository

    @InjectMocks
    private lateinit var announcementService: AnnouncementService

    // --- getActiveAnnouncements ---

    @Test
    fun `getActiveAnnouncements returns list from repository`() {
        val announcements = listOf(
            Announcement(
                category = AnnouncementCategory.NOTICE,
                title = "공지사항",
                body = "내용",
                startAt = LocalDateTime.now().minusDays(1),
            ),
        )
        `when`(announcementRepository.findActiveAnnouncements(any(LocalDateTime::class.java)))
            .thenReturn(announcements)

        val result = announcementService.getActiveAnnouncements()

        assertEquals(announcements, result)
        verify(announcementRepository).findActiveAnnouncements(any(LocalDateTime::class.java))
    }

    @Test
    fun `getActiveAnnouncements returns empty list when no active announcements`() {
        `when`(announcementRepository.findActiveAnnouncements(any(LocalDateTime::class.java)))
            .thenReturn(emptyList())

        val result = announcementService.getActiveAnnouncements()

        assertEquals(emptyList<Announcement>(), result)
    }

    @Test
    fun `getActiveAnnouncements passes a timestamp close to now`() {
        val before = LocalDateTime.now()
        `when`(announcementRepository.findActiveAnnouncements(any(LocalDateTime::class.java)))
            .thenReturn(emptyList())

        announcementService.getActiveAnnouncements()

        val after = LocalDateTime.now()
        val captor = ArgumentCaptor.forClass(LocalDateTime::class.java)
        verify(announcementRepository).findActiveAnnouncements(captor.capture())
        val captured = captor.value
        assert(!captured.isBefore(before)) { "timestamp should be >= before" }
        assert(!captured.isAfter(after)) { "timestamp should be <= after" }
    }

    // --- createAnnouncement ---

    @Test
    fun `createAnnouncement saves and returns announcement`() {
        val req = CreateAnnouncementRequest(
            title = "새 공지",
            body = "공지 내용",
            startAt = LocalDateTime.now(),
            endAt = null,
            isPinned = false,
            category = AnnouncementCategory.NOTICE.name,
        )
        val saved = Announcement(
            category = AnnouncementCategory.NOTICE,
            title = req.title,
            body = req.body,
            startAt = req.startAt,
        )
        `when`(announcementRepository.save(any(Announcement::class.java))).thenReturn(saved)

        val result = announcementService.createAnnouncement(req)

        assertEquals(saved, result)
        verify(announcementRepository).save(any(Announcement::class.java))
    }

    @Test
    fun `createAnnouncement maps request fields onto entity correctly`() {
        val startAt = LocalDateTime.of(2026, 3, 24, 10, 0)
        val endAt = LocalDateTime.of(2026, 4, 1, 0, 0)
        val req = CreateAnnouncementRequest(
            title = "행사 안내",
            body = "행사 내용입니다",
            startAt = startAt,
            endAt = endAt,
            isPinned = true,
            category = AnnouncementCategory.EVENT.name,
        )
        `when`(announcementRepository.save(any(Announcement::class.java))).thenAnswer { it.arguments[0] }

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
        val req = CreateAnnouncementRequest(
            title = "잘못된 카테고리",
            body = "내용",
            startAt = LocalDateTime.now(),
            category = "INVALID_CATEGORY",
        )

        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            announcementService.createAnnouncement(req)
        }
    }
}