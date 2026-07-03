package com.hanmaum.dn.app.features.album.service

import com.hanmaum.dn.app.common.storage.S3StorageService
import com.hanmaum.dn.app.features.album.domain.Album
import com.hanmaum.dn.app.features.album.domain.AlbumPhoto
import com.hanmaum.dn.app.features.album.domain.PhotoStatus
import com.hanmaum.dn.app.features.album.repository.AlbumPhotoRepository
import com.hanmaum.dn.app.features.album.repository.AlbumRepository
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.members.domain.Member
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class AlbumServiceTest {
    @Mock private lateinit var albumRepository: AlbumRepository

    @Mock private lateinit var photoRepository: AlbumPhotoRepository

    @Mock private lateinit var groupRepository: ChurchGroupRepository

    @Mock private lateinit var storage: S3StorageService

    private fun service() = AlbumService(albumRepository, photoRepository, groupRepository, storage)

    @Test
    fun `provisions an album for every group that has none`() {
        val grouped = ChurchGroup(division = "1구역", name = "소망").also { it.id = 1L }
        val ungrouped = ChurchGroup(division = "1구역", name = "믿음").also { it.id = 2L }
        `when`(groupRepository.findAllByDeletedAtIsNullOrderByDivisionAscNameAsc())
            .thenReturn(listOf(grouped, ungrouped))
        `when`(albumRepository.findByGroupIdAndDeletedAtIsNull(1L))
            .thenReturn(Album(name = "소망", group = grouped))
        `when`(albumRepository.findByGroupIdAndDeletedAtIsNull(2L)).thenReturn(null)

        service().provisionMissingGroupAlbums()

        verify(albumRepository).save(any(Album::class.java))
    }

    @Test
    fun `getAlbums maps tiles with photo count and presigned cover url`() {
        val group = ChurchGroup(division = "1구역", name = "소망").also { it.id = 1L }
        val album = Album(name = "소망", group = group).also { it.id = 10L }
        val member = Member(lastName = "김", firstName = "철수", group = group)
        val cover =
            AlbumPhoto(
                album = album,
                originalKey = "albums/${album.publicId}/c.jpg",
                contentType = "image/jpeg",
                sizeBytes = 5L,
                uploadedBy = member,
            ).also { it.thumbKey = "albums/${album.publicId}/thumbs/c.jpg" }
        album.coverPhoto = cover
        val empty = Album(name = "믿음").also { it.id = 11L }
        `when`(groupRepository.findAllByDeletedAtIsNullOrderByDivisionAscNameAsc()).thenReturn(emptyList())
        `when`(albumRepository.findAllActive()).thenReturn(listOf(album, empty))
        `when`(photoRepository.countByAlbumIdAndStatusAndDeletedAtIsNull(10L, PhotoStatus.ACTIVE)).thenReturn(3L)
        `when`(photoRepository.countByAlbumIdAndStatusAndDeletedAtIsNull(11L, PhotoStatus.ACTIVE)).thenReturn(0L)
        `when`(storage.presignGet("albums/${album.publicId}/thumbs/c.jpg")).thenReturn("https://signed/cover")

        val result = service().getAlbums()

        assertEquals(2, result.size)
        assertEquals(album.publicId, result[0].publicId)
        assertEquals(group.publicId, result[0].groupPublicId)
        assertEquals(3L, result[0].photoCount)
        assertEquals("https://signed/cover", result[0].coverThumbUrl)
        assertNull(result[1].coverThumbUrl)
        verify(storage, never()).presignGet("albums/${empty.publicId}/thumbs/c.jpg")
    }
}
