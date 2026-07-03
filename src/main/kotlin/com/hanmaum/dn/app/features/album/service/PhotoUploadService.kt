package com.hanmaum.dn.app.features.album.service

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.hanmaum.dn.app.common.storage.S3StorageService
import com.hanmaum.dn.app.features.album.api.v1.dto.ConfirmUploadRequest
import com.hanmaum.dn.app.features.album.api.v1.dto.PresignUploadRequest
import com.hanmaum.dn.app.features.album.api.v1.dto.PresignUploadResponse
import com.hanmaum.dn.app.features.album.api.v1.dto.PresignedUpload
import com.hanmaum.dn.app.features.album.domain.Album
import com.hanmaum.dn.app.features.album.domain.AlbumPhoto
import com.hanmaum.dn.app.features.album.domain.PhotoStatus
import com.hanmaum.dn.app.features.album.repository.AlbumPhotoRepository
import com.hanmaum.dn.app.features.album.repository.AlbumRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import net.coobird.thumbnailator.Thumbnails
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant

@Service
class PhotoUploadService(
    private val albumRepository: AlbumRepository,
    private val photoRepository: AlbumPhotoRepository,
    private val memberRepository: MemberRepository,
    private val storage: S3StorageService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val MAX_FILES_PER_BATCH = 30
        const val MAX_FILE_SIZE_BYTES = 20_971_520L
        const val THUMB_MAX_EDGE = 640
        val ALLOWED_CONTENT_TYPES =
            mapOf(
                "image/jpeg" to "jpg",
                "image/png" to "png",
                "image/heic" to "heic",
                "image/webp" to "webp",
            )
    }

    @Transactional
    fun presign(
        requesterKeycloakId: String,
        request: PresignUploadRequest,
    ): PresignUploadResponse {
        val member = requireMember(requesterKeycloakId)
        val group =
            member.group
                ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "업로드는 순에 소속된 멤버만 가능합니다")
        if (request.files.isEmpty() || request.files.size > MAX_FILES_PER_BATCH) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "1–$MAX_FILES_PER_BATCH files per request")
        }
        request.files.forEach { file ->
            if (file.sizeBytes > MAX_FILE_SIZE_BYTES) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "File too large: ${file.fileName}")
            }
            if (file.contentType !in ALLOWED_CONTENT_TYPES) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported type: ${file.contentType}")
            }
        }
        val album =
            albumRepository.findByGroupIdAndDeletedAtIsNull(group.id!!)
                ?: albumRepository.save(Album(name = group.name, group = group))

        val items =
            request.files.map { file ->
                val photo =
                    AlbumPhoto(
                        album = album,
                        originalKey = "",
                        contentType = file.contentType,
                        sizeBytes = file.sizeBytes,
                        uploadedBy = member,
                    )
                photo.originalKey =
                    "albums/${album.publicId}/${photo.publicId}.${ALLOWED_CONTENT_TYPES.getValue(file.contentType)}"
                val persisted = photoRepository.save(photo)
                PresignedUpload(
                    photoId = persisted.publicId,
                    uploadUrl = storage.presignPut(persisted.originalKey, file.contentType),
                )
            }
        return PresignUploadResponse(items)
    }

    @Transactional
    fun confirm(
        requesterKeycloakId: String,
        request: ConfirmUploadRequest,
    ): Int {
        val member = requireMember(requesterKeycloakId)
        var activated = 0
        request.photoIds.forEach { photoId ->
            val photo = photoRepository.findByPublicIdAndDeletedAtIsNull(photoId) ?: return@forEach
            if (photo.uploadedBy.id != member.id || photo.status != PhotoStatus.PENDING) return@forEach
            if (!storage.objectExists(photo.originalKey)) return@forEach

            val bytes = storage.getObjectBytes(photo.originalKey)
            photo.takenAt = extractTakenAt(bytes) ?: Instant.now()
            photo.thumbKey = "albums/${photo.album.publicId}/thumbs/${photo.publicId}.jpg"
            storage.putObject(photo.thumbKey!!, buildThumbnail(bytes), "image/jpeg")
            photo.status = PhotoStatus.ACTIVE
            photo.album.coverPhoto = photo
            activated++
        }
        return activated
    }

    private fun requireMember(keycloakId: String): Member =
        memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Member not found")

    private fun extractTakenAt(bytes: ByteArray): Instant? =
        try {
            ImageMetadataReader
                .readMetadata(ByteArrayInputStream(bytes))
                .getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
                ?.dateOriginal
                ?.toInstant()
        } catch (e: Exception) {
            log.debug("No EXIF date extracted: {}", e.message)
            null
        }

    private fun buildThumbnail(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        Thumbnails
            .of(ByteArrayInputStream(bytes))
            .size(THUMB_MAX_EDGE, THUMB_MAX_EDGE)
            .outputFormat("jpg")
            .outputQuality(0.8)
            .toOutputStream(out)
        return out.toByteArray()
    }
}
