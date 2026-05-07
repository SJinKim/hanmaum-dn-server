package com.hanmaum.dn.app.features.announcements.api.v1

import com.hanmaum.dn.app.features.announcements.api.toDto
import com.hanmaum.dn.app.features.announcements.api.v1.dto.AnnouncementDto
import com.hanmaum.dn.app.features.announcements.api.v1.dto.CreateAnnouncementRequest
import com.hanmaum.dn.app.features.announcements.service.AnnouncementService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/announcements")
class AnnouncementController(
    private val announcementService: AnnouncementService,
) {
    // Öffentlich für die App (oder nur für eingeloggte User)
    @GetMapping
    fun getAnnouncements(): List<AnnouncementDto> = announcementService.getActiveAnnouncements().map { it.toDto() }

    // Admin Only (später absichern mit @PreAuthorize)
    @PostMapping
    fun createAnnouncement(
        @RequestBody createAnnouncementRequest: CreateAnnouncementRequest,
    ): AnnouncementDto = announcementService.createAnnouncement(createAnnouncementRequest).toDto()
}
