package com.hanmaum.dn.app.features.church.service

import com.hanmaum.dn.app.common.domainvalue.CheckInPresence
import com.hanmaum.dn.app.features.church.config.ChurchLocationProperties
import org.springframework.stereotype.Service
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Decides whether a device position is evidence of being at the church.
 *
 * The comparison lives on the server, next to the radius it compares against
 * (`hanmaum.church.location.*`). A client sends raw coordinates and the platform's accuracy
 * estimate and never a verdict — a client-supplied one would be a single line to forge, and
 * keeping the rule here lets the radius change without an app release.
 */
@Service
class ChurchGeofenceService(
    private val properties: ChurchLocationProperties,
) {
    /**
     * Both bounds are deliberate and conservative in both directions.
     *
     * A fix 40 m from the centre with a ±800 m error is not evidence of being present, so it
     * cannot be [CheckInPresence.IN_PLACE]. By the same logic it is not evidence of being
     * elsewhere either, so it is not [CheckInPresence.OUTSIDE]: an imprecise reading is
     * equally weak in both directions, and OUTSIDE should mean the member was actually seen
     * somewhere else. Anything whose error circle overlaps the boundary stays
     * [CheckInPresence.UNCONFIRMED].
     */
    fun evaluate(
        latitude: Double?,
        longitude: Double?,
        accuracyMeters: Double?,
    ): CheckInPresence {
        if (!properties.isConfigured()) return CheckInPresence.UNCONFIRMED
        if (latitude == null || longitude == null || accuracyMeters == null) return CheckInPresence.UNCONFIRMED
        if (!latitude.isFinite() || !longitude.isFinite() || !accuracyMeters.isFinite()) {
            return CheckInPresence.UNCONFIRMED
        }

        val radius = properties.radiusMeters!!.toDouble()
        // A platform may report 0 or a negative accuracy when it has no estimate at all.
        // Treating that as a perfect fix would turn "unknown" into IN_PLACE, so it is the
        // one case where a missing number must widen the circle rather than shrink it.
        val accuracy = if (accuracyMeters <= 0.0) return CheckInPresence.UNCONFIRMED else accuracyMeters
        val distance = distanceMeters(latitude, longitude, properties.latitude!!, properties.longitude!!)

        return when {
            distance + accuracy <= radius -> CheckInPresence.IN_PLACE
            distance - accuracy > radius -> CheckInPresence.OUTSIDE
            else -> CheckInPresence.UNCONFIRMED
        }
    }

    /**
     * Great-circle distance in metres. The haversine formula is well past accurate enough
     * at geofence scale, where the error against a proper ellipsoid model is centimetres.
     */
    private fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }

    private companion object {
        /** Mean Earth radius (IUGG). */
        const val EARTH_RADIUS_METERS = 6_371_008.8
    }
}
