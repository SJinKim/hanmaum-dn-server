package com.hanmaum.dn.app.features.announcements.service

import com.hanmaum.dn.app.features.announcements.api.applyUpdate
import com.hanmaum.dn.app.features.announcements.api.toEntity
import com.hanmaum.dn.app.features.announcements.api.v1.dto.CreateAnnouncementRequest
import com.hanmaum.dn.app.features.announcements.api.v1.dto.UpdateAnnouncementRequest
import com.hanmaum.dn.app.features.announcements.domain.Announcement
import com.hanmaum.dn.app.features.announcements.repository.AnnouncementRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

@Service
class AnnouncementService(
    private val announcementRepository: AnnouncementRepository,
) {
    // für die App (nur aktive)
    fun getActiveAnnouncements(): List<Announcement> =
        announcementRepository.findActiveAnnouncements(
            OffsetDateTime.now(ZoneId.of("Europe/Berlin")),
        )

    // for Admin Dashboard: all not-soft-deleted announcements
    fun getAllForAdmin(): List<Announcement> = announcementRepository.findAllNotDeleted()

    // for Admin Dashboard (create)
    @Transactional
    fun createAnnouncement(req: CreateAnnouncementRequest): Announcement = announcementRepository.save(req.toEntity())

    @Transactional
    fun updateAnnouncement(
        publicId: UUID,
        req: UpdateAnnouncementRequest,
    ): Announcement {
        val announcement =
            announcementRepository
                .findByPublicIdAndDeleteEntryAtIsNull(publicId)
                .orElseThrow { EntityNotFoundException("Announcement not found: $publicId") }
        announcement.applyUpdate(req)
        return announcementRepository.save(announcement)
    }

    // Admin trash: mark for deletion. The cleanup cron hard-deletes 30 days later.
    // deleted_at is intentionally left null and gets set automatically at purge time.
    @Transactional
    fun softDeleteAnnouncement(publicId: UUID) {
        val announcement =
            announcementRepository
                .findByPublicIdAndDeleteEntryAtIsNull(publicId)
                .orElseThrow { EntityNotFoundException("Announcement not found or already deleted: $publicId") }
        announcement.deleteEntryAt = Instant.now()
        announcementRepository.save(announcement)
    }
}
