package com.hanmaum.dn.app.features.newcomers.api.v1.dto

import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerLifecycle
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerVisitSource
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerVisitType
import dev.zacsweers.redacted.annotations.Redacted
import dev.zacsweers.redacted.annotations.Unredacted
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Redacted
data class CreateNewcomerVisitRequest(
    /** Defaults to today in the church's time zone. */
    @Unredacted val visitDate: LocalDate? = null,
    @field:NotBlank @field:Size(max = 50) val lastName: String,
    @field:NotBlank @field:Size(max = 50) val firstName: String,
    val gender: Gender? = null,
    @field:Min(1900) @field:Max(2100) val birthYear: Int? = null,
    @Unredacted val visitType: NewcomerVisitType = NewcomerVisitType.FIRST,
    @Unredacted val source: NewcomerVisitSource? = null,
    @field:Size(max = 1000) val note: String? = null,
)

/** PATCH semantics: a null field stays as it is. */
@Redacted
data class UpdateNewcomerVisitRequest(
    @Unredacted val visitDate: LocalDate? = null,
    @field:Size(min = 1, max = 50) val lastName: String? = null,
    @field:Size(min = 1, max = 50) val firstName: String? = null,
    val gender: Gender? = null,
    @field:Min(1900) @field:Max(2100) val birthYear: Int? = null,
    @Unredacted val visitType: NewcomerVisitType? = null,
    @Unredacted val source: NewcomerVisitSource? = null,
    @field:Size(max = 1000) val note: String? = null,
)

/** Links the visit to a 새가족 profile; a null [newcomerPublicId] removes the link. */
data class LinkNewcomerVisitRequest(
    val newcomerPublicId: UUID? = null,
)

@Redacted
data class NewcomerVisitResponse(
    @Unredacted val publicId: String,
    @Unredacted val visitDate: LocalDate,
    val lastName: String,
    val firstName: String,
    val fullName: String,
    val gender: Gender?,
    val birthYear: Int?,
    @Unredacted val visitType: NewcomerVisitType,
    @Unredacted val source: NewcomerVisitSource?,
    val note: String?,
    @Unredacted val newcomerPublicId: String?,
    @Unredacted val newcomerLifecycle: NewcomerLifecycle?,
    @Unredacted val createdAt: Instant,
)

data class NewcomerVisitSourceCount(
    val source: NewcomerVisitSource?,
    val count: Int,
)

data class NewcomerVisitDayCount(
    val date: LocalDate,
    val count: Int,
)

/**
 * The 새가족 funnel over a date range: 방문 → 새가족 등록 (linked profile) → 등반 (profile GRADUATED),
 * plus the 알게 된 경로 split for the 광고 evaluation.
 */
data class NewcomerVisitStatsResponse(
    val from: LocalDate,
    val to: LocalDate,
    val visits: Int,
    val firstVisits: Int,
    val revisits: Int,
    val registered: Int,
    val graduated: Int,
    val bySource: List<NewcomerVisitSourceCount>,
    val byDay: List<NewcomerVisitDayCount>,
)
