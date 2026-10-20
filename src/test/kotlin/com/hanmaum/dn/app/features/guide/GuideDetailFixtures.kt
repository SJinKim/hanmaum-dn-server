package com.hanmaum.dn.app.features.guide

import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailEntryRequest
import com.hanmaum.dn.app.features.guide.domain.GuideRoom

internal fun guideZeroRows(): List<GuideDetailEntryRequest> =
    GuideRoom.entries.flatMap { room ->
        (1..3).map { service -> GuideDetailEntryRequest(room.name, service, 0, 0, if (service == 3) 0 else null) }
    }

internal fun guideYouthExample(): List<GuideDetailEntryRequest> =
    guideZeroRows().map {
        if (it.roomCode == "MAIN_1F_A" && it.serviceNumber == 3) it.copy(adultCount = 40, youthCount = 25, schoolCount = 5) else it
    }
