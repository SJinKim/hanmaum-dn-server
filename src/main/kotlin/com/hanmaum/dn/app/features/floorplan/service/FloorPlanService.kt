package com.hanmaum.dn.app.features.floorplan.service

import com.hanmaum.dn.app.features.floorplan.domain.Floor
import com.hanmaum.dn.app.features.floorplan.domain.Room
import com.hanmaum.dn.app.features.floorplan.repository.FloorRepository
import com.hanmaum.dn.app.features.floorplan.repository.RoomRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class FloorPlanService(
    private val floorRepository: FloorRepository,
    private val roomRepository: RoomRepository,
) {
    fun getFloors(): List<Floor> = floorRepository.findAllActive()

    fun getRooms(floorPublicId: UUID): List<Room> = roomRepository.findActiveByFloorPublicId(floorPublicId)
}
