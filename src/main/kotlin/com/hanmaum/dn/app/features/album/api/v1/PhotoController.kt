package com.hanmaum.dn.app.features.album.api.v1

import com.hanmaum.dn.app.features.album.api.v1.dto.CalendarDayDto
import com.hanmaum.dn.app.features.album.api.v1.dto.ConfirmUploadRequest
import com.hanmaum.dn.app.features.album.api.v1.dto.PhotoDto
import com.hanmaum.dn.app.features.album.api.v1.dto.PresignUploadRequest
import com.hanmaum.dn.app.features.album.api.v1.dto.PresignUploadResponse
import com.hanmaum.dn.app.features.album.service.PhotoQueryService
import com.hanmaum.dn.app.features.album.service.PhotoUploadService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.YearMonth

@RestController
@RequestMapping("/photos")
class PhotoController(
    private val photoUploadService: PhotoUploadService,
    private val photoQueryService: PhotoQueryService,
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
}
