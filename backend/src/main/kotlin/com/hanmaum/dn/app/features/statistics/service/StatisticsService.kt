package com.hanmaum.dn.app.features.statistics.service

import com.hanmaum.dn.app.features.statistics.api.v1.dto.ChartDataDto
import com.hanmaum.dn.app.features.statistics.api.v1.dto.DashboardStatsDto
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class StatisticsService(
    private val memberRepository: MemberRepository,
) {
    @Transactional(readOnly=true)
    fun getDashboardStats(): DashboardStatsDto {
        // 1 Einfache KPIs laden
        val totalMembers = memberRepository.countByDeletedAtIsNull()
        val newMembersYtd = memberRepository.countNewMembersYtd()

        // Durchschnittsalter runden
        val avgAgeRaw = memberRepository.getAverageAge()
        val avgAge = BigDecimal.valueOf(avgAgeRaw)
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

        val ageStats = rawAgeGroups.map { row ->
            ChartDataDto(
                label = row[0] as String,
                // Postgres COUNT gibt BigInteger oder Long zurück, sicherheitshalber casten
                value = (row[1] as Number).toLong()
            )
        }

        // 3. Alles zusammenpacken
        return DashboardStatsDto(
            totalMembers = totalMembers,
            newMembersYtd = newMembersYtd,
            averageAge = avgAge,
            cityDistribution = cityStats,
            ageDistribution = ageStats,
            genderDistribution = genderStats
        )
    }
}