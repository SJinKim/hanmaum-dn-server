package com.hanmaum.dn.app.features.attendance.api

import com.hanmaum.dn.app.features.attendance.api.v1.dto.DefinitionDto
import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition

fun AttendanceDefinition.toDto(): DefinitionDto =
    DefinitionDto(
        publicId = this.publicId.toString(),
        title = this.title,
        dayOfWeek = this.dayOfWeek,
        windowStart = this.windowStart,
        windowEnd = this.windowEnd,
        isActive = this.isActive,
    )
