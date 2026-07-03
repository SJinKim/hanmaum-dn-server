package com.hanmaum.dn.app.features.album.service

import com.hanmaum.dn.app.common.storage.S3StorageService
import com.hanmaum.dn.app.features.album.api.v1.dto.AlbumDto
import com.hanmaum.dn.app.features.album.domain.Album
import com.hanmaum.dn.app.features.album.domain.PhotoStatus
import com.hanmaum.dn.app.features.album.repository.AlbumPhotoRepository
import com.hanmaum.dn.app.features.album.repository.AlbumRepository
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AlbumService(
    private val albumRepository: AlbumRepository,
    private val photoRepository: AlbumPhotoRepository,
    private val groupRepository: ChurchGroupRepository,
    private val storage: S3StorageService,
) {
    /** One album per 순 — created lazily so new groups get an album without admin action. */
    @Transactional
    fun provisionMissingGroupAlbums() {
        groupRepository
            .findAllByDeletedAtIsNullOrderByDivisionAscNameAsc()
            .forEach { group ->
                if (albumRepository.findByGroupIdAndDeletedAtIsNull(group.id!!) == null) {
                    albumRepository.save(Album(name = group.name, group = group))
                }
            }
    }

    @Transactional
    fun getAlbums(): List<AlbumDto> {
        provisionMissingGroupAlbums()
        return albumRepository.findAllActive().map { album ->
            AlbumDto(
                publicId = album.publicId,
                name = album.name,
                groupPublicId = album.group?.publicId,
                photoCount = photoRepository.countByAlbumIdAndStatusAndDeletedAtIsNull(album.id!!, PhotoStatus.ACTIVE),
                coverThumbUrl = album.coverPhoto?.thumbKey?.let { storage.presignGet(it) },
            )
        }
    }
}
