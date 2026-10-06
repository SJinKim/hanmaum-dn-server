package com.hanmaum.dn.app.features.statistics.service

import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.repository.AttendanceDefinitionRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceLogRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceServiceDayCount
import com.hanmaum.dn.app.features.attendance.repository.DivisionAttendeeCount
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.repository.MinistryAssignmentRepository
import com.hanmaum.dn.app.features.ministry.repository.MinistryHeadcount
import com.hanmaum.dn.app.features.statistics.api.v1.dto.ChartDataDto
import com.hanmaum.dn.app.features.statistics.api.v1.dto.TrainingStageDto
import com.hanmaum.dn.app.features.training.domain.TrainingCode
import com.hanmaum.dn.app.features.training.repository.TrainingStageCount
import com.hanmaum.dn.app.features.training.repository.UserTrainingRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.math.BigInteger
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@ExtendWith(MockitoExtension::class)
class StatisticsServiceTest {
    @Mock
    private lateinit var memberRepository: MemberRepository

    @Mock
    private lateinit var attendanceDefinitionRepository: AttendanceDefinitionRepository

    @Mock
    private lateinit var attendanceLogRepository: AttendanceLogRepository

    @Mock
    private lateinit var userTrainingRepository: UserTrainingRepository

    @Mock
    private lateinit var ministryAssignmentRepository: MinistryAssignmentRepository

    private lateinit var statisticsService: StatisticsService

