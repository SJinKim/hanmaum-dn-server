package com.hanmaum.dn.app.features.guide

import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaDefinition
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailEntryRequest
import java.util.UUID

internal val guideAreas: List<GuideAreaDefinition> =
    listOf("본당1층A", "본당1층B", "본당2층C", "유아유치부").mapIndexed { i, label ->
        GuideAreaDefinition(
            UUID.fromString("e3310000-0000-4000-8000-00000000000${i + 1}"),
            UUID(0, (i + 1).toLong()),
            "fixture room",
            label,
            i,
        )
    }

internal fun guideZeroRows(areas: List<GuideAreaDefinition> = guideAreas): List<GuideDetailEntryRequest> =
    areas.flatMap { area ->
        (1..3).map { service -> GuideDetailEntryRequest(area.areaPublicId, service, 0, 0, if (service == 3) 0 else null) }
    }

internal fun guideYouthExample(): List<GuideDetailEntryRequest> =
    guideZeroRows().map {
        if (it.areaPublicId == guideAreas[0].areaPublicId &&
            it.serviceNumber == 3
        ) {
            it.copy(adultCount = 40, youthCount = 25, schoolCount = 5)
        } else {
            it
        }
    }
