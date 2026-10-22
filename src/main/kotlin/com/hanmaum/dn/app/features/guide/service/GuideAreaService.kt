package com.hanmaum.dn.app.features.guide.service

import com.hanmaum.dn.app.features.guide.api.v1.dto.GUIDE_AREA_LABEL_MAX
import com.hanmaum.dn.app.features.guide.api.v1.dto.GUIDE_AREA_ORDER_MIN
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaCreateRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaOrderRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaResponse
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaUpdateRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideEligibleRoomResponse
import com.hanmaum.dn.app.features.guide.domain.GuideCountingArea
import com.hanmaum.dn.app.features.guide.repository.GuideCountingAreaRepository
import com.hanmaum.dn.app.features.guide.repository.GuideRoomLookupRepository
import jakarta.persistence.EntityManager
import org.hibernate.Session
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class GuideAreaService(
    private val areas: GuideCountingAreaRepository,
    private val rooms: GuideRoomLookupRepository,
    private val entityManager: EntityManager,
) {
    fun list(): List<GuideAreaResponse> = areas.findConfigured().map(::response)

    fun rooms(): List<GuideEligibleRoomResponse> =
        rooms.findEligible().map { GuideEligibleRoomResponse(it.publicId, it.name, it.floor.publicId, it.floor.name) }

    @Transactional
    fun create(request: GuideAreaCreateRequest): GuideAreaResponse {
        validate(request.label, request.displayOrder)
        lockConfiguration()
        val configured = areas.findConfigured()
        val room = rooms.lockEligible(request.roomPublicId) ?: invalidRoom()
        val added = areas.save(GuideCountingArea(room, request.label.trim(), configured.size, request.active))
        normalize(configured + added)
        return response(added)
    }

    @Transactional
    fun update(
        publicId: UUID,
        request: GuideAreaUpdateRequest,
    ): GuideAreaResponse {
        validate(request.label, request.displayOrder)
        lockConfiguration()
        val configured = areas.findConfigured().toMutableList()
        val area = configured.find { it.publicId == publicId } ?: missingArea()
        if (request.version != area.version) throw GuideException(HttpStatus.CONFLICT, "Counting area changed; reload before saving.")
        val room =
            if (!request.active && request.roomPublicId == area.room.publicId) {
                area.room
            } else {
                rooms.lockEligible(request.roomPublicId) ?: invalidRoom()
            }
        area.room = room
        area.label = request.label.trim()
        area.active = request.active
        request.displayOrder?.let { position ->
            configured.remove(area)
            configured.add(position.coerceAtMost(configured.size), area)
        }
        normalize(configured)
        return response(area)
    }

    /** Serialize membership changes as well as ordering, even when the configuration is empty.
     * A row lock alone cannot protect against a concurrent append to an empty/list snapshot. */
    private fun lockConfiguration() {
        entityManager.unwrap(Session::class.java).doWork { connection ->
            connection.createStatement().use { it.execute("select pg_advisory_xact_lock(260, 355)") }
        }
    }

    private fun normalize(configured: List<GuideCountingArea>) {
        configured.forEachIndexed { index, area -> area.displayOrder = index }
        areas.flush()
    }

    @Transactional
    fun reorder(request: GuideAreaOrderRequest): List<GuideAreaResponse> {
        if (request.areas
                .map { it.areaPublicId }
                .distinct()
                .size != request.areas.size
        ) {
            throw GuideDetailValidationException(mapOf("areas" to "Each area must appear exactly once."))
        }
        lockConfiguration()
        val configured = areas.findConfigured()
        val expected = request.areas.associate { it.areaPublicId to it.version }
        if (configured.associate { it.publicId to it.version } != expected) {
            throw GuideException(HttpStatus.CONFLICT, "Counting areas changed; reload before sorting.")
        }
        val byId = configured.associateBy { it.publicId }
        val ordered = request.areas.map { byId.getValue(it.areaPublicId) }
        normalize(ordered)
        return ordered.map(::response)
    }

    @Transactional
    fun remove(
        publicId: UUID,
        version: Long,
    ): List<GuideAreaResponse> {
        if (version < 0) throw GuideDetailValidationException(mapOf("version" to "Version must be nonnegative."))
        lockConfiguration()
        val configured = areas.findConfigured()
        val area = configured.find { it.publicId == publicId } ?: missingArea()
        if (area.version != version) throw GuideException(HttpStatus.CONFLICT, "Counting area changed; reload before removing.")
        if (area.active) throw GuideException(HttpStatus.CONFLICT, "Deactivate the counting area before removing it.")
        area.deletedAt = Instant.now()
        val remaining = configured.filter { it.publicId != publicId }
        normalize(remaining)
        return remaining.map(::response)
    }

    private fun missingArea(): Nothing = throw GuideException(HttpStatus.NOT_FOUND, "Counting area not found.")

    private fun validate(
        label: String,
        order: Int?,
    ) {
        val errors = linkedMapOf<String, String>()
        if (label.isBlank() ||
            label.length > GUIDE_AREA_LABEL_MAX
        ) {
            errors["label"] = "Label must contain 1 to $GUIDE_AREA_LABEL_MAX characters."
        }
        if (order != null && order < GUIDE_AREA_ORDER_MIN) errors["displayOrder"] = "Display order must be nonnegative."
        if (errors.isNotEmpty()) throw GuideDetailValidationException(errors)
    }

    private fun invalidRoom(): Nothing =
        throw GuideDetailValidationException(mapOf("roomPublicId" to "Select an existing active room on an active floor."))

    private fun response(area: GuideCountingArea) =
        GuideAreaResponse(area.publicId, area.room.publicId, area.room.name, area.label, area.displayOrder, area.active, area.version)
}
