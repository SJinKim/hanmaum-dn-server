package com.hanmaum.dn.app.features.events.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Duration

class RsvpPropertiesTest {
    @Test
    fun `defaults to one reminder seven days before window end`() {
        assertEquals(listOf(Duration.ofDays(7)), RsvpProperties().reminderOffsets)
    }

    @Test
    fun `deployment environment variable binds multiple reminder offsets`() {
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withUserConfiguration(RsvpConfiguration::class.java)
            .withSystemProperties("HANMAUM_RSVP_REMINDEROFFSETS=P14D,P7D,P2D")
            .run { context ->
                assertEquals(
                    listOf(Duration.ofDays(14), Duration.ofDays(7), Duration.ofDays(2)),
                    context.getBean(RsvpProperties::class.java).reminderOffsets,
                )
            }
    }
}
