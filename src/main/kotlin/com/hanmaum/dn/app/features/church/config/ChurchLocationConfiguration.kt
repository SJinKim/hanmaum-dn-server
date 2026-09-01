package com.hanmaum.dn.app.features.church.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties("hanmaum.church.location")
data class ChurchLocationProperties(
    /**
     * Explicit off switch. The coordinates ship as defaults in application.yml, so clearing
     * the environment variables cannot disable the geofence — the placeholder default still
     * applies. A deployment without a geofence therefore sets this to false, which is also
     * the only way the documented 503 is reachable.
     */
    val enabled: Boolean = true,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Int? = null,
) {
    init {
        val configuredValues = listOf(latitude, longitude, radiusMeters).count { it != null }
        require(configuredValues == 0 || configuredValues == 3) {
            "Church location latitude, longitude, and radiusMeters must be configured together."
        }
        latitude?.let {
            require(it.isFinite() && it in -90.0..90.0) {
                "Church location latitude must be a finite value between -90 and 90."
            }
        }
        longitude?.let {
            require(it.isFinite() && it in -180.0..180.0) {
                "Church location longitude must be a finite value between -180 and 180."
            }
        }
        radiusMeters?.let {
            require(it > 0) { "Church location radiusMeters must be positive." }
        }
    }

    fun isConfigured(): Boolean = enabled && latitude != null && longitude != null && radiusMeters != null
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChurchLocationProperties::class)
class ChurchLocationConfiguration
