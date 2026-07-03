package com.hanmaum.dn.app.features.album.service

import com.hanmaum.dn.app.common.storage.S3StorageService
import com.hanmaum.dn.app.features.album.domain.Album
import com.hanmaum.dn.app.features.album.domain.AlbumPhoto
import com.hanmaum.dn.app.features.album.domain.PhotoStatus
import com.hanmaum.dn.app.features.album.repository.AlbumPhotoRepository
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

@ExtendWith(MockitoExtension::class)
class PhotoCleanupJobTest {
    @Mock private lateinit var photoRepository: AlbumPhotoRepository

    @Mock private lateinit var storage: S3StorageService

    @Test
    fun `stale PENDING photos are soft-deleted and their objects removed`() {
        val group = ChurchGroup(division = "1구역", name = "소망").also { it.id = 1L }
        val member = Member(lastName = "김", firstName = "철수", group = group).also { it.id = 7L }
        val album = Album(name = "소망", group = group).also { it.id = 10L }
        val stale = AlbumPhoto(album, "albums/${album.publicId}/s.jpg", "image/jpeg", 1L, member)
        `when`(photoRepository.findByStatusAndCreatedAtBefore(eq(PhotoStatus.PENDING), any()))
            .thenReturn(listOf(stale))

        PhotoCleanupJob(photoRepository, storage).cleanupStalePendingPhotos()

        assertNotNull(stale.deletedAt)
        verify(storage).deleteObjects(listOf(stale.originalKey))
    }
}
