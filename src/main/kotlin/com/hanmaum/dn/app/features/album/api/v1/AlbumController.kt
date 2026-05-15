package com.hanmaum.dn.app.features.album.api.v1

import com.hanmaum.dn.app.features.album.api.v1.dto.AlbumDto
import com.hanmaum.dn.app.features.album.service.AlbumService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/albums")
class AlbumController(
    private val albumService: AlbumService,
) {
    @GetMapping
    fun getAlbums(): List<AlbumDto> = albumService.getAlbums()
}
