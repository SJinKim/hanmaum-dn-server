package com.hanmaum.dn.app.features.church.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties("hanmaum.church.location")
data class ChurchLocationProperties(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Church location latitude must be a finite value between -90 and 90."
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Church location longitude must be a finite value between -180 and 180."
        }
        require(radiusMeters > 0) { "Church location radiusMeters must be positive." }
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChurchLocationProperties::class)
class ChurchLocationConfiguration
