package com.hanmaum.dn.app.features.floorplan.repository

import com.hanmaum.dn.app.features.floorplan.domain.Floor
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface FloorRepository : JpaRepository<Floor, Long> {
    @Query("SELECT f FROM Floor f WHERE f.deletedAt IS NULL ORDER BY f.floorNumber ASC")
    fun findAllActive(): List<Floor>
}
