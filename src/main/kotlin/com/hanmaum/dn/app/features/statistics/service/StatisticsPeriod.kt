package com.hanmaum.dn.app.features.statistics.service

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.YearMonth

/** The time window of the 통계 screen (#229), as sent in the `period` query parameter. */
enum class StatisticsPeriod(
    val param: String,
) {
    /** The last 30 days, one bucket per day. */
    LAST_30_DAYS("30d"),

    /** The current calendar quarter, one bucket per month. */
    QUARTER("quarter"),

    /** The current calendar year, one bucket per month. */
    YEAR("year"),
    ;

    /** The buckets of this period, oldest first, including those that lie in the future. */
    fun buckets(today: LocalDate): List<StatisticsBucket> =
        when (this) {
            LAST_30_DAYS ->
                (29 downTo 0).map { today.minusDays(it.toLong()) }.map { StatisticsBucket(it.toString(), it, it) }
            QUARTER -> {
                val firstMonth = (today.monthValue - 1) / 3 * 3 + 1
                (firstMonth until firstMonth + 3).map { monthBucket(YearMonth.of(today.year, it)) }
            }
            YEAR -> (1..12).map { monthBucket(YearMonth.of(today.year, it)) }
        }

    private fun monthBucket(month: YearMonth) = StatisticsBucket(month.toString(), month.atDay(1), month.atEndOfMonth())

    companion object {
        const val DEFAULT = "year"

        fun fromParam(value: String): StatisticsPeriod =
            entries.firstOrNull { it.param == value }
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unknown period '$value', expected one of ${entries.joinToString { it.param }}",
                )
    }
}

/** One point on the x-axis: a day or a month, both ends inclusive. */
data class StatisticsBucket(
    val label: String,
    val from: LocalDate,
    val to: LocalDate,
) {
    /** The same day or month [years] earlier, for the previous-year line of the 성장 추이 chart. */
    fun minusYears(years: Long): StatisticsBucket =
        if (from == to) {
            val day = from.minusYears(years)
            StatisticsBucket(day.toString(), day, day)
        } else {
            val month = YearMonth.from(from).minusYears(years)
            StatisticsBucket(month.toString(), month.atDay(1), month.atEndOfMonth())
        }
}
