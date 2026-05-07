package com.hanmaum.dn.app.features.floorplan.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "floor")
class Floor(
    @Column(name = "floor_number", nullable = false)
    var floorNumber: Int,
    @Column(nullable = false)
    var name: String,
) : BaseEntity()
