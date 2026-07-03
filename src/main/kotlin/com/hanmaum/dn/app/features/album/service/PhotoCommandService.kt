package com.hanmaum.dn.app.features.album.service

import com.hanmaum.dn.app.common.storage.S3StorageService
import com.hanmaum.dn.app.features.album.domain.PhotoSave
import com.hanmaum.dn.app.features.album.repository.AlbumPhotoRepository
import com.hanmaum.dn.app.features.album.repository.PhotoSaveRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class PhotoCommandService(
    private val photoRepository: AlbumPhotoRepository,
    private val saveRepository: PhotoSaveRepository,
    private val memberRepository: MemberRepository,
    private val storage: S3StorageService,
) {
    @Transactional
    fun setSaved(
        requesterKeycloakId: String,
        photoId: UUID,
        saved: Boolean,
    ) {
        val member = requireMember(requesterKeycloakId)
        val photo =
            photoRepository.findByPublicIdAndDeletedAtIsNull(photoId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found")
        val existing = saveRepository.findByMemberIdAndPhotoIdAndDeletedAtIsNull(member.id!!, photo.id!!)
        if (saved && existing == null) {
            saveRepository.save(PhotoSave(member = member, photo = photo))
        }
        if (!saved && existing != null) {
            saveRepository.delete(existing)
        }
    }

    /** Q4: a member may delete their own photos only. */
    @Transactional
    fun deleteOwnPhoto(
        requesterKeycloakId: String,
        photoId: UUID,
    ) {
        val member = requireMember(requesterKeycloakId)
        val photo =
            photoRepository.findByPublicIdAndDeletedAtIsNull(photoId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found")
        if (photo.uploadedBy.id != member.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 올린 사진만 삭제할 수 있습니다")
        }
        if (photo.album.coverPhoto?.id == photo.id) {
            photo.album.coverPhoto = null
        }
        photo.deletedAt = Instant.now()
        storage.deleteObjects(listOfNotNull(photo.originalKey, photo.thumbKey))
    }

    private fun requireMember(keycloakId: String): Member =
        memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Member not found")
}
