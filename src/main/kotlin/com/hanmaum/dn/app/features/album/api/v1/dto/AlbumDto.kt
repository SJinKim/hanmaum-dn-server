package com.hanmaum.dn.app.features.album.api.v1.dto

import java.util.UUID

data class AlbumDto(
    val publicId: UUID,
    val name: String,
    val groupPublicId: UUID?,
)
