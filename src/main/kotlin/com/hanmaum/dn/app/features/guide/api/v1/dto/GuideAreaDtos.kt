package com.hanmaum.dn.app.features.guide.api.v1.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import tools.jackson.databind.annotation.JsonDeserialize
import java.util.UUID

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
    @field:Size(max = 200) val label: String,
    @field:JsonDeserialize(using = GuideCountDeserializer::class)
    @param:JsonProperty(required = true)
    @field:Min(0)
    @field:Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) val displayOrder: Int,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    @param:JsonProperty(required = true) val active: Boolean,
) : GuideStrictRequest()

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
data class GuideAreaUpdateRequest(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val roomPublicId: UUID,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotBlank
    @field:Size(max = 200) val label: String,
    @field:JsonDeserialize(using = GuideCountDeserializer::class)
    @param:JsonProperty(required = true)
    @field:Min(0)
    @field:Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) val displayOrder: Int,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    @param:JsonProperty(required = true) val active: Boolean,
    @field:JsonDeserialize(using = GuideVersionDeserializer::class)
    @param:JsonProperty(required = true)
    @field:Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) val version: Long,
) : GuideStrictRequest()
