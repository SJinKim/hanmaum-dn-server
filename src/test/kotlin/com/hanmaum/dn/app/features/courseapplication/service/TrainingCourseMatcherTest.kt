package com.hanmaum.dn.app.features.courseapplication.service

import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.features.courseapplication.client.ExternalCourse
import com.hanmaum.dn.app.features.training.domain.TrainingCode
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainingCourseMatcherTest {
    /** name_ko as seeded, plus the aliases of V20260915120010. */
    private val trainings =
        listOf(
            TrainingSearchTerms(TrainingCode.BAPTISM_MEMBERSHIP, listOf("세례입교", "세례교육")),
            TrainingSearchTerms(TrainingCode.QT_BASIC_SEMINAR, listOf("큐티베이직세미나", "큐베세")),
            TrainingSearchTerms(TrainingCode.QT_ADVANCED_SEMINAR, listOf("큐심세")),
            TrainingSearchTerms(TrainingCode.ONE_ON_ONE, listOf("일대일제자양육")),
            TrainingSearchTerms(TrainingCode.YOUTH_POWER_DISCIPLESHIP, listOf("청년 파워제자반", "청년부 파워제자반")),
            TrainingSearchTerms(TrainingCode.ONE_ON_ONE_SCHOOL, listOf("일대일양육자스쿨")),
            TrainingSearchTerms(TrainingCode.MINISTRY_CLASS, listOf("사역반")),
            TrainingSearchTerms(TrainingCode.PROSPECTIVE_LEADER, listOf("예비순교육")),
            TrainingSearchTerms(TrainingCode.BIBLE_PANORAMA, listOf("성경파노라마")),
            TrainingSearchTerms(TrainingCode.BIBLE_OVERVIEW, listOf("성경개관")),
        )

    /** Names taken verbatim from the external course list of 2026-09-14. */
    private val courses =
        listOf(
            course(36, "마더와이즈 \"지혜\" 5기"),
            course(37, "2022 상반기 어와나 Awana 추가인원 모집"),
            course(41, "왕초보 성경 파노라마"),
            course(42, "상반기 세례 / 입교 / 유아,아동세례"),
            course(48, "제4기 청년부 파워제자반"),
            course(50, "2023 큐티서약"),
            course(51, "2023 성경통독"),
            course(59, "성경개관 (신약)"),
            course(60, "학부모 특강"),
            course(64, "제5기 청년부 파워제자반"),
            course(74, "일대일 양육자 스쿨 신청"),
            course(76, "업그레이드 부부학교 3기"),
            course(90, "2025 상반기 세례교육 신청"),
            course(91, "2025 성경개관 (구약) 오전반"),
            course(100, "청년교구 성경개관 (신약)"),
            course(105, "큐베세 여자반"),
            course(106, "큐베세 직장인/청년 반"),
            course(56, "파더와이즈 7기"),
            course(3, "일대일 제자양육"),
        )

    private fun course(
        id: Int,
        name: String,
        startsAt: String? = null,
        endsAt: String? = null,
    ) = ExternalCourse(id = id, name = name, registrationStartsAt = startsAt, registrationEndsAt = endsAt)

    private fun idsOf(
        result: Map<TrainingCode, List<ExternalCourse>>,
        code: TrainingCode,
    ) = result.getValue(code).map { it.id }.toSet()

    @Test
    fun `every 청년부 course lands on its training, whitespace, punctuation and prefixes notwithstanding`() {
        val result = TrainingCourseMatcher.match(trainings, courses)

        assertEquals(setOf(42, 90), idsOf(result, TrainingCode.BAPTISM_MEMBERSHIP))
        assertEquals(setOf(105, 106), idsOf(result, TrainingCode.QT_BASIC_SEMINAR))
        assertEquals(setOf(3), idsOf(result, TrainingCode.ONE_ON_ONE))
        assertEquals(setOf(48, 64), idsOf(result, TrainingCode.YOUTH_POWER_DISCIPLESHIP))
        assertEquals(setOf(74), idsOf(result, TrainingCode.ONE_ON_ONE_SCHOOL))
        assertEquals(setOf(41), idsOf(result, TrainingCode.BIBLE_PANORAMA))
        assertEquals(setOf(59, 91, 100), idsOf(result, TrainingCode.BIBLE_OVERVIEW))
    }

    @Test
    fun `trainings without any external course get an empty list`() {
        val result = TrainingCourseMatcher.match(trainings, courses)

        assertTrue(result.getValue(TrainingCode.QT_ADVANCED_SEMINAR).isEmpty())
        assertTrue(result.getValue(TrainingCode.MINISTRY_CLASS).isEmpty())
        assertTrue(result.getValue(TrainingCode.PROSPECTIVE_LEADER).isEmpty())
    }

    @Test
    fun `courses of other departments match no training`() {
        val matched =
            TrainingCourseMatcher
                .match(trainings, courses)
                .values
                .flatten()
                .map { it.id }
                .toSet()

        listOf(36, 37, 50, 51, 56, 60, 76).forEach { assertFalse(it in matched, "course $it must not match") }
    }

    @Test
    fun `a blank Korean name matches nothing instead of everything`() {
        val result =
            TrainingCourseMatcher.match(listOf(TrainingSearchTerms(TrainingCode.KAIROS, listOf(" ", ""))), courses)

        assertTrue(result.getValue(TrainingCode.KAIROS).isEmpty())
    }

    // ─── Offerings ────────────────────────────────────────────────────────────

    private val now = Instant.parse("2026-09-14T10:00:00Z")

    @Test
    fun `only women may apply to 여자반 and a member without a recorded gender may not`() {
        val offering =
            CourseOffering.evaluate(course(105, "큐베세 여자반", "2026-09-01 00:00:00", "2026-09-30 23:59:59"), now)

        assertTrue(offering.isOpenFor(Gender.F))
        assertFalse(offering.isOpenFor(Gender.M))
        assertFalse(offering.isOpenFor(null))
    }

    @Test
    fun `a course the API cannot take applications for is not open`() {
        val offering =
            CourseOffering.evaluate(
                course(1, "큐베세 직장인/청년 반", "2026-09-01 00:00:00", "2026-09-30 23:59:59")
                    .copy(apiApplicationSupported = false),
                now,
            )

        assertFalse(offering.isOpen)
    }

    @Test
    fun `a training is open for a man only through a course he is eligible for`() {
        val offering =
            TrainingOffering.evaluate(
                listOf(
                    course(105, "큐베세 여자반", "2026-09-01 00:00:00", "2026-09-30 23:59:59"),
                    course(106, "큐베세 직장인/청년 반", "2026-01-22 07:00:00", "2026-02-28 23:59:59"),
                ),
                now,
            )

        assertTrue(offering.isOpenFor(Gender.F))
        assertFalse(offering.isOpenFor(Gender.M))
        assertEquals(listOf(105), offering.openCoursesFor(Gender.F).map { it.course.id })
    }

    @Test
    fun `the displayed course is the open one closing first, else the one ending last`() {
        val courses =
            listOf(
                course(1, "큐베세 여자반", "2026-09-01 00:00:00", "2026-10-31 23:59:59"),
                course(2, "큐베세 직장인/청년 반", "2026-09-01 00:00:00", "2026-09-20 23:59:59"),
                course(3, "큐베세 직장인/청년 반", "2025-09-21 07:00:00", "2025-10-12 23:59:59"),
            )
        val offering = TrainingOffering.evaluate(courses, now)

        assertEquals(2, offering.displayedCourseFor(Gender.F)?.course?.id)

        val closed = TrainingOffering.evaluate(listOf(courses[2]), now)
        assertEquals(3, closed.displayedCourseFor(Gender.M)?.course?.id)
        assertNull(TrainingOffering.evaluate(emptyList(), now).displayedCourseFor(Gender.M))
    }
}
