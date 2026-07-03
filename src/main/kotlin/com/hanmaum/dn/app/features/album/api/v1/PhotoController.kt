package com.hanmaum.dn.app.features.album.api.v1

import com.hanmaum.dn.app.features.album.api.v1.dto.CalendarDayDto
import com.hanmaum.dn.app.features.album.api.v1.dto.ConfirmUploadRequest
import com.hanmaum.dn.app.features.album.api.v1.dto.PhotoDto
import com.hanmaum.dn.app.features.album.api.v1.dto.PhotoPageDto
import com.hanmaum.dn.app.features.album.api.v1.dto.PresignUploadRequest
import com.hanmaum.dn.app.features.album.api.v1.dto.PresignUploadResponse
import com.hanmaum.dn.app.features.album.service.PhotoCommandService
import com.hanmaum.dn.app.features.album.service.PhotoQueryService
import com.hanmaum.dn.app.features.album.service.PhotoUploadService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@RestController
@RequestMapping("/photos")
class PhotoController(
    private val photoUploadService: PhotoUploadService,
    private val photoQueryService: PhotoQueryService,
    private val photoCommandService: PhotoCommandService,
) {
    @PostMapping("/presign")
    fun presign(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: PresignUploadRequest,
    ): PresignUploadResponse = photoUploadService.presign(jwt.subject, request)

    @PostMapping("/confirm")
    fun confirm(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: ConfirmUploadRequest,
    ): Map<String, Int> = mapOf("activated" to photoUploadService.confirm(jwt.subject, request))

    @GetMapping("/calendar")
    fun calendar(
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM") month: YearMonth,
    ): List<CalendarDayDto> = photoQueryService.getCalendar(month)

    @GetMapping
    fun photosByDate(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @AuthenticationPrincipal jwt: Jwt,
    ): List<PhotoDto> = photoQueryService.getPhotosByDate(date, jwt.subject)

    @GetMapping("/saved")
    fun savedPhotos(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "60") size: Int,
    ): PhotoPageDto = photoQueryService.getSavedPhotos(jwt.subject, page, size)

    @PutMapping("/{photoId}/save")
    fun save(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable photoId: UUID,
    ) = photoCommandService.setSaved(jwt.subject, photoId, saved = true)

    @DeleteMapping("/{photoId}/save")
    fun unsave(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable photoId: UUID,
    ) = photoCommandService.setSaved(jwt.subject, photoId, saved = false)

    @DeleteMapping("/{photoId}")
    fun deletePhoto(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable photoId: UUID,
    ) = photoCommandService.deleteOwnPhoto(jwt.subject, photoId)
}
