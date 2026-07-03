package com.hanmaum.dn.app.features.album.repository

import com.hanmaum.dn.app.features.album.domain.PhotoSave
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PhotoSaveRepository : JpaRepository<PhotoSave, Long> {
    fun findByMemberIdAndPhotoIdAndDeletedAtIsNull(
        memberId: Long,
        photoId: Long,
    ): PhotoSave?

    @Query(
        "SELECT s FROM PhotoSave s JOIN FETCH s.photo p WHERE s.member.id = :memberId " +
            "AND s.deletedAt IS NULL AND p.status = 'ACTIVE' AND p.deletedAt IS NULL " +
            "ORDER BY s.createdAt DESC",
    )
    fun findSavedByMember(
        @Param("memberId") memberId: Long,
        pageable: Pageable,
    ): Page<PhotoSave>
}
