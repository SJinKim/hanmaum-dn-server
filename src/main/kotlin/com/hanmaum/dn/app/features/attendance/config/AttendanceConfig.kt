package com.hanmaum.dn.app.features.attendance.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration
class AttendanceConfig(
    @Value("\${app.attendance.zone-id:Europe/Berlin}") private val zoneId: String,
) {
    @Bean
    fun attendanceClock(): Clock = Clock.system(ZoneId.of(zoneId))
}
