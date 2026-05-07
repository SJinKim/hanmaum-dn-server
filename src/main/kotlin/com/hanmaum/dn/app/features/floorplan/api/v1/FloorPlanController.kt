package com.hanmaum.dn.app.features.floorplan.api.v1

import com.hanmaum.dn.app.features.floorplan.api.toResponse
import com.hanmaum.dn.app.features.floorplan.api.v1.dto.FloorResponse
import com.hanmaum.dn.app.features.floorplan.api.v1.dto.RoomResponse
import com.hanmaum.dn.app.features.floorplan.service.FloorPlanService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/floorplan")
class FloorPlanController(
    private val floorPlanService: FloorPlanService,
) {
    /** Returns all active floors ordered by floor number. */
    @GetMapping("/floors")
    fun getFloors(): List<FloorResponse> = floorPlanService.getFloors().map { it.toResponse() }

    /** Returns all active rooms belonging to the given floor. */
    @GetMapping("/floors/{floorId}/rooms")
    fun getRooms(
        @PathVariable floorId: UUID,
    ): List<RoomResponse> = floorPlanService.getRooms(floorId).map { it.toResponse() }
}
