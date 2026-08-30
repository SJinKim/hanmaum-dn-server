package com.hanmaum.dn.app.features.events.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

@ConfigurationProperties("hanmaum.rsvp")
data class RsvpProperties(
    /** Intervals before windowEnd at which members with a MAYBE response are reminded. */
    val reminderOffsets: List<Duration> = listOf(Duration.ofDays(7)),
) {
    init {
        require(reminderOffsets.all { !it.isNegative && !it.isZero }) {
            "RSVP reminder offsets must be positive durations."
        }
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RsvpProperties::class)
class RsvpConfiguration
