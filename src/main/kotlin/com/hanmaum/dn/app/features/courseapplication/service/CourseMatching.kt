package com.hanmaum.dn.app.features.courseapplication.service

import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.features.courseapplication.client.ExternalCourse
import com.hanmaum.dn.app.features.training.domain.TrainingCode
import java.time.Instant

/** Name handling shared by matching and eligibility. */
object CourseNames {
    private const val WOMEN_ONLY_MARKER = "여자반"

    /**
     * Letters and digits only, lower-cased. "큐베세 직장인/청년 반" and "큐베세직장인청년반" are
     * the same name; so are "청년부 파워제자반" and "청년부파워제자반". Hangul counts as letters.
     */
    fun normalize(name: String): String = name.filter(Char::isLetterOrDigit).lowercase()

    /** 큐베세 여자반 and the like: open to women only. */
    fun isWomenOnly(courseName: String): Boolean = normalize(courseName).contains(WOMEN_ONLY_MARKER)
}

/** What a training is searched by: its Korean name and its aliases. */
data class TrainingSearchTerms(
    val code: TrainingCode,
    val terms: Collection<String>,
)

/**
 * Assigns external courses to DN trainings.
 *
 * A course belongs to a training when its normalised name contains any of the training's
 * normalised terms, anywhere — cohort prefixes like 제5기 and suffixes like 여자반 do not
 * matter. Courses that match no training are dropped: the external list serves every
 * department (마더와이즈, 어와나, …), and only 청년부 trainings exist here.
 */
object TrainingCourseMatcher {
    fun match(
        trainings: List<TrainingSearchTerms>,
        courses: List<ExternalCourse>,
    ): Map<TrainingCode, List<ExternalCourse>> {
        val normalizedCourses = courses.map { it to CourseNames.normalize(it.name) }
        return trainings.associate { training ->
            // A blank name_ko must not normalise to "" and match every course.
            val terms =
                training.terms
                    .map(CourseNames::normalize)
                    .filter { it.isNotEmpty() }
                    .distinct()
            training.code to
                normalizedCourses
                    .filter { (_, name) -> terms.any { name.contains(it) } }
                    .map { (course, _) -> course }
        }
    }
}

/** One external course with its registration state evaluated at a fixed instant. */
data class CourseOffering(
    val course: ExternalCourse,
    /** Null when the API sent a timestamp this server cannot read; such a course is closed. */
    val window: RegistrationWindow?,
    val isOpen: Boolean,
    val isAlwaysOpen: Boolean,
) {
    val isWomenOnly: Boolean get() = CourseNames.isWomenOnly(course.name)

    /** A member whose gender is not recorded is not eligible for a women-only course. */
    fun isEligible(gender: Gender?): Boolean = !isWomenOnly || gender == Gender.F

    fun isOpenFor(gender: Gender?): Boolean = isOpen && isEligible(gender)

    companion object {
        fun evaluate(
            course: ExternalCourse,
            now: Instant,
        ): CourseOffering {
            val window = RegistrationWindow.of(course.registrationStartsAt, course.registrationEndsAt)
            // apiApplicationSupported=false: the course needs fields the API cannot take
            // (a photo, say). It is listed, but applying goes through the website.
            val open = window != null && window.isOpen(now) && course.apiApplicationSupported != false
            return CourseOffering(
                course = course,
                window = window,
                isOpen = open,
                isAlwaysOpen = open && window.isAlwaysOpen(now),
            )
        }
    }
}

/** All external courses of one training, evaluated together. */
data class TrainingOffering(
    val courses: List<CourseOffering>,
) {
    val hasCourses: Boolean get() = courses.isNotEmpty()

    fun openCoursesFor(gender: Gender?): List<CourseOffering> = courses.filter { it.isOpenFor(gender) }

    fun isOpenFor(gender: Gender?): Boolean = courses.any { it.isOpenFor(gender) }

    /**
     * The course whose window the list shows for this member.
     *
     * While something is open: the open course that closes first — the deadline that matters
     * soonest. Otherwise the course with the latest end, which is the upcoming run if one is
     * announced and the most recent past run if not.
     */
    fun displayedCourseFor(gender: Gender?): CourseOffering? =
        openCoursesFor(gender).minByOrNull { it.window?.endsAt?.toInstant() ?: Instant.MAX }
            ?: courses
                .filter { it.window?.endsAt != null }
                .maxByOrNull { requireNotNull(it.window?.endsAt).toInstant() }

    companion object {
        fun evaluate(
            courses: List<ExternalCourse>,
            now: Instant,
        ): TrainingOffering = TrainingOffering(courses.map { CourseOffering.evaluate(it, now) })
    }
}
