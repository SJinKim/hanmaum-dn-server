package com.hanmaum.dn.app.features.guide.api.v1.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import tools.jackson.databind.annotation.JsonDeserialize
import java.util.UUID

/** Shared by bean validation, generated schema and internal service guards. */
const val GUIDE_AREA_LABEL_MAX = 200
const val GUIDE_AREA_ORDER_MIN = 0L

data class GuideAreaDefinition(
    val areaPublicId: UUID,
    val roomPublicId: UUID,
    val roomName: String,
    val label: String,
    val displayOrder: Int,
)

data class GuideAreaResponse(
    val areaPublicId: UUID,
    val roomPublicId: UUID,
    val roomName: String,
    val label: String,
    val displayOrder: Int,
    val active: Boolean,
    val version: Long,
)

data class GuideEligibleRoomResponse(
    val publicId: UUID,
    val name: String,
    val floorPublicId: UUID,
    val floorName: String,
)

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
data class GuideAreaCreateRequest(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val roomPublicId: UUID,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotBlank
    @field:Size(max = GUIDE_AREA_LABEL_MAX) val label: String,
    @field:JsonDeserialize(using = GuideCountDeserializer::class)
    @field:Min(GUIDE_AREA_ORDER_MIN)
    @field:Schema(minimum = "0", description = "Legacy position hint; creation always appends.")
    val displayOrder: Int? = null,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    @param:JsonProperty(required = true) val active: Boolean,
) : GuideStrictRequest()

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
data class GuideAreaUpdateRequest(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val roomPublicId: UUID,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotBlank
    @field:Size(max = GUIDE_AREA_LABEL_MAX) val label: String,
    @field:JsonDeserialize(using = GuideCountDeserializer::class)
    @field:Min(GUIDE_AREA_ORDER_MIN)
    @field:Schema(minimum = "0", description = "Legacy position hint; omit to preserve the current position.")
    val displayOrder: Int? = null,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    @param:JsonProperty(required = true) val active: Boolean,
    @field:JsonDeserialize(using = GuideVersionDeserializer::class)
    @param:JsonProperty(required = true)
    @field:Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) val version: Long,
) : GuideStrictRequest()

/** Complete ordered configuration snapshot; a changed member or version rejects the whole operation. */
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
data class GuideAreaOrderRequest(
    @field:Valid
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val areas: List<GuideAreaOrderItem>,
) : GuideStrictRequest()

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
data class GuideAreaOrderItem(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val areaPublicId: UUID,
    @field:JsonDeserialize(using = GuideVersionDeserializer::class)
    @param:JsonProperty(required = true)
    @field:Min(0)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val version: Long,
) : GuideStrictRequest()
