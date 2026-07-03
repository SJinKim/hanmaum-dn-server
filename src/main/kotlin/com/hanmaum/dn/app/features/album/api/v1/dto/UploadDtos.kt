package com.hanmaum.dn.app.features.album.api.v1.dto

import java.util.UUID

data class PresignFile(
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
)

data class PresignUploadRequest(
    val files: List<PresignFile>,
)

data class PresignedUpload(
    val photoId: UUID,
    val uploadUrl: String,
)

data class PresignUploadResponse(
    val items: List<PresignedUpload>,
)

data class ConfirmUploadRequest(
    val photoIds: List<UUID>,
)
