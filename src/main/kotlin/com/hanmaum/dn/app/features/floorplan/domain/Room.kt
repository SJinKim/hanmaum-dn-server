package com.hanmaum.dn.app.features.floorplan.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "room")
class Room(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    var floor: Floor,
    @Column(nullable = false)
    var name: String,
    @Column(columnDefinition = "TEXT", nullable = false)
    var description: String = "",
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var points: List<List<Double>> = emptyList(),
) : BaseEntity()
