package com.hanmaum.dn.app.features.album.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "albums")
class Album(
    @Column(nullable = false, length = 200)
    var name: String,
    @Column(name = "pcloud_code", nullable = false, unique = true, length = 100)
    val pcloudCode: String,
    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
) : BaseEntity()
