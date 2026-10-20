package com.hanmaum.dn.app.features.guide.service

import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaDefinition
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideCountTotals
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailEntryRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailEntryResponse
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideRoomTotals
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideServiceTotals
import com.hanmaum.dn.app.features.guide.domain.worshipTotal
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.util.UUID

class GuideDetailValidationException(
    val fieldErrors: Map<String, String>,
) : RuntimeException("Invalid attendance details.")

data class GuideDetailCalculation(
    val rows: List<GuideDetailEntryRequest>,
    val entries: List<GuideDetailEntryResponse>,
    val rooms: List<GuideRoomTotals>,
    val services: List<GuideServiceTotals>,
    val totals: GuideCountTotals,
)

@Component
class GuideDetailsCalculator {
    fun calculate(
        entries: List<GuideDetailEntryRequest>,
        areas: List<GuideAreaDefinition>,
    ): GuideDetailCalculation {
        if (areas.isEmpty() || areas.map { it.areaPublicId }.distinct().size != areas.size) {
            throw GuideException(HttpStatus.CONFLICT, "Report requires unique counting areas; repair its configuration before editing.")
        }
        val ordered = areas.sortedWith(compareBy({ it.displayOrder }, { it.areaPublicId }))
        val allowed = areas.map { it.areaPublicId }.toSet()
        val errors = linkedMapOf<String, String>()
        val seen = mutableSetOf<Pair<UUID, Int>>()
        if (entries.size > areas.size * 3) errors["entries"] = "Too many counting-area/service rows."
        val nullableEntries: List<GuideDetailEntryRequest?> = entries
        nullableEntries.forEachIndexed { index, entry ->
            val path = "entries[$index]"
            if (entry == null) {
                errors[path] = "A row cannot be null."
                return@forEachIndexed
            }
            if (entry.areaPublicId !in allowed) errors["$path.areaPublicId"] = "Counting area does not belong to this report."
            if (entry.serviceNumber !in 1..3) errors["$path.serviceNumber"] = "Service must be 1, 2 or 3."
            if (!seen.add(entry.areaPublicId to entry.serviceNumber)) errors[path] = "Duplicate room/service row."
            if (entry.serviceNumber != 3 && entry.youthCount != null) errors["$path.youthCount"] = "Youth is allowed only for service 3."
            val counts = mapOf("adultCount" to entry.adultCount, "schoolCount" to entry.schoolCount, "youthCount" to entry.youthCount)
            counts.forEach { (field, value) ->
                if (value != null && value < 0) errors["$path.$field"] = "Count must be between 0 and 2147483647."
            }
        }
        if (errors.isNotEmpty()) throw GuideDetailValidationException(errors)
        val byKey = entries.associateBy { it.areaPublicId to it.serviceNumber }
        val rows =
            ordered.flatMap { area ->
                (1..3).map { service -> byKey[area.areaPublicId to service] ?: GuideDetailEntryRequest(area.areaPublicId, service) }
            }
        val services =
            (1..3).map { service ->
                val serviceRows = rows.filter { it.serviceNumber == service }
                // Known draft counts must not already exceed the eventual canonical int32 total.
                val known = serviceRows.sumOf { row -> listOfNotNull(row.adultCount, row.schoolCount, row.youthCount).sumOf(Int::toLong) }
                if (known > Int.MAX_VALUE) errors["services[$service].totalCount"] = "Service total must not exceed 2147483647."
                GuideServiceTotals(service, totals(serviceRows))
            }
        if (errors.isNotEmpty()) throw GuideDetailValidationException(errors)
        return GuideDetailCalculation(
            rows,
            rows.map { GuideDetailEntryResponse(it.areaPublicId, it.serviceNumber, totals(listOf(it))) },
            ordered.map { area ->
                GuideRoomTotals(
                    area.areaPublicId,
                    area.roomPublicId,
                    area.roomName,
                    area.label,
                    area.displayOrder,
                    totals(
                        rows.filter {
                            it.areaPublicId ==
                                area.areaPublicId
                        },
                    ),
                )
            },
            services,
            totals(rows),
        )
    }

    private fun totals(rows: List<GuideDetailEntryRequest>): GuideCountTotals {
        val adult = worshipTotal(rows.map { it.adultCount })
        val school = worshipTotal(rows.map { it.schoolCount })
        val youthRows = rows.filter { it.serviceNumber == 3 }
        val youth = if (youthRows.isEmpty()) null else worshipTotal(youthRows.map { it.youthCount })
        val compatible = if (youthRows.isEmpty()) adult else plus(adult, youth)
        val total = plus(compatible, school)
        return GuideCountTotals(adult, youth, compatible, school, total, total != null)
    }

    private fun plus(
        a: Long?,
        b: Long?,
    ): Long? = if (a != null && b != null) a + b else null
}
