package com.hanmaum.dn.app.features.guide.service

import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailsResponse
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideHomeMetrics
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideHomePoint
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideHomeResponse
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideHomeShare
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideHomeStatus
import com.hanmaum.dn.app.features.guide.api.v1.dto.WorshipCountResponse
import com.hanmaum.dn.app.features.guide.domain.GuideReportMode
import com.hanmaum.dn.app.features.guide.domain.WorshipCountReport
import com.hanmaum.dn.app.features.guide.repository.WorshipCountRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.MathContext
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

@Service
class GuideHomeService(
    private val reports: WorshipCountRepository,
    private val details: GuideDetailsService,
    private val clock: Clock,
) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun get(): GuideHomeResponse {
        val asOf = clock.instant()
        val sunday = LocalDate.ofInstant(asOf, clock.zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val recent = reports.findAllByServiceDateLessThanEqualAndDeletedAtIsNullOrderByServiceDateDesc(sunday, PageRequest.of(0, 4))
        val selected = reports.findHomeConfirmed(sunday, PageRequest.of(0, 1)).firstOrNull()
        val complete = reports.findHomeCompleteSundays(sunday, PageRequest.of(0, 4))
        val timeline = reports.findRange(sunday.minusWeeks(7), sunday, PageRequest.of(0, 50)).content
        val displayed = (recent + listOfNotNull(selected) + complete + timeline).distinctBy { it.publicId }
        val previous = reports.findByServiceDateInAndDeletedAtIsNull(displayed.map { it.serviceDate.minusWeeks(1) })
        val rows = (displayed + previous).distinctBy { it.publicId }.sortedByDescending { it.serviceDate }
        val byDate = rows.associateBy { it.serviceDate }
        val attendance =
            selected?.let {
                if (it.reportMode == GuideReportMode.DETAILED) {
                    details.getForHome(it.publicId)
                } else {
                    GuideDetailsResponse(dto(it, byDate), emptyList(), emptyList(), emptyList(), null)
                }
            }
        val selectedTotal = selected?.total()
        val sharesReason =
            when {
                selectedTotal == null -> "NO_COMPLETE_REPORT"
                selectedTotal == 0L -> "ZERO_TOTAL"
                else -> null
            }
        val signature = sunday.toString() + rows.joinToString("|") { "${it.publicId}:${it.version}:${it.serviceDate}" }
        return GuideHomeResponse(
            asOf,
            clock.zone.id,
            sunday,
            UUID.nameUUIDFromBytes(signature.toByteArray(Charsets.UTF_8)).toString(),
            when {
                selected == null -> GuideHomeStatus.EMPTY
                selectedTotal == null -> GuideHomeStatus.PARTIAL
                else -> GuideHomeStatus.COMPLETE
            },
            selected?.serviceDate != sunday,
            attendance,
            byDate[sunday]?.let { dto(it, byDate) },
            recent.map { dto(it, byDate) },
            metrics(complete),
            (7L downTo 0L).map { offset ->
                val date = sunday.minusWeeks(offset)
                val report = byDate[date]
                GuideHomePoint(date, report?.service1Count, report?.service2Count, report?.service3Count, report?.total())
            },
            selected?.serviceDate,
            (1..3).map { service ->
                val count = selected?.let { counts(it)[service - 1] }
                GuideHomeShare(service, count, if (sharesReason == null) percent(count!!.toLong(), selectedTotal!!) else null)
            },
            sharesReason,
        )
    }

    private fun metrics(complete: List<WorshipCountReport>): GuideHomeMetrics {
        val current = complete.firstOrNull()
        val reference = complete.getOrNull(1)
        val total = current?.total()
        val baseline = reference?.total()
        val change = if (total != null && baseline != null) total - baseline else null
        val reason =
            when {
                total == null -> "NO_CURRENT_DATA"
                baseline == null -> "NO_BASELINE_DATA"
                baseline == 0L -> "ZERO_BASELINE"
                else -> null
            }
        val lastFour = complete.take(4)
        val mean =
            if (lastFour.isEmpty()) {
                null
            } else {
                lastFour
                    .fold(BigDecimal.ZERO) { sum, report -> sum + BigDecimal.valueOf(report.total()!!) }
                    .divide(BigDecimal.valueOf(lastFour.size.toLong()), MathContext.DECIMAL128)
            }
        return GuideHomeMetrics(
            current?.serviceDate,
            total,
            reference?.serviceDate,
            baseline,
            change,
            if (reason == null) percent(change!!, baseline!!) else null,
            reason,
            mean,
            lastFour.size,
            lastFour.lastOrNull()?.serviceDate,
            current?.serviceDate,
        )
    }

    private fun percent(
        value: Long,
        baseline: Long,
    ): BigDecimal = BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(baseline), MathContext.DECIMAL128)

    private fun counts(report: WorshipCountReport) = listOf(report.service1Count, report.service2Count, report.service3Count)

    private fun dto(
        report: WorshipCountReport,
        byDate: Map<LocalDate, WorshipCountReport>,
    ): WorshipCountResponse = report.toGuideResponse(byDate[report.serviceDate.minusWeeks(1)]?.total())
}
