package com.hanmaum.dn.app.features.ministry.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.LocalTime

@Embeddable
class MinistrySchedule(
    @Column(name = "description", nullable = false, length = 200)
    var description: String,
    @Column(name = "start_time", nullable = false)
    var startTime: LocalTime,
    @Column(name = "end_time", nullable = false)
    var endTime: LocalTime,
)
