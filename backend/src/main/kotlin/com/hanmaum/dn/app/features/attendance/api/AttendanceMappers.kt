package com.hanmaum.dn.app.features.attendance.api

import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceLogDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceStatsDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.DefinitionDto
import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.domain.AttendanceLog

fun AttendanceDefinition.toDto(): DefinitionDto =
    DefinitionDto(
        publicId = this.publicId.toString(),
        title = this.title,
        dayOfWeek = this.dayOfWeek,
        windowStart = this.windowStart,
        windowEnd = this.windowEnd,
        isActive = this.isActive,
    )

fun AttendanceLog.toDto(): AttendanceLogDto =
    AttendanceLogDto(
        publicId = this.publicId.toString(),
        definitionPublicId = this.definition.publicId.toString(),
        definitionTitle = this.definition.title,
        memberPublicId = this.member.publicId.toString(),
        memberName = "${this.member.lastName}${this.member.firstName}",
        attendanceDate = this.attendanceDate,
        attended = this.attended,
    )

fun List<AttendanceLog>.toStatsDto(): List<AttendanceStatsDto> =
    this
        .groupBy { it.member }
        .map { (member, logs) ->
            AttendanceStatsDto(
                memberPublicId = member.publicId.toString(),
                memberName = "${member.lastName}${member.firstName}",
                attendanceCount = logs.count { it.attended },
            )
        }.sortedByDescending { it.attendanceCount }
