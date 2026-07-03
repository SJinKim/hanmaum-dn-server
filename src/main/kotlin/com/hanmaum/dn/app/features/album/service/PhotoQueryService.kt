package com.hanmaum.dn.app.features.album.service

import com.hanmaum.dn.app.common.storage.S3StorageService
import com.hanmaum.dn.app.features.album.api.v1.dto.PhotoDto
import com.hanmaum.dn.app.features.album.api.v1.dto.PhotoPageDto
import com.hanmaum.dn.app.features.album.domain.AlbumPhoto
import com.hanmaum.dn.app.features.album.domain.PhotoStatus
import com.hanmaum.dn.app.features.album.repository.AlbumPhotoRepository
import com.hanmaum.dn.app.features.album.repository.AlbumRepository
import com.hanmaum.dn.app.features.album.repository.PhotoSaveRepository
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class PhotoQueryService(
    private val albumRepository: AlbumRepository,
    private val photoRepository: AlbumPhotoRepository,
    private val saveRepository: PhotoSaveRepository,
    private val memberRepository: MemberRepository,
    private val storage: S3StorageService,
) {
    @Transactional(readOnly = true)
    fun getAlbumPhotos(
        albumPublicId: UUID,
        page: Int,
        size: Int,
        requesterKeycloakId: String,
    ): PhotoPageDto {
        val album =
            albumRepository.findByPublicIdAndDeletedAtIsNull(albumPublicId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found")
        val requesterId = memberRepository.findByKeycloakIdAndDeletedAtIsNull(requesterKeycloakId)?.id
        val photoPage =
            photoRepository.findByAlbumIdAndStatusAndDeletedAtIsNullOrderByTakenAtDesc(
                album.id!!,
                PhotoStatus.ACTIVE,
                PageRequest.of(page, size.coerceIn(1, 100)),
            )
        return PhotoPageDto(
            items =
                photoPage.content.map { photo ->
                    val saved =
                        requesterId != null &&
                            saveRepository.findByMemberIdAndPhotoIdAndDeletedAtIsNull(requesterId, photo.id!!) != null
                    toDto(photo, requesterId, saved)
                },
            page = photoPage.number,
            totalPages = photoPage.totalPages,
            totalItems = photoPage.totalElements,
        )
    }

    fun toDto(
        photo: AlbumPhoto,
        requesterMemberId: Long?,
        saved: Boolean,
    ): PhotoDto =
        PhotoDto(
            photoId = photo.publicId,
            thumbUrl = photo.thumbKey?.let { storage.presignGet(it) },
            originalUrl = storage.presignGet(photo.originalKey),
            takenAt = photo.takenAt,
            uploadedBy = "${photo.uploadedBy.lastName}${photo.uploadedBy.firstName}",
            mine = requesterMemberId != null && photo.uploadedBy.id == requesterMemberId,
            saved = saved,
        )
}
