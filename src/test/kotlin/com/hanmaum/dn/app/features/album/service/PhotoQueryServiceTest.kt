package com.hanmaum.dn.app.features.album.service

import com.hanmaum.dn.app.common.storage.S3StorageService
import com.hanmaum.dn.app.features.album.domain.Album
import com.hanmaum.dn.app.features.album.domain.AlbumPhoto
import com.hanmaum.dn.app.features.album.domain.PhotoStatus
import com.hanmaum.dn.app.features.album.repository.AlbumPhotoRepository
import com.hanmaum.dn.app.features.album.repository.AlbumRepository
import com.hanmaum.dn.app.features.album.repository.PhotoSaveRepository
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PhotoQueryServiceTest {
    @Mock private lateinit var albumRepository: AlbumRepository

    @Mock private lateinit var photoRepository: AlbumPhotoRepository

    @Mock private lateinit var saveRepository: PhotoSaveRepository

    @Mock private lateinit var memberRepository: MemberRepository

    @Mock private lateinit var storage: S3StorageService

    private fun service() = PhotoQueryService(albumRepository, photoRepository, saveRepository, memberRepository, storage)

    @Test
    fun `returns page of dtos with presigned urls and mine flag`() {
        val group = ChurchGroup(division = "1구역", name = "소망").also { it.id = 1L }
        val album = Album(name = "소망", group = group).also { it.id = 10L }
        val me =
            Member(lastName = "김", firstName = "철수", group = group).also {
                it.id = 7L
                it.keycloakId = "kc-7"
            }
        val photo =
            AlbumPhoto(
                album = album,
                originalKey = "albums/${album.publicId}/p.jpg",
                contentType = "image/jpeg",
                sizeBytes = 5L,
                uploadedBy = me,
            ).also {
                it.id = 100L
                it.thumbKey = "albums/${album.publicId}/thumbs/p.jpg"
                it.takenAt = Instant.parse("2026-07-01T10:00:00Z")
                it.status = PhotoStatus.ACTIVE
            }
        `when`(albumRepository.findByPublicIdAndDeletedAtIsNull(album.publicId)).thenReturn(album)
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-7")).thenReturn(me)
        `when`(
            photoRepository.findByAlbumIdAndStatusAndDeletedAtIsNullOrderByTakenAtDesc(
                10L,
                PhotoStatus.ACTIVE,
                PageRequest.of(0, 60),
            ),
        ).thenReturn(PageImpl(listOf(photo), PageRequest.of(0, 60), 1))
        `when`(saveRepository.findByMemberIdAndPhotoIdAndDeletedAtIsNull(7L, 100L)).thenReturn(null)
        `when`(storage.presignGet("albums/${album.publicId}/thumbs/p.jpg")).thenReturn("https://signed/thumb")
        `when`(storage.presignGet("albums/${album.publicId}/p.jpg")).thenReturn("https://signed/orig")

        val result = service().getAlbumPhotos(album.publicId, page = 0, size = 60, requesterKeycloakId = "kc-7")

        assertEquals(1, result.items.size)
        assertEquals(photo.publicId, result.items[0].photoId)
        assertEquals("https://signed/thumb", result.items[0].thumbUrl)
        assertEquals("https://signed/orig", result.items[0].originalUrl)
        assertTrue(result.items[0].mine)
        assertEquals(1L, result.totalItems)
    }

    @Test
    fun `unknown album yields 404`() {
        val missing = UUID.randomUUID()
        `when`(albumRepository.findByPublicIdAndDeletedAtIsNull(missing)).thenReturn(null)

        assertThrows(ResponseStatusException::class.java) {
            service().getAlbumPhotos(missing, 0, 60, "kc-7")
        }
    }
}