    /** Friday 2026-05-15: the second month of Q2, this week starts Monday 05-11. */
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 5, 15)

    @BeforeEach
    fun setUp() {
        val clock = Clock.fixed(ZonedDateTime.of(today.atTime(10, 0), zone).toInstant(), zone)
        statisticsService =
            StatisticsService(
                memberRepository,
                attendanceDefinitionRepository,
                attendanceLogRepository,
                userTrainingRepository,
                ministryAssignmentRepository,
                clock,
            )
    }

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

    @Test
    fun `the period parameter maps to buckets around today`() {
        assertEquals(12, StatisticsPeriod.YEAR.buckets(today).size)
        assertEquals(listOf("2026-04", "2026-05", "2026-06"), StatisticsPeriod.QUARTER.buckets(today).map { it.label })
        val days = StatisticsPeriod.LAST_30_DAYS.buckets(today)
        assertEquals(30, days.size)
        assertEquals("2026-04-16", days.first().label)
        assertEquals("2026-05-15", days.last().label)
        assertEquals(StatisticsPeriod.QUARTER, StatisticsPeriod.fromParam("quarter"))
    }

    @Test
    fun `an unknown period is a bad request`() {
        val error = assertThrows<ResponseStatusException> { StatisticsPeriod.fromParam("week") }
        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
    }

    @Test
    fun `growth trend counts members by join date for this year so far and all of last year`() {
        setupMocks()
        `when`(memberRepository.findAllByDeletedAtIsNull()).thenReturn(
            listOf(
                member(registrationDate = LocalDate.of(2025, 3, 10)),
                member(registrationDate = LocalDate.of(2026, 2, 1)),
                member(registrationDate = null).apply { createdAt = null },
                member(registrationDate = null).apply { createdAt = ZonedDateTime.of(2026, 5, 20, 0, 0, 0, 0, zone).toInstant() },
            ),
        )

        val trend = statisticsService.getDashboardStats(StatisticsPeriod.YEAR).growthTrend

        assertEquals(listOf(2026, 2025), trend.map { it.year })
        assertEquals(listOf(2L, 3L, 3L, 3L, 3L), trend[0].points.map { it.value })
        assertEquals("2026-05", trend[0].points.last().label)
        assertEquals(12, trend[1].points.size)
        assertEquals(ChartDataDto("2025-02", 1L), trend[1].points[1])
        assertEquals(ChartDataDto("2025-03", 2L), trend[1].points[2])
    }

    @Test
    fun `attendance rate divides check-ins by members times services held`() {
        setupMocks()
        `when`(memberRepository.findAllByDeletedAtIsNull()).thenReturn(List(3) { member(LocalDate.of(2026, 1, 1)) })
        `when`(attendanceLogRepository.countByServiceAndDayBetween(LocalDate.of(2026, 4, 1), today)).thenReturn(
            listOf(
                AttendanceServiceDayCount(1, LocalDate.of(2026, 4, 5), 2),
                AttendanceServiceDayCount(1, LocalDate.of(2026, 5, 10), 3),
                AttendanceServiceDayCount(2, LocalDate.of(2026, 5, 10), 1),
            ),
        )

        val stats = statisticsService.getDashboardStats(StatisticsPeriod.QUARTER)

        assertEquals("quarter", stats.period)
        assertEquals(listOf(ChartDataDto("2026-04", 67L), ChartDataDto("2026-05", 67L)), stats.attendanceRate)
    }

    @Test
    fun `service attendance compares this week with last week`() {
        setupMocks()
        val sunday = AttendanceDefinition("주일예배", DayOfWeek.SUNDAY, LocalTime.of(11, 0), LocalTime.of(13, 0)).apply { id = 1 }
        `when`(attendanceDefinitionRepository.findAll(true)).thenReturn(listOf(sunday))
        `when`(attendanceLogRepository.countByServiceAndDayBetween(LocalDate.of(2026, 4, 16), today)).thenReturn(
            listOf(
                AttendanceServiceDayCount(1, LocalDate.of(2026, 5, 3), 9),
                AttendanceServiceDayCount(1, LocalDate.of(2026, 5, 10), 3),
                AttendanceServiceDayCount(1, LocalDate.of(2026, 5, 12), 5),
            ),
        )

        val row = statisticsService.getDashboardStats(StatisticsPeriod.LAST_30_DAYS).serviceAttendance.single()

        assertEquals("주일예배", row.title)
        assertEquals(sunday.publicId, row.definitionPublicId)
        assertEquals(5L, row.thisWeek)
        assertEquals(3L, row.lastWeek)
    }

    @Test
    fun `division, training and ministry rows are mapped for the charts`() {
        setupMocks()
        `when`(attendanceLogRepository.countAttendeesByDivisionBetween(LocalDate.of(2026, 1, 1), today)).thenReturn(
            listOf(DivisionAttendeeCount("느헤미야", 4), DivisionAttendeeCount("다니엘", 6), DivisionAttendeeCount(null, 2)),
        )
        `when`(
            userTrainingRepository.countMembersByTrainingCode(
                StatisticsService.TRAINING_STAGES,
                StatisticsService.CURRENT_TRAINING_STATUSES,
            ),
        ).thenReturn(
            listOf(
                TrainingStageCount(TrainingCode.QT_BASIC_SEMINAR, "QT Basic Seminar", "큐베세", 1, 7),
                TrainingStageCount(TrainingCode.ONE_ON_ONE, "One on One", null, 2, 3),
            ),
        )
        `when`(ministryAssignmentRepository.countCurrentMembersByMinistry())
            .thenReturn(listOf(MinistryHeadcount("찬양팀", 8), MinistryHeadcount("미디어팀", 2)))

        val stats = statisticsService.getDashboardStats(StatisticsPeriod.YEAR)

        assertEquals(listOf(ChartDataDto("느헤미야", 4L), ChartDataDto("다니엘", 6L)), stats.divisionAttendance)
        assertEquals(
            listOf(TrainingStageDto("QT_BASIC_SEMINAR", "큐베세", 7L), TrainingStageDto("ONE_ON_ONE", "One on One", 3L)),
            stats.trainingStages,
        )
        assertEquals(listOf(ChartDataDto("찬양팀", 8L), ChartDataDto("미디어팀", 2L)), stats.ministryHeadcount)
    }

    private fun member(registrationDate: LocalDate?) = Member(lastName = "홍", firstName = "길동", registrationDate = registrationDate)
}
