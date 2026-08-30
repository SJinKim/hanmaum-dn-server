package com.hanmaum.dn.app.features.announcements.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.announcements.api.toDto
import com.hanmaum.dn.app.features.announcements.api.v1.dto.AnnouncementDto
import com.hanmaum.dn.app.features.announcements.api.v1.dto.CreateAnnouncementRequest
import com.hanmaum.dn.app.features.announcements.api.v1.dto.UpdateAnnouncementRequest
import com.hanmaum.dn.app.features.announcements.service.AnnouncementService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/announcements")
class AnnouncementController(
    private val announcementService: AnnouncementService,
) {
    /** Returns currently active announcements for the mobile feed and home carousel. */
    @GetMapping
    fun getAnnouncements(): ResponseEntity<ApiResponse<List<AnnouncementDto>>> {
        val data = announcementService.getActiveAnnouncements().map { it.toDto() }
        return ResponseEntity.ok(ApiResponse.success(data = data))
    }

    /** Returns one active announcement and records a detail view. */
    @GetMapping("/{publicId}")
    fun getAnnouncement(
        @PathVariable publicId: UUID,
    ): ResponseEntity<ApiResponse<AnnouncementDto>> {
        val data = announcementService.getActiveAnnouncement(publicId).toDto()
        return ResponseEntity.ok(ApiResponse.success(data = data))
    }

    /** Returns all non-deleted announcements for the admin dashboard. */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    fun getAnnouncementsForAdmin(): ResponseEntity<ApiResponse<List<AnnouncementDto>>> {
        val data = announcementService.getAllForAdmin().map { it.toDto() }
        return ResponseEntity.ok(ApiResponse.success(data = data))
    }

    /** Creates an announcement as an administrator. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun createAnnouncement(
        @RequestBody createAnnouncementRequest: CreateAnnouncementRequest,
    ): ResponseEntity<ApiResponse<AnnouncementDto>> {
        val data = announcementService.createAnnouncement(createAnnouncementRequest).toDto()
        return ResponseEntity.ok(ApiResponse.success(data = data))
    }

    /** Fully replaces editable announcement data as an administrator. */
    @PutMapping("/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateAnnouncement(
        @PathVariable publicId: UUID,
        @RequestBody request: UpdateAnnouncementRequest,
    ): ResponseEntity<ApiResponse<AnnouncementDto>> {
        val data = announcementService.updateAnnouncement(publicId, request).toDto()
        return ResponseEntity.ok(ApiResponse.success(data = data))
    }

    /** Moves an announcement to the admin trash for delayed deletion. */
    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAnnouncement(
        @PathVariable publicId: UUID,
    ) {
        announcementService.softDeleteAnnouncement(publicId)
    }
}
