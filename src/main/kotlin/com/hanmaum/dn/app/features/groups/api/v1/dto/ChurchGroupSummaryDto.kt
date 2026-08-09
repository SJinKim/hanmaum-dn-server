package com.hanmaum.dn.app.features.groups.api.v1.dto

import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

/**
 * Lightweight church-group entry for selection lists (e.g. the member edit form's
 * "Church Group" dropdown). publicId (UUID string) is the external identifier;
 * the internal Long id is never exposed.
 */
data class ChurchGroupSummaryDto(
    val publicId: String,
    /** Optional grouping/category (구역/부서), null when ungrouped. */
    val division: String?,
    val name: String,
    /** publicId of the group's current leader, or null while the group is without one. */
    val leaderPublicId: String? = null,
    /** Display name of the current leader, or null while the group is without one. */
    val leaderName: String? = null,
    /** Day the current leader took over. Null exactly when [leaderPublicId] is null. */
    val leaderSince: LocalDate? = null,
)

/**
 * PUT /church-groups/{publicId}/leader — makes [memberPublicId] the group's current leader.
 *
 * The member must already belong to the group. Any sitting leader's tenure is closed as of
 * [startDate]. Re-sending the member who already leads the group is a no-op, so retries and
 * double-submits cannot fragment one continuous tenure into several rows.
 */
data class AssignGroupLeaderRequest(
    @field:NotBlank(message = "맴버 ID는 필수입니다.")
    val memberPublicId: String,
    /** Day the tenure begins. Defaults to today when omitted. */
    val startDate: LocalDate? = null,
)
