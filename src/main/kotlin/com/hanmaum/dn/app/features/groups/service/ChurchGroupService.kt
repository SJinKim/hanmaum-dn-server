package com.hanmaum.dn.app.features.groups.service

import com.hanmaum.dn.app.features.groups.api.v1.dto.ChurchGroupSummaryDto
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChurchGroupService(
    private val churchGroupRepository: ChurchGroupRepository,
) {
    /** All non-deleted groups, ordered by division then name — used to populate selection lists. */
    @Transactional(readOnly = true)
    fun getGroups(): List<ChurchGroupSummaryDto> =
        churchGroupRepository
            .findAllByDeletedAtIsNullOrderByDivisionAscNameAsc()
            .map {
                ChurchGroupSummaryDto(
                    publicId = it.publicId.toString(),
                    division = it.division,
                    name = it.name,
                )
            }
}
