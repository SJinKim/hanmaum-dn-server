package com.hanmaum.dn.app.features.album.api.v1

import com.hanmaum.dn.app.features.album.api.v1.dto.ConfirmUploadRequest
import com.hanmaum.dn.app.features.album.api.v1.dto.PresignUploadRequest
import com.hanmaum.dn.app.features.album.api.v1.dto.PresignUploadResponse
import com.hanmaum.dn.app.features.album.service.PhotoUploadService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/photos")
class PhotoController(
    private val photoUploadService: PhotoUploadService,
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
}
