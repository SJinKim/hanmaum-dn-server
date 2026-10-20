package com.hanmaum.dn.app.features.guide.service

import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaDefinition
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailEntryRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailsCreateRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailsResponse
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailsUpdateRequest
import com.hanmaum.dn.app.features.guide.domain.GuideReportArea
import com.hanmaum.dn.app.features.guide.domain.GuideReportMode
import com.hanmaum.dn.app.features.guide.domain.GuideSummaryConversion
import com.hanmaum.dn.app.features.guide.domain.GuideWorshipDetail
import com.hanmaum.dn.app.features.guide.domain.WorshipCountReport
import com.hanmaum.dn.app.features.guide.repository.GuideCountingAreaRepository
import com.hanmaum.dn.app.features.guide.repository.GuideReportAreaRepository
import com.hanmaum.dn.app.features.guide.repository.GuideSummaryConversionRepository
import com.hanmaum.dn.app.features.guide.repository.GuideWorshipDetailRepository
import com.hanmaum.dn.app.features.guide.repository.WorshipCountRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
@Transactional(readOnly = true)
class GuideDetailsService(
    private val reports: WorshipCountRepository,
    private val details: GuideWorshipDetailRepository,
    private val conversions: GuideSummaryConversionRepository,
    private val calculator: GuideDetailsCalculator,
    private val summaries: WorshipCountService,
    private val configuredAreas: GuideCountingAreaRepository,
    private val reportAreas: GuideReportAreaRepository,
) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun get(publicId: UUID): GuideDetailsResponse {
        val report = reports.findByPublicIdAndDeletedAtIsNull(publicId) ?: missing()
        if (report.reportMode == GuideReportMode.SUMMARY_ONLY) {
            return GuideDetailsResponse(summaries.get(publicId), emptyList(), emptyList(), emptyList(), null)
        }
        val rows = details.findAllByReportPublicId(publicId)
        val areas = reportAreas.findAllByReportPublicId(publicId)
        if (areas.isEmpty() ||
            rows.size != areas.size * 3
        ) {
            conflict("Stored Guide detail matrix is incomplete; repair is required before editing.")
        }
        val calculated =
            calculator.calculate(
                rows.map {
                    GuideDetailEntryRequest(it.reportArea.area.publicId, it.serviceNumber, it.adultCount, it.schoolCount, it.youthCount)
                },
                areas.map(::definition),
            )
        return response(report, calculated)
    }

    @Transactional
    fun create(
        request: GuideDetailsCreateRequest,
        actor: String,
    ): GuideDetailsResponse {
        validateDate(request.serviceDate)
        available(request.serviceDate, null)
        val report = WorshipCountReport(request.serviceDate, null, null, null, request.notes, actor)
        val areas = currentAreas(report)
        val calculated = calculator.calculate(request.entries, areas.map(::definition))
        report.reportMode = GuideReportMode.DETAILED
        applyCounts(report, calculated)
        reports.saveAndFlush(report)
        reportAreas.saveAllAndFlush(areas)
        replaceMatrix(report, calculated, areas)
        return response(report, calculated)
    }

    @Transactional
    fun update(
        publicId: UUID,
        request: GuideDetailsUpdateRequest,
        actor: String,
    ): GuideDetailsResponse {
        validateDate(request.serviceDate)
        if (request.version < 0) invalid("version", "Expected version must be nonnegative.")
        val report = reports.lockActive(publicId) ?: missing()
        if (request.version != report.version) conflict("Report changed; reload before saving.")
        available(request.serviceDate, publicId)
        val converting = report.reportMode == GuideReportMode.SUMMARY_ONLY
        val areas = if (converting) currentAreas(report) else reportAreas.findAllByReportPublicId(publicId)
        val calculated = calculator.calculate(request.entries, areas.map(::definition))
        if (converting) {
            val confirmation = request.expectedSummary ?: conflict("Explicit summary comparison is required to convert this report.")
            if (listOf(confirmation.service1Count, confirmation.service2Count, confirmation.service3Count) !=
                listOf(report.service1Count, report.service2Count, report.service3Count)
            ) {
                conflict("The confirmed summary no longer matches this report.")
            }
            conversions.save(
                GuideSummaryConversion(
                    report,
                    report.version,
                    report.serviceDate,
                    report.service1Count,
                    report.service2Count,
                    report.service3Count,
                    report.notes,
                    actor,
                ),
            )
        } else if (request.expectedSummary != null) {
            invalid("expectedSummary", "This report is already detailed; summary conversion is not applicable.")
        }
        report.reportMode = GuideReportMode.DETAILED
        report.serviceDate = request.serviceDate
        report.notes = request.notes
        report.updatedBy = actor
        report.detailRevision++
        applyCounts(report, calculated)
        if (converting) reportAreas.saveAllAndFlush(areas)
        replaceMatrix(report, calculated, areas)
        reports.saveAndFlush(report)
        return response(report, calculated)
    }

    private fun replaceMatrix(
        report: WorshipCountReport,
        calculated: GuideDetailCalculation,
        areas: List<GuideReportArea>,
    ) {
        val byId = areas.associateBy { it.area.publicId }
        details.deleteMatrix(report.publicId)
        details.saveAllAndFlush(
            calculated.rows.map {
                GuideWorshipDetail(report, byId.getValue(it.areaPublicId), it.serviceNumber, it.adultCount, it.schoolCount, it.youthCount)
            },
        )
    }

    private fun currentAreas(report: WorshipCountReport): List<GuideReportArea> {
        val areas = configuredAreas.lockUsable()
        if (areas.isEmpty()) conflict("At least one active counting area on an active room/floor is required.")
        return areas.map { GuideReportArea(report, it, it.room, it.room.name, it.label, it.displayOrder) }
    }

    private fun definition(area: GuideReportArea) =
        GuideAreaDefinition(area.area.publicId, area.room.publicId, area.roomName, area.label, area.displayOrder)

    private fun applyCounts(
        report: WorshipCountReport,
        calculated: GuideDetailCalculation,
    ) {
        report.service1Count =
            calculated.services[0]
                .counts.totalCount
                ?.toInt()
        report.service2Count =
            calculated.services[1]
                .counts.totalCount
                ?.toInt()
        report.service3Count =
            calculated.services[2]
                .counts.totalCount
                ?.toInt()
    }

    private fun response(
        report: WorshipCountReport,
        calculated: GuideDetailCalculation,
    ) = GuideDetailsResponse(
        summaries.get(report.publicId),
        calculated.entries,
        calculated.rooms,
        calculated.services,
        calculated.totals,
    )

    private fun available(
        date: LocalDate,
        publicId: UUID?,
    ) {
        val existing = reports.findByServiceDateAndDeletedAtIsNull(date)
        if (existing != null && existing.publicId != publicId) conflict("An active report already exists for this date.")
    }

    private fun validateDate(date: LocalDate) {
        if (date.year !in 1..9999) invalid("serviceDate", "Year must be between 1 and 9999.")
    }

    private fun invalid(
        field: String,
        message: String,
    ): Nothing = throw GuideDetailValidationException(mapOf(field to message))

    private fun conflict(message: String): Nothing = throw GuideException(HttpStatus.CONFLICT, message)

    private fun missing(): Nothing = throw GuideException(HttpStatus.NOT_FOUND, "Report not found.")
}
