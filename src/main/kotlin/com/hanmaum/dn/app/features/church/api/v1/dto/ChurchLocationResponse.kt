package com.hanmaum.dn.app.features.church.api.v1.dto

/**
 * Server-controlled geofence around the church building.
 *
 * @property latitude Geofence center latitude in decimal degrees.
 * @property longitude Geofence center longitude in decimal degrees.
 * @property radiusMeters Geofence radius in whole meters.
 */
data class ChurchLocationResponse(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
)
