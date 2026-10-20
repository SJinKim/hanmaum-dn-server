package com.hanmaum.dn.app.features.guide.service

import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideStatisticChange
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideStatisticKey
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideStatisticMetric
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideStatisticNullReason
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideStatisticPeriod
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideStatisticPoint
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideStatisticsResponse
import com.hanmaum.dn.app.features.guide.domain.WorshipCountReport
import com.hanmaum.dn.app.features.guide.repository.WorshipCountRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.MathContext
import java.time.Clock
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class GuideStatisticsService(
    private val reports: WorshipCountRepository,
    private val clock: Clock,
) {
    private val precision = MathContext.DECIMAL128

    fun statistics(
        year: Int,
        asOf: LocalDate?,
    ): GuideStatisticsResponse {
        val today = LocalDate.now(clock)
        if (year !in 2..9999 || (asOf != null && asOf.year != year) || (asOf == null && year > today.year)) {
            throw GuideException(
                HttpStatus.BAD_REQUEST,
                "Choose a valid year and a cutoff within that year; future years require an explicit cutoff.",
            )
        }
        val start = LocalDate.of(year, 1, 1)
        val upper = asOf ?: minOf(today, LocalDate.of(year, 12, 31))
        // One database read gives every table/chart metric the same report snapshot.
        val rows = reports.findAllByServiceDateBetweenAndDeletedAtIsNullOrderByServiceDateAsc(start.minusYears(1), upper)
        val selected = rows.filter { it.serviceDate >= start }
        val cutoff = asOf ?: selected.lastOrNull()?.serviceDate ?: upper
        val current = period(start, cutoff, selected)
        val previousSame = period(start.minusYears(1), cutoff.minusYears(1), rows)
        val previousFull = period(start.minusYears(1), start.minusDays(1), rows)
        return GuideStatisticsResponse(
            year,
            cutoff,
            year - 1,
            clock.zone.id,
            current,
            previousSame,
            previousFull,
            changes(current, previousSame),
            changes(current, previousFull),
            selected.filter { it.serviceDate <= cutoff }.map {
                GuideStatisticPoint(it.serviceDate, it.service1Count, it.service2Count, it.service3Count, it.total(), it.total() != null)
            },
        )
    }

    private fun period(
        from: LocalDate,
        to: LocalDate,
        source: List<WorshipCountReport>,
    ): GuideStatisticPeriod {
        val rows = source.filter { it.serviceDate in from..to }
        val complete = rows.filter { it.total() != null }
        val total = complete.sumOf { it.total()!! }
        return GuideStatisticPeriod(
            from,
            to,
            rows.firstOrNull()?.serviceDate,
            rows.lastOrNull()?.serviceDate,
            complete.firstOrNull()?.serviceDate,
            complete.lastOrNull()?.serviceDate,
            rows.size,
            complete.size,
            rows.size - complete.size,
            GuideStatisticKey.entries.map { key ->
                val sum = complete.takeIf { it.isNotEmpty() }?.sumOf { value(it, key) }
                val average = sum?.toBigDecimal()?.divide(complete.size.toBigDecimal(), precision)
                val share =
                    if (sum == null ||
                        total == 0L
                    ) {
                        null
                    } else {
                        sum.toBigDecimal().multiply(BigDecimal(100)).divide(total.toBigDecimal(), precision)
                    }
                GuideStatisticMetric(
                    key,
                    sum,
                    average,
                    if (average == null) GuideStatisticNullReason.NO_COMPLETE_REPORTS else null,
                    share,
                    when {
                        sum == null -> GuideStatisticNullReason.NO_COMPLETE_REPORTS
                        total == 0L -> GuideStatisticNullReason.ZERO_TOTAL
                        else -> null
                    },
                )
            },
        )
    }

    private fun value(
        row: WorshipCountReport,
        key: GuideStatisticKey,
    ): Long =
        when (key) {
            GuideStatisticKey.SERVICE_1 -> row.service1Count!!.toLong()
            GuideStatisticKey.SERVICE_2 -> row.service2Count!!.toLong()
            GuideStatisticKey.SERVICE_3 -> row.service3Count!!.toLong()
            GuideStatisticKey.TOTAL -> row.total()!!
        }

    private fun changes(
        current: GuideStatisticPeriod,
        baseline: GuideStatisticPeriod,
    ): List<GuideStatisticChange> =
        current.metrics.zip(baseline.metrics).map { (now, before) ->
            val reason =
                when {
                    now.average == null -> GuideStatisticNullReason.NO_CURRENT_DATA
                    before.average == null -> GuideStatisticNullReason.NO_BASELINE_DATA
                    else -> null
                }
            val absolute = if (reason == null) now.average!!.subtract(before.average!!, precision) else null
            val zero = before.average?.signum() == 0
            val percent =
                if (reason != null ||
                    zero
                ) {
                    null
                } else {
                    now.average!!
                        .divide(before.average!!, precision)
                        .subtract(BigDecimal.ONE)
                        .multiply(BigDecimal(100), precision)
                }
            GuideStatisticChange(now.key, absolute, reason, percent, reason ?: if (zero) GuideStatisticNullReason.ZERO_BASELINE else null)
        }
}
