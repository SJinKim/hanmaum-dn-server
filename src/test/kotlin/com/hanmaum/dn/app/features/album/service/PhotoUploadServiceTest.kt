package com.hanmaum.dn.app.features.album.service

import com.hanmaum.dn.app.common.storage.S3StorageService
import com.hanmaum.dn.app.features.album.api.v1.dto.ConfirmUploadRequest
import com.hanmaum.dn.app.features.album.api.v1.dto.PresignFile
import com.hanmaum.dn.app.features.album.api.v1.dto.PresignUploadRequest
import com.hanmaum.dn.app.features.album.domain.Album
import com.hanmaum.dn.app.features.album.domain.AlbumPhoto
import com.hanmaum.dn.app.features.album.domain.PhotoStatus
import com.hanmaum.dn.app.features.album.repository.AlbumPhotoRepository
import com.hanmaum.dn.app.features.album.repository.AlbumRepository
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.springframework.web.server.ResponseStatusException
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@ExtendWith(MockitoExtension::class)
class PhotoUploadServiceTest {
    @Mock private lateinit var albumRepository: AlbumRepository

    @Mock private lateinit var photoRepository: AlbumPhotoRepository

    @Mock private lateinit var memberRepository: MemberRepository

    @Mock private lateinit var storage: S3StorageService

    private fun service() = PhotoUploadService(albumRepository, photoRepository, memberRepository, storage)

    private fun memberWithGroup(): Member {
        val group = ChurchGroup(division = "1구역", name = "소망").also { it.id = 1L }
        return Member(lastName = "김", firstName = "철수", group = group).also {
            it.id = 7L
            it.keycloakId = "kc-7"
        }
    }

    @Test
    fun `presign rejects member without a group with 403`() {
        val loner =
            Member(lastName = "박", firstName = "혼자").also {
                it.id = 9L
                it.keycloakId = "kc-9"
            }
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-9")).thenReturn(loner)

        val request = PresignUploadRequest(listOf(PresignFile("a.jpg", "image/jpeg", 100L)))
        val ex =
            assertThrows(ResponseStatusException::class.java) {
                service().presign("kc-9", request)
            }
        assertEquals(403, ex.statusCode.value())
    }

    @Test
    fun `presign creates PENDING rows in the uploader group album and returns urls`() {
        val member = memberWithGroup()
        val album = Album(name = "소망", group = member.group).also { it.id = 10L }
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-7")).thenReturn(member)
        `when`(albumRepository.findByGroupIdAndDeletedAtIsNull(1L)).thenReturn(album)
        `when`(photoRepository.save(any<AlbumPhoto>())).thenAnswer { it.arguments[0] }
        `when`(storage.presignPut(anyString(), eq("image/jpeg"))).thenReturn("https://signed/put")

        val response =
            service().presign(
                "kc-7",
                PresignUploadRequest(listOf(PresignFile("a.jpg", "image/jpeg", 100L))),
            )

        assertEquals(1, response.items.size)
        assertEquals("https://signed/put", response.items[0].uploadUrl)
        val captor = argumentCaptor<AlbumPhoto>()
        verify(photoRepository).save(captor.capture())
        assertEquals(PhotoStatus.PENDING, captor.firstValue.status)
        assertTrue(captor.firstValue.originalKey.startsWith("albums/${album.publicId}/"))
        assertTrue(captor.firstValue.originalKey.endsWith(".jpg"))
    }

    @Test
    fun `presign rejects oversize and over-count batches with 400`() {
        val member = memberWithGroup()
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-7")).thenReturn(member)

        val oversize = PresignUploadRequest(listOf(PresignFile("big.jpg", "image/jpeg", 21_000_000L)))
        assertEquals(
            400,
            assertThrows(ResponseStatusException::class.java) { service().presign("kc-7", oversize) }
                .statusCode
                .value(),
        )

        val tooMany = PresignUploadRequest((1..31).map { PresignFile("f$it.jpg", "image/jpeg", 10L) })
        assertEquals(
            400,
            assertThrows(ResponseStatusException::class.java) { service().presign("kc-7", tooMany) }
                .statusCode
                .value(),
        )
    }

