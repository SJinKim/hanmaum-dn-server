package com.hanmaum.dn.app.features.announcements.service

import com.hanmaum.dn.app.features.announcements.api.toEntity
import com.hanmaum.dn.app.features.announcements.api.v1.dto.CreateAnnouncementRequest
import com.hanmaum.dn.app.features.announcements.domain.Announcement
import com.hanmaum.dn.app.features.announcements.repository.AnnouncementRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class AnnouncementService(
    private val announcementRepository: AnnouncementRepository,
) {
    // für die App (nur aktive)
    fun getActiveAnnouncements(): List<Announcement> = announcementRepository.findActiveAnnouncements(LocalDateTime.now())

    // for Admin Dashboard (create)
    fun createAnnouncement(req: CreateAnnouncementRequest): Announcement = announcementRepository.save(req.toEntity())
}
