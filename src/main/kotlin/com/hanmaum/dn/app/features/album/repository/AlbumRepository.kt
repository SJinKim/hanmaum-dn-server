package com.hanmaum.dn.app.features.album.repository

import com.hanmaum.dn.app.features.album.domain.Album
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface AlbumRepository : JpaRepository<Album, Long> {
    @Query("SELECT a FROM Album a WHERE a.deletedAt IS NULL ORDER BY a.displayOrder ASC, a.id ASC")
    fun findAllActive(): List<Album>

    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Album?

    fun findByGroupIdAndDeletedAtIsNull(groupId: Long): Album?
}