    @Test
    fun `presign rejects heic files with 400 since client must transcode to jpeg`() {
        val member = memberWithGroup()
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-7")).thenReturn(member)

        val heic = PresignUploadRequest(listOf(PresignFile("x.heic", "image/heic", 100L)))
        assertEquals(
            400,
            assertThrows(ResponseStatusException::class.java) { service().presign("kc-7", heic) }
                .statusCode
                .value(),
        )
    }

    @Test
    fun `confirm activates photo, writes thumb, sets takenAt fallback and album cover`() {
        val member = memberWithGroup()
        val album = Album(name = "소망", group = member.group).also { it.id = 10L }
        val photo =
            AlbumPhoto(
                album = album,
                originalKey = "albums/${album.publicId}/p.jpg",
                contentType = "image/jpeg",
                sizeBytes = 100L,
                uploadedBy = member,
            )
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-7")).thenReturn(member)
        `when`(photoRepository.findByPublicIdAndDeletedAtIsNull(photo.publicId)).thenReturn(photo)
        `when`(storage.objectExists(photo.originalKey)).thenReturn(true)

        val image = BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB)
        val jpegBytes = ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
        `when`(storage.getObjectBytes(photo.originalKey)).thenReturn(jpegBytes)

        val activated = service().confirm("kc-7", ConfirmUploadRequest(listOf(photo.publicId)))

        assertEquals(1, activated)
        assertEquals(PhotoStatus.ACTIVE, photo.status)
        assertEquals("albums/${album.publicId}/thumbs/${photo.publicId}.jpg", photo.thumbKey)
        assertTrue(photo.takenAt != null)
        assertEquals(photo, album.coverPhoto)
        verify(storage).putObject(eq(photo.thumbKey!!), any(), eq("image/jpeg"))
    }

    @Test
    fun `confirm skips missing S3 objects and photos of other members`() {
        val member = memberWithGroup()
        val other = Member(lastName = "이", firstName = "남").also { it.id = 8L }
        val album = Album(name = "소망", group = member.group).also { it.id = 10L }
        val missing =
            AlbumPhoto(album, "albums/${album.publicId}/m.jpg", "image/jpeg", 1L, member)
        val foreign =
            AlbumPhoto(album, "albums/${album.publicId}/f.jpg", "image/jpeg", 1L, other)
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-7")).thenReturn(member)
        `when`(photoRepository.findByPublicIdAndDeletedAtIsNull(missing.publicId)).thenReturn(missing)
        `when`(photoRepository.findByPublicIdAndDeletedAtIsNull(foreign.publicId)).thenReturn(foreign)
        `when`(storage.objectExists(missing.originalKey)).thenReturn(false)

        val activated =
            service().confirm("kc-7", ConfirmUploadRequest(listOf(missing.publicId, foreign.publicId)))

        assertEquals(0, activated)
        assertEquals(PhotoStatus.PENDING, missing.status)
        assertEquals(PhotoStatus.PENDING, foreign.status)
    }

    @Test
    fun `confirm isolates per-photo failures so one corrupt photo doesn't block the batch`() {
        val member = memberWithGroup()
        val album = Album(name = "소망", group = member.group).also { it.id = 10L }
        val corrupt =
            AlbumPhoto(album, "albums/${album.publicId}/corrupt.jpg", "image/jpeg", 1L, member)
        val valid =
            AlbumPhoto(album, "albums/${album.publicId}/valid.jpg", "image/jpeg", 1L, member)

        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-7")).thenReturn(member)
        `when`(photoRepository.findByPublicIdAndDeletedAtIsNull(corrupt.publicId)).thenReturn(corrupt)
        `when`(photoRepository.findByPublicIdAndDeletedAtIsNull(valid.publicId)).thenReturn(valid)
        `when`(storage.objectExists(corrupt.originalKey)).thenReturn(true)
        `when`(storage.objectExists(valid.originalKey)).thenReturn(true)
        `when`(storage.getObjectBytes(corrupt.originalKey)).thenReturn("not-an-image".toByteArray())

        val image = BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB)
        val jpegBytes = ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
        `when`(storage.getObjectBytes(valid.originalKey)).thenReturn(jpegBytes)

        val activated =
            service().confirm("kc-7", ConfirmUploadRequest(listOf(corrupt.publicId, valid.publicId)))

        assertEquals(1, activated)
        assertEquals(PhotoStatus.PENDING, corrupt.status)
        assertEquals(null, corrupt.thumbKey)
        assertEquals(PhotoStatus.ACTIVE, valid.status)
    }
}
