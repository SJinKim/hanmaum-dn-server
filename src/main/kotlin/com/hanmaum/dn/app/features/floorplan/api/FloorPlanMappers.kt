package com.hanmaum.dn.app.features.floorplan.api

import com.hanmaum.dn.app.features.floorplan.api.v1.dto.FloorResponse
import com.hanmaum.dn.app.features.floorplan.api.v1.dto.RoomResponse
import com.hanmaum.dn.app.features.floorplan.domain.Floor
import com.hanmaum.dn.app.features.floorplan.domain.Room

fun Floor.toResponse() =
    FloorResponse(
        id = publicId.toString(),
        floorNumber = floorNumber,
        name = name,
    )

fun Room.toResponse() =
    RoomResponse(
        id = publicId.toString(),
        floorId = floor.publicId.toString(),
        name = name,
        description = description,
        points = points,
    )
