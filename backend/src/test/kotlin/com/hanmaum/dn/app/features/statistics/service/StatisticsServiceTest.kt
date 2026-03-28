package com.hanmaum.dn.app.features.statistics.service

import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.statistics.api.v1.dto.ChartDataDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigInteger

@ExtendWith(MockitoExtension::class)
class StatisticsServiceTest {
    @Mock
    private lateinit var memberRepository: MemberRepository

    @InjectMocks
    private lateinit var statisticsService: StatisticsService

    private fun setupMocks(
        total: Long = 0L,
        newYtd: Long = 0L,
        avgAge: Double = 0.0,
        cities: List<ChartDataDto> = emptyList(),
        genders: List<ChartDataDto> = emptyList(),
        ageGroups: List<Array<Any>> = emptyList(),
    ) {
        `when`(memberRepository.countByDeletedAtIsNull()).thenReturn(total)
        `when`(memberRepository.countNewMembersYtd()).thenReturn(newYtd)
        `when`(memberRepository.getAverageAge()).thenReturn(avgAge)
        `when`(memberRepository.getCityDistribution()).thenReturn(cities)
        `when`(memberRepository.getGenderDistribution()).thenReturn(genders)
        `when`(memberRepository.getAgeGroupsNative()).thenReturn(ageGroups)
    }

    @Test
    fun `getDashboardStats returns totalMembers and newMembersYtd`() {
        setupMocks(total = 42L, newYtd = 5L)

        val stats = statisticsService.getDashboardStats()

        assertEquals(42L, stats.totalMembers)
        assertEquals(5L, stats.newMembersYtd)
    }

    @Test
    fun `getDashboardStats rounds averageAge half up`() {
        setupMocks(avgAge = 27.45)

        val stats = statisticsService.getDashboardStats()

        assertEquals(27.5, stats.averageAge)
    }

    @Test
    fun `getDashboardStats rounds averageAge down when below half`() {
        setupMocks(avgAge = 27.44)

        val stats = statisticsService.getDashboardStats()

        assertEquals(27.4, stats.averageAge)
    }

    @Test
    fun `getDashboardStats maps age group rows to ChartDataDto`() {
        setupMocks(
            ageGroups =
                listOf(
                    arrayOf<Any>("20대", 10L),
                    arrayOf<Any>("30대", 7L),
                ),
        )

        val stats = statisticsService.getDashboardStats()

        assertEquals(2, stats.ageDistribution.size)
        assertEquals("20대", stats.ageDistribution[0].label)
        assertEquals(10L, stats.ageDistribution[0].value)
        assertEquals("30대", stats.ageDistribution[1].label)
        assertEquals(7L, stats.ageDistribution[1].value)
    }

    @Test
    fun `getDashboardStats casts age group count from BigInteger to Long`() {
        setupMocks(ageGroups = listOf(arrayOf<Any>("10대", BigInteger.valueOf(3))))

        val stats = statisticsService.getDashboardStats()

        assertEquals(3L, stats.ageDistribution[0].value)
    }

    @Test
    fun `getDashboardStats takes only top 5 cities from distribution`() {
        val cities = (1..8).map { ChartDataDto("City$it", it.toLong()) }
        setupMocks(cities = cities)

        val stats = statisticsService.getDashboardStats()

        assertEquals(5, stats.cityDistribution.size)
        assertEquals("City1", stats.cityDistribution[0].label)
        assertEquals("City5", stats.cityDistribution[4].label)
    }

    @Test
    fun `getDashboardStats passes through gender distribution unchanged`() {
        val genders =
            listOf(
                ChartDataDto("형제", 20L),
                ChartDataDto("자매", 22L),
            )
        setupMocks(genders = genders)

        val stats = statisticsService.getDashboardStats()

        assertEquals(genders, stats.genderDistribution)
    }

    @Test
    fun `getDashboardStats returns empty distributions when no data`() {
        setupMocks()

        val stats = statisticsService.getDashboardStats()

        assertEquals(0L, stats.totalMembers)
        assertEquals(0.0, stats.averageAge)
        assertEquals(emptyList<ChartDataDto>(), stats.cityDistribution)
        assertEquals(emptyList<ChartDataDto>(), stats.ageDistribution)
        assertEquals(emptyList<ChartDataDto>(), stats.genderDistribution)
    }
}
