package com.hanmaum.dn.app.features.statistics.service

import com.hanmaum.dn.app.features.attendance.repository.AttendanceDefinitionRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceLogRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceServiceDayCount
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.repository.MinistryAssignmentRepository
import com.hanmaum.dn.app.features.statistics.api.v1.dto.ChartDataDto
import com.hanmaum.dn.app.features.statistics.api.v1.dto.DashboardStatsDto
import com.hanmaum.dn.app.features.statistics.api.v1.dto.ServiceAttendanceDto
import com.hanmaum.dn.app.features.statistics.api.v1.dto.TrainingStageDto
import com.hanmaum.dn.app.features.statistics.api.v1.dto.TrendSeriesDto
import com.hanmaum.dn.app.features.training.domain.TrainingCode
import com.hanmaum.dn.app.features.training.domain.TrainingStatus
import com.hanmaum.dn.app.features.training.repository.UserTrainingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Service
class StatisticsService(
    private val memberRepository: MemberRepository,
    private val attendanceDefinitionRepository: AttendanceDefinitionRepository,
    private val attendanceLogRepository: AttendanceLogRepository,
    private val userTrainingRepository: UserTrainingRepository,
    private val ministryAssignmentRepository: MinistryAssignmentRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun getDashboardStats(period: StatisticsPeriod = StatisticsPeriod.YEAR): DashboardStatsDto {
        // 1 Einfache KPIs laden
        val totalMembers = memberRepository.countByDeletedAtIsNull()
        val newMembersYtd = memberRepository.countNewMembersYtd()

        // Durchschnittsalter runden
        val avgAgeRaw = memberRepository.getAverageAge()
        val avgAge =
            BigDecimal
                .valueOf(avgAgeRaw)
                .setScale(1, RoundingMode.HALF_UP)
                .toDouble()

        // 2. Diagramm-Daten laden

        // A. Städte: Nehmen wir die Top 5, der Rest ist uninteressant für den Chart
        val cityStats = memberRepository.getCityDistribution().take(5)

        // B. Geschlecht: Direkt aus JPQL
        val genderStats = memberRepository.getGenderDistribution()

        // C. Altersgruppen: Das Ergebnis der Native Query mappen
        // Native Query gibt List<Object[]> zurück (Array<Any> in Kotlin)
        // index[0] = label (String), index[1] = count (Number)
        val rawAgeGroups = memberRepository.getAgeGroupsNative()

        val ageStats =
            rawAgeGroups.map { row ->
                ChartDataDto(
                    label = row[0] as String,
                    // Postgres COUNT gibt BigInteger oder Long zurück, sicherheitshalber casten
                    value = (row[1] as Number).toLong(),
                )
            }

        val aggregates = aggregates(period)

        // 3. Alles zusammenpacken
        return DashboardStatsDto(
            totalMembers = totalMembers,
            newMembersYtd = newMembersYtd,
            averageAge = avgAge,
            cityDistribution = cityStats,
            ageDistribution = ageStats,
            genderDistribution = genderStats,
            period = period.param,
            growthTrend = aggregates.growthTrend,
            attendanceRate = aggregates.attendanceRate,
            serviceAttendance = aggregates.serviceAttendance,
            divisionAttendance = aggregates.divisionAttendance,
            trainingStages = aggregates.trainingStages,
            ministryHeadcount = aggregates.ministryHeadcount,
        )
    }

    private data class Aggregates(
        val growthTrend: List<TrendSeriesDto>,
        val attendanceRate: List<ChartDataDto>,
        val serviceAttendance: List<ServiceAttendanceDto>,
        val divisionAttendance: List<ChartDataDto>,
        val trainingStages: List<TrainingStageDto>,
        val ministryHeadcount: List<ChartDataDto>,
    )

    /** The charts of the 통계 screen (#229), all relative to today in the church's time zone. */
    private fun aggregates(period: StatisticsPeriod): Aggregates {
        val today = LocalDate.now(clock)
        val allBuckets = period.buckets(today)
        val buckets = allBuckets.filter { !it.from.isAfter(today) }
        val periodStart = allBuckets.first().from

        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val lastMonday = thisMonday.minusWeeks(1)

        val joinDates = memberRepository.findAllByDeletedAtIsNull().map { joinDate(it) }

        fun membersOn(day: LocalDate): Long = joinDates.count { it == null || !it.isAfter(day) }.toLong()

        val serviceDays =
            attendanceLogRepository.countByServiceAndDayBetween(minOf(periodStart, lastMonday), today)

        val growthTrend =
            listOf(
                TrendSeriesDto(today.year, buckets.map { ChartDataDto(it.label, membersOn(minOf(it.to, today))) }),
                TrendSeriesDto(
                    today.year - 1,
                    allBuckets.map { it.minusYears(1) }.map { ChartDataDto(it.label, membersOn(it.to)) },
                ),
            )

        val attendanceRate =
            buckets.map { bucket ->
                val end = minOf(bucket.to, today)
                val held = serviceDays.filter { !it.attendanceDate.isBefore(bucket.from) && !it.attendanceDate.isAfter(end) }
                ChartDataDto(bucket.label, ratePercent(held.sumOf { it.count }, membersOn(end) * held.size))
            }

        val serviceAttendance =
            attendanceDefinitionRepository.findAll(true).map { definition ->
                fun checkIns(from: LocalDate): Long =
                    serviceDays
                        .filter { it.definitionId == definition.id && inWeek(it, from) }
                        .sumOf { it.count }
                ServiceAttendanceDto(
                    definitionPublicId = definition.publicId,
                    title = definition.title,
                    windowStart = definition.windowStart,
                    thisWeek = checkIns(thisMonday),
                    lastWeek = checkIns(lastMonday),
                )
            }

        val divisionAttendance =
            attendanceLogRepository
                .countAttendeesByDivisionBetween(periodStart, today)
                .mapNotNull { row -> row.division?.let { ChartDataDto(it, row.count) } }

        val trainingStages =
            userTrainingRepository
                .countMembersByTrainingCode(TRAINING_STAGES, CURRENT_TRAINING_STATUSES)
                .map { TrainingStageDto(it.code.name, it.nameKo ?: it.name, it.count) }

        val ministryHeadcount =
            ministryAssignmentRepository
                .countCurrentMembersByMinistry()
                .map { ChartDataDto(it.ministryName, it.count) }

        return Aggregates(growthTrend, attendanceRate, serviceAttendance, divisionAttendance, trainingStages, ministryHeadcount)
    }

    /** The registration date, or the day the record was created when the form left it empty. */
    private fun joinDate(member: Member): LocalDate? = member.registrationDate ?: member.createdAt?.atZone(clock.zone)?.toLocalDate()

    private fun inWeek(
        row: AttendanceServiceDayCount,
        monday: LocalDate,
    ): Boolean = !row.attendanceDate.isBefore(monday) && row.attendanceDate.isBefore(monday.plusWeeks(1))

    private fun ratePercent(
        attended: Long,
        possible: Long,
    ): Long =
        if (possible == 0L) {
            0
        } else {
            BigDecimal(attended * 100).divide(BigDecimal(possible), 0, RoundingMode.HALF_UP).toLong()
        }

    companion object {
        /** 큐베세, 1대1 and 제자반, the three steps of the 양육 chart. */
        val TRAINING_STAGES =
            listOf(TrainingCode.QT_BASIC_SEMINAR, TrainingCode.ONE_ON_ONE, TrainingCode.YOUTH_POWER_DISCIPLESHIP)

        val CURRENT_TRAINING_STATUSES = listOf(TrainingStatus.ENROLLED, TrainingStatus.IN_PROGRESS)
    }
}
