package com.hanmaum.dn.app.features.guide.service

import com.hanmaum.dn.app.features.guide.api.v1.dto.WorshipCountRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.WorshipCountResponse
import com.hanmaum.dn.app.features.guide.domain.GuideReportMode
import com.hanmaum.dn.app.features.guide.domain.WorshipCountReport
import com.hanmaum.dn.app.features.guide.repository.WorshipCountRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class GuideException(
    val status: HttpStatus,
    val safeDetail: String,
) : RuntimeException(safeDetail)

@Service
@Transactional(readOnly = true)
class WorshipCountService(
    private val reports: WorshipCountRepository,
) {
    fun list(
        from: LocalDate?,
        to: LocalDate?,
        page: Int,
        size: Int,
        sort: String,
    ): Page<WorshipCountResponse> {
        if (page < 0 || size !in 1..200 || (from != null && to != null && from > to)) bad("Invalid date range or pagination.")
        val parts = sort.split(',')
        if (parts.size != 2 ||
            parts[0] != "serviceDate" ||
            parts[1] !in setOf("asc", "desc")
        ) {
            bad("Sort must be serviceDate,asc or serviceDate,desc.")
        }
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(parts[1]), "serviceDate"))
        val result = reports.findRange(from ?: LocalDate.of(1, 1, 1), to ?: LocalDate.of(9999, 12, 31), pageable)
        val dates = result.content.map { it.serviceDate.minusWeeks(1) }.toSet()
        val previousByDate =
            if (dates.isEmpty()) emptyMap() else reports.findByServiceDateInAndDeletedAtIsNull(dates).associateBy { it.serviceDate }
        return result.map { dto(it, previousByDate[it.serviceDate.minusWeeks(1)]?.total()) }
    }

    fun get(publicId: UUID): WorshipCountResponse = dto(active(publicId))

    @Transactional
    fun create(
        request: WorshipCountRequest,
        actor: String,
    ): WorshipCountResponse {
        validate(request)
        if (request.version != null) bad("New reports must not supply a version.")
        available(request.serviceDate, null)
        return dto(
            reports.saveAndFlush(
                WorshipCountReport(
                    request.serviceDate,
                    request.service1Count,
                    request.service2Count,
                    request.service3Count,
                    request.notes,
                    actor,
                ),
            ),
        )
    }

    @Transactional
    fun update(
        publicId: UUID,
        request: WorshipCountRequest,
        actor: String,
    ): WorshipCountResponse {
        validate(request)
        val entity = locked(publicId)
        if (request.version == null) bad("Expected version is required.")
        if (entity.reportMode != GuideReportMode.SUMMARY_ONLY) conflict("Detail reports must be edited through the detail endpoint.")
        if (request.version != entity.version) conflict("Report changed; reload before saving.")
        available(request.serviceDate, publicId)
        entity.serviceDate = request.serviceDate
        entity.service1Count = request.service1Count
        entity.service2Count = request.service2Count
        entity.service3Count = request.service3Count
        entity.notes = request.notes
        entity.updatedBy = actor
        return dto(reports.saveAndFlush(entity))
    }

    @Transactional
    fun delete(
        publicId: UUID,
        version: Long,
        actor: String,
    ) {
        val entity = locked(publicId)
        if (version != entity.version) conflict("Report changed; reload before deleting.")
        entity.deletedAt = Instant.now()
        entity.updatedBy = actor
        reports.saveAndFlush(entity)
    }

    private fun active(id: UUID): WorshipCountReport = reports.findByPublicIdAndDeletedAtIsNull(id) ?: missing()

    private fun locked(id: UUID): WorshipCountReport = reports.lockActive(id) ?: missing()

    private fun available(
        date: LocalDate,
        id: UUID?,
    ) {
        val existing = reports.findByServiceDateAndDeletedAtIsNull(date)
        if (existing != null && existing.publicId != id) conflict("An active report already exists for this date.")
    }

    private fun validate(request: WorshipCountRequest) {
        if (listOfNotNull(request.service1Count, request.service2Count, request.service3Count).any {
                it < 0
            }
        ) {
            bad("Counts must be nonnegative integers.")
        }
        if (request.serviceDate.year !in 1..9999) bad("Invalid report year.")
    }

    private fun dto(
        r: WorshipCountReport,
        previous: Long? = reports.findByServiceDateAndDeletedAtIsNull(r.serviceDate.minusWeeks(1))?.total(),
    ): WorshipCountResponse = r.toGuideResponse(previous)

    private fun bad(message: String): Nothing = throw GuideException(HttpStatus.BAD_REQUEST, message)

    private fun conflict(message: String): Nothing = throw GuideException(HttpStatus.CONFLICT, message)

    private fun missing(): Nothing = throw GuideException(HttpStatus.NOT_FOUND, "Report not found.")
}

internal fun WorshipCountReport.toGuideResponse(previous: Long?): WorshipCountResponse {
    val total = this.total()
    return WorshipCountResponse(
        this.publicId,
        this.serviceDate,
        this.service1Count,
        this.service2Count,
        this.service3Count,
        total,
        if (total != null && previous != null) total - previous else null,
        this.notes,
        this.version,
        reportMode = this.reportMode.name,
        complete = total != null,
    )
}
