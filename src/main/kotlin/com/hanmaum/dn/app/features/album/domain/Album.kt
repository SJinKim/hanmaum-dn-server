package com.hanmaum.dn.app.features.album.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

@Entity
@Table(name = "albums")
class Album(
    @Column(nullable = false, length = 200)
    var name: String,
    // One album per 순 (unique). Null = special album (legacy/common) — none exist at launch.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", unique = true)
    val group: ChurchGroup? = null,
    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
) : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_photo_id")
    var coverPhoto: AlbumPhoto? = null
}
