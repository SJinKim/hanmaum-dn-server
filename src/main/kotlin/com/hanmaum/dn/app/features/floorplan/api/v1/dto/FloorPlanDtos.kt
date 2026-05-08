package com.hanmaum.dn.app.features.floorplan.api.v1.dto

data class FloorResponse(
    val id: String,
    val floorNumber: Int,
    val name: String,
)

data class RoomResponse(
    val id: String,
    val floorId: String,
    val name: String,
    val description: String,
    val points: List<List<Double>>,
)
