package com.hanmaum.dn.app.features.guide.service

import com.hanmaum.dn.app.features.guide.api.v1.dto.GUIDE_AREA_LABEL_MAX
import com.hanmaum.dn.app.features.guide.api.v1.dto.GUIDE_AREA_ORDER_MIN
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaCreateRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaResponse
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaUpdateRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideEligibleRoomResponse
import com.hanmaum.dn.app.features.guide.domain.GuideCountingArea
import com.hanmaum.dn.app.features.guide.repository.GuideCountingAreaRepository
import com.hanmaum.dn.app.features.guide.repository.GuideRoomLookupRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class GuideAreaService(
    private val areas: GuideCountingAreaRepository,
    private val rooms: GuideRoomLookupRepository,
) {
    fun list(): List<GuideAreaResponse> = areas.findConfigured().map(::response)

    fun rooms(): List<GuideEligibleRoomResponse> =
        rooms.findEligible().map { GuideEligibleRoomResponse(it.publicId, it.name, it.floor.publicId, it.floor.name) }

    @Transactional
    fun create(request: GuideAreaCreateRequest): GuideAreaResponse {
        validate(request.label, request.displayOrder)
        val room = rooms.lockEligible(request.roomPublicId) ?: invalidRoom()
        return response(areas.saveAndFlush(GuideCountingArea(room, request.label.trim(), request.displayOrder, request.active)))
    }

    @Transactional
    fun update(
        publicId: UUID,
        request: GuideAreaUpdateRequest,
    ): GuideAreaResponse {
        validate(request.label, request.displayOrder)
        val area = areas.lockActive(publicId) ?: throw GuideException(HttpStatus.NOT_FOUND, "Counting area not found.")
        if (request.version != area.version) throw GuideException(HttpStatus.CONFLICT, "Counting area changed; reload before saving.")
        val room =
            if (!request.active && request.roomPublicId == area.room.publicId) {
                area.room
            } else {
                rooms.lockEligible(request.roomPublicId) ?: invalidRoom()
            }
        area.room = room
        area.label = request.label.trim()
        area.displayOrder = request.displayOrder
        area.active = request.active
        return response(areas.saveAndFlush(area))
    }

    private fun validate(
        label: String,
        order: Int,
    ) {
        val errors = linkedMapOf<String, String>()
        if (label.isBlank() ||
            label.length > GUIDE_AREA_LABEL_MAX
        ) {
            errors["label"] = "Label must contain 1 to $GUIDE_AREA_LABEL_MAX characters."
        }
        if (order < GUIDE_AREA_ORDER_MIN) errors["displayOrder"] = "Display order must be nonnegative."
        if (errors.isNotEmpty()) throw GuideDetailValidationException(errors)
    }

    private fun invalidRoom(): Nothing =
        throw GuideDetailValidationException(mapOf("roomPublicId" to "Select an existing active room on an active floor."))

    private fun response(area: GuideCountingArea) =
        GuideAreaResponse(area.publicId, area.room.publicId, area.room.name, area.label, area.displayOrder, area.active, area.version)
}
