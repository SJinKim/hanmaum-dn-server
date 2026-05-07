package com.hanmaum.dn.app.features.floorplan.repository

import com.hanmaum.dn.app.features.floorplan.domain.Room
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface RoomRepository : JpaRepository<Room, Long> {
    @Query("SELECT r FROM Room r WHERE r.floor.publicId = :floorPublicId AND r.deletedAt IS NULL")
    fun findActiveByFloorPublicId(floorPublicId: UUID): List<Room>
}
