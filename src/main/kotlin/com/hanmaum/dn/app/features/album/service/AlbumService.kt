package com.hanmaum.dn.app.features.album.service

import com.hanmaum.dn.app.features.album.api.v1.dto.AlbumDto
import com.hanmaum.dn.app.features.album.repository.AlbumRepository
import org.springframework.stereotype.Service

@Service
class AlbumService(
    private val albumRepository: AlbumRepository,
) {
    fun getAlbums(): List<AlbumDto> =
        albumRepository.findAllActive().map { album ->
            AlbumDto(
                publicId = album.publicId,
                name = album.name,
                pcloudCode = album.pcloudCode,
                displayOrder = album.displayOrder,
            )
        }
}
