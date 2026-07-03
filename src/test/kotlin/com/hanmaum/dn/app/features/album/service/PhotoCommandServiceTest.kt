package com.hanmaum.dn.app.features.album.service

import com.hanmaum.dn.app.common.storage.S3StorageService
import com.hanmaum.dn.app.features.album.domain.Album
import com.hanmaum.dn.app.features.album.domain.AlbumPhoto
import com.hanmaum.dn.app.features.album.domain.PhotoSave
import com.hanmaum.dn.app.features.album.domain.PhotoStatus
import com.hanmaum.dn.app.features.album.repository.AlbumPhotoRepository
import com.hanmaum.dn.app.features.album.repository.PhotoSaveRepository
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.web.server.ResponseStatusException

@ExtendWith(MockitoExtension::class)
class PhotoCommandServiceTest {
    @Mock private lateinit var photoRepository: AlbumPhotoRepository

    @Mock private lateinit var saveRepository: PhotoSaveRepository

    @Mock private lateinit var memberRepository: MemberRepository

    @Mock private lateinit var storage: S3StorageService

    private fun service() = PhotoCommandService(photoRepository, saveRepository, memberRepository, storage)

    private fun fixture(): Triple<Member, Member, AlbumPhoto> {
        val group = ChurchGroup(division = "1구역", name = "소망").also { it.id = 1L }
        val owner =
            Member(lastName = "김", firstName = "주인", group = group).also {
                it.id = 7L
                it.keycloakId = "kc-owner"
            }
        val other =
            Member(lastName = "이", firstName = "남", group = group).also {
                it.id = 8L
                it.keycloakId = "kc-other"
            }
        val album = Album(name = "소망", group = group).also { it.id = 10L }
        val photo =
            AlbumPhoto(album, "albums/${album.publicId}/p.jpg", "image/jpeg", 5L, owner).also {
                it.id = 100L
                it.thumbKey = "albums/${album.publicId}/thumbs/p.jpg"
                it.status = PhotoStatus.ACTIVE
                album.coverPhoto = it
            }
        return Triple(owner, other, photo)
    }

    @Test
    fun `setSaved true creates a save once and is idempotent`() {
        val (owner, _, photo) = fixture()
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-owner")).thenReturn(owner)
        `when`(photoRepository.findByPublicIdAndDeletedAtIsNull(photo.publicId)).thenReturn(photo)
        `when`(saveRepository.findByMemberIdAndPhotoIdAndDeletedAtIsNull(7L, 100L))
            .thenReturn(null)
            .thenReturn(PhotoSave(owner, photo))

        service().setSaved("kc-owner", photo.publicId, true)
        service().setSaved("kc-owner", photo.publicId, true)

        verify(saveRepository, times(1)).save(any<PhotoSave>())
    }

    @Test
    fun `deleteOwnPhoto by owner soft-deletes row, removes S3 objects, clears cover`() {
        val (owner, _, photo) = fixture()
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-owner")).thenReturn(owner)
        `when`(photoRepository.findByPublicIdAndDeletedAtIsNull(photo.publicId)).thenReturn(photo)

        service().deleteOwnPhoto("kc-owner", photo.publicId)

        assertNotNull(photo.deletedAt)
        assertNull(photo.album.coverPhoto)
        verify(storage).deleteObjects(listOf(photo.originalKey, photo.thumbKey!!))
    }

    @Test
    fun `deleteOwnPhoto by another member is rejected with 403 and touches nothing`() {
        val (_, other, photo) = fixture()
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-other")).thenReturn(other)
        `when`(photoRepository.findByPublicIdAndDeletedAtIsNull(photo.publicId)).thenReturn(photo)

        val ex =
            assertThrows(ResponseStatusException::class.java) {
                service().deleteOwnPhoto("kc-other", photo.publicId)
            }

        assertEquals(403, ex.statusCode.value())
        assertNull(photo.deletedAt)
        verify(storage, never()).deleteObjects(any())
    }

    @Test
    fun `setSaved false hard-deletes save and re-save after unsave works`() {
        val (owner, _, photo) = fixture()
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-owner")).thenReturn(owner)
        `when`(photoRepository.findByPublicIdAndDeletedAtIsNull(photo.publicId)).thenReturn(photo)
        val existingSave = PhotoSave(owner, photo)
        `when`(saveRepository.findByMemberIdAndPhotoIdAndDeletedAtIsNull(7L, 100L))
            .thenReturn(null) // first setSaved(true): no existing save, creates new one
            .thenReturn(existingSave) // setSaved(false): existing save found, triggers delete
            .thenReturn(null) // second setSaved(true): no existing save (was hard-deleted), creates new one

        service().setSaved("kc-owner", photo.publicId, true)
        service().setSaved("kc-owner", photo.publicId, false) // hard-delete
        service().setSaved("kc-owner", photo.publicId, true) // re-save

        verify(saveRepository, times(1)).delete(existingSave) // hard delete called once
        verify(saveRepository, times(2)).save(any<PhotoSave>()) // save called twice (initial + re-save)
    }
}
