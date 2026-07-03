package com.hanmaum.dn.app.features.album.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

enum class PhotoStatus { PENDING, ACTIVE }

@Entity
@Table(name = "album_photos")
class AlbumPhoto(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_id", nullable = false)
    val album: Album,
    // var (not val): presign computes the final key from this entity's own publicId after construction.
    @Column(name = "original_key", nullable = false, columnDefinition = "TEXT")
    var originalKey: String,
    @Column(name = "content_type", nullable = false, length = 100)
    val contentType: String,
    @Column(name = "size_bytes", nullable = false)
    val sizeBytes: Long,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_member_id", nullable = false)
    val uploadedBy: Member,
) : BaseEntity() {
    @Column(name = "thumb_key", columnDefinition = "TEXT")
    var thumbKey: String? = null

    // EXIF capture date when available, else set to upload time at confirm (drives the calendar).
    @Column(name = "taken_at")
    var takenAt: Instant? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PhotoStatus = PhotoStatus.PENDING
}
