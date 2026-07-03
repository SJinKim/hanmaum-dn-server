package com.hanmaum.dn.app.features.album.api.v1

import com.hanmaum.dn.app.features.album.api.v1.dto.AlbumDto
import com.hanmaum.dn.app.features.album.api.v1.dto.PhotoPageDto
import com.hanmaum.dn.app.features.album.service.AlbumService
import com.hanmaum.dn.app.features.album.service.PhotoQueryService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/albums")
class AlbumController(
    private val albumService: AlbumService,
    private val photoQueryService: PhotoQueryService,
) {
    @GetMapping
    fun getAlbums(): List<AlbumDto> = albumService.getAlbums()

    @GetMapping("/{publicId}/photos")
    fun getAlbumPhotos(
        @PathVariable publicId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "60") size: Int,
        @AuthenticationPrincipal jwt: Jwt,
    ): PhotoPageDto = photoQueryService.getAlbumPhotos(publicId, page, size, jwt.subject)
}
