package com.hanmaum.dn.app.features.church.service

import com.hanmaum.dn.app.common.domainvalue.CheckInPresence
import com.hanmaum.dn.app.features.church.config.ChurchLocationProperties
import kotlin.test.Test
import kotlin.test.assertEquals

class ChurchGeofenceServiceTest {
    private val churchLatitude = 50.1281518
    private val churchLongitude = 8.5843494

    /** One degree of latitude is ~111.32 km, so this converts a distance into an offset. */
    private fun metresNorth(metres: Double) = churchLatitude + metres / 111_320.0

    private fun service(
        enabled: Boolean = true,
        radiusMeters: Int? = 100,
    ) = ChurchGeofenceService(
        ChurchLocationProperties(
            enabled = enabled,
            latitude = if (radiusMeters == null) null else churchLatitude,
            longitude = if (radiusMeters == null) null else churchLongitude,
            radiusMeters = radiusMeters,
        ),
    )

    @Test
    fun `a precise fix at the centre is IN_PLACE`() {
        val result = service().evaluate(churchLatitude, churchLongitude, accuracyMeters = 10.0)

        assertEquals(CheckInPresence.IN_PLACE, result)
    }

    @Test
    fun `a precise fix a kilometre away is OUTSIDE`() {
        val result = service().evaluate(metresNorth(1_000.0), churchLongitude, accuracyMeters = 50.0)

        assertEquals(CheckInPresence.OUTSIDE, result)
    }

    @Test
    fun `a fix at the centre with a cell-tower error is UNCONFIRMED, not IN_PLACE`() {
        // The exact case the design turns on: no WiFi in the building means an indoor fix
        // can fall back to a tower whose error is wider than the radius. Sitting in the
        // room is not something an 800 m circle can demonstrate.
        val result = service().evaluate(churchLatitude, churchLongitude, accuracyMeters = 800.0)

        assertEquals(CheckInPresence.UNCONFIRMED, result)
    }

    @Test
    fun `a fix whose error circle straddles the boundary is UNCONFIRMED, not OUTSIDE`() {
        // 150 m out with a ±100 m error: too far to confirm presence, too imprecise to
        // claim absence. OUTSIDE has to mean the member was actually seen somewhere else.
        val result = service().evaluate(metresNorth(150.0), churchLongitude, accuracyMeters = 100.0)

        assertEquals(CheckInPresence.UNCONFIRMED, result)
    }

    @Test
    fun `no position at all is UNCONFIRMED`() {
        assertEquals(CheckInPresence.UNCONFIRMED, service().evaluate(null, null, null))
    }

    @Test
    fun `a partial position is UNCONFIRMED rather than judged on what arrived`() {
        assertEquals(
            CheckInPresence.UNCONFIRMED,
            service().evaluate(churchLatitude, churchLongitude, accuracyMeters = null),
        )
    }

    @Test
    fun `a non-positive accuracy is UNCONFIRMED, never a perfect fix`() {
        // Some platforms report 0 when they have no estimate. Reading that as a flawless
        // fix would turn "unknown" into IN_PLACE for anyone standing anywhere.
        assertEquals(
            CheckInPresence.UNCONFIRMED,
            service().evaluate(churchLatitude, churchLongitude, accuracyMeters = 0.0),
        )
    }

    @Test
    fun `a deployment with the geofence switched off records UNCONFIRMED`() {
        val result = service(enabled = false).evaluate(churchLatitude, churchLongitude, 10.0)

        assertEquals(CheckInPresence.UNCONFIRMED, result)
    }

    @Test
    fun `a deployment without configured coordinates records UNCONFIRMED and does not fail`() {
        val result = service(radiusMeters = null).evaluate(churchLatitude, churchLongitude, 10.0)

        assertEquals(CheckInPresence.UNCONFIRMED, result)
    }

    @Test
    fun `a non-finite coordinate is UNCONFIRMED`() {
        assertEquals(
            CheckInPresence.UNCONFIRMED,
            service().evaluate(Double.NaN, churchLongitude, 10.0),
        )
    }
}
