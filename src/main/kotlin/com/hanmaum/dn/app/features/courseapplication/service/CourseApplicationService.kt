package com.hanmaum.dn.app.features.courseapplication.service

import com.hanmaum.dn.app.common.api.ApiErrorCode
import com.hanmaum.dn.app.common.domainvalue.Baptism
import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.features.courseapplication.client.CourseApplicationApiClient
import com.hanmaum.dn.app.features.courseapplication.client.CourseApplicationApiRejectedException
import com.hanmaum.dn.app.features.courseapplication.client.CourseApplicationApiUnavailableException
import com.hanmaum.dn.app.features.courseapplication.client.ExternalCourse
import com.hanmaum.dn.app.features.courseapplication.client.ExternalCreateApplicationRequest
import com.hanmaum.dn.app.features.courseapplication.domain.CourseApplicationAttempt
import com.hanmaum.dn.app.features.courseapplication.domain.CourseApplicationAttemptStatus
import com.hanmaum.dn.app.features.courseapplication.repository.CourseApplicationAttemptRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.service.CurrentMemberResolver
import com.hanmaum.dn.app.features.training.api.toDetailDto
import com.hanmaum.dn.app.features.training.api.toDto
import com.hanmaum.dn.app.features.training.api.v1.dto.ApplicantPrefillDto
import com.hanmaum.dn.app.features.training.api.v1.dto.MyTrainingApplicationDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingApplicationRequest
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingCourseDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDetailDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingRegistrationDto
import com.hanmaum.dn.app.features.training.domain.Training
import com.hanmaum.dn.app.features.training.domain.TrainingStatus
import com.hanmaum.dn.app.features.training.domain.TrainingVariant
import com.hanmaum.dn.app.features.training.domain.UserTraining
import com.hanmaum.dn.app.features.training.repository.TrainingRepository
import com.hanmaum.dn.app.features.training.repository.UserTrainingRepository
import com.hanmaum.dn.app.features.training.service.TrainingService
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * The 양육 list, detail page and application, backed by application.hanmaum.de.
 *
 * Trainings come from this database; which of them can be applied to, and when, comes from
 * the external course list. Applications are made there and mirrored here: a
 * [CourseApplicationAttempt] makes retries idempotent, a [UserTraining] row records the
 * participation the rest of the system already understands.
 *
 * No method holds a database transaction across an external call. The course list is asked
 * outside any transaction, and an application is written in steps — PENDING before the call,
 * CREATED after it — so a slow or failing upstream never pins a connection.
 */
@Service
class CourseApplicationService(
    private val client: CourseApplicationApiClient,
    private val trainingRepository: TrainingRepository,
    private val userTrainingRepository: UserTrainingRepository,
    private val attemptRepository: CourseApplicationAttemptRepository,
    private val currentMemberResolver: CurrentMemberResolver,
    transactionManager: PlatformTransactionManager,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val writeTx = TransactionTemplate(transactionManager)

    private val readTx = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    /**
     * The 양육 list: active trainings that have at least one external course, those the
     * caller can apply to right now first, then by progression.
     */
    fun listTrainings(keycloakSubject: String): List<TrainingDto> {
        val member = currentMemberResolver.require(keycloakSubject)
        val memberId = requireNotNull(member.id)
        val (trainings, applications) =
            requireNotNull(
                readTx.execute { trainingRepository.findActiveWithAliases() to myApplicationsByTraining(memberId) },
            )
        val courses = coursesOrUnavailable()
        val now = clock.instant()
        val gender = member.gender
        val matched = TrainingCourseMatcher.match(trainings.map { it.searchTerms() }, courses)

        return trainings
            .map { it to TrainingOffering.evaluate(matched[it.code].orEmpty(), now) }
            .filter { (_, offering) -> offering.hasCourses }
            .sortedWith(
                compareByDescending<Pair<Training, TrainingOffering>> { (_, offering) -> offering.isOpenFor(gender) }
                    .thenBy { (training, _) -> training.sortOrder },
            ).map { (training, offering) ->
                val displayed = offering.displayedCourseFor(gender)
                training.toDto().copy(
                    openForRegistration = offering.isOpenFor(gender),
                    registrationStartsAt = displayed?.window?.startsAt,
                    registrationEndsAt = displayed?.window?.endsAt,
                    isAlwaysOpen = displayed.isAlwaysOpenFor(gender),
                    myApplication = applications[training.id],
                )
            }
    }

    /** The 양육 detail page, with the courses open right now and the form's starting values. */
    fun getTrainingDetail(
        publicId: UUID,
        keycloakSubject: String,
    ): TrainingDetailDto {
        val member = currentMemberResolver.require(keycloakSubject)
        val memberId = requireNotNull(member.id)
        val snapshot =
            requireNotNull(
                readTx.execute {
                    val training = requireTraining(publicId)
                    val trainingId = requireNotNull(training.id)
                    val participations = userTrainingRepository.findByMemberId(memberId)
                    DetailSnapshot(
                        detail =
                            training.toDetailDto(
                                userTrainingRepository.countByTrainingIdAndStatusInAndDeletedAtIsNull(
                                    trainingId,
                                    TrainingService.REGISTERED_STATUSES,
                                ),
                            ),
                        terms = training.searchTerms(),
                        myApplication = myApplicationsByTraining(memberId, participations)[trainingId],
                        prefill = member.toPrefill(participations),
                    )
                },
            )
        val offering = offeringFor(snapshot.terms, coursesOrUnavailable())
        val gender = member.gender
        val displayed = offering.displayedCourseFor(gender)

        return snapshot.detail.copy(
            openForRegistration = offering.isOpenFor(gender),
            registrationStartsAt = displayed?.window?.startsAt,
            registrationEndsAt = displayed?.window?.endsAt,
            isAlwaysOpen = displayed.isAlwaysOpenFor(gender),
            courses = offering.courses.filter { it.isOpen }.map { it.toDto(gender) },
            myApplication = snapshot.myApplication,
            applicantPrefill = snapshot.prefill,
        )
    }

    /** 나의 신청: the caller's applications made through the app, newest first. */
    fun myTrainingApplications(keycloakSubject: String): List<MyTrainingApplicationDto> {
        val memberId = requireNotNull(currentMemberResolver.require(keycloakSubject).id)
        return requireNotNull(
            readTx.execute {
                val participations = userTrainingRepository.findByMemberId(memberId)
                attemptRepository
                    .findByMember(memberId, setOf(CourseApplicationAttemptStatus.CREATED))
                    .map { it.toMyApplication(participations) }
            },
        )
    }

    /**
     * Applies the caller to one external course of a training.
     *
     * Idempotent per member and course: the first call mints a clientApplicationId and stores
     * it as PENDING before the external call; every retry sends the same id, so the external
     * API returns the application it already has instead of creating a second one. A retry
     * after the application was recorded returns it without calling out at all.
     */
    fun apply(
        publicId: UUID,
        keycloakSubject: String,
        request: TrainingApplicationRequest,
    ): TrainingRegistrationDto {
        val member = currentMemberResolver.require(keycloakSubject)
        val memberId = requireNotNull(member.id)
        val training = requireNotNull(readTx.execute { requireTraining(publicId) })
        val offering = offeringFor(training.searchTerms(), coursesOrUnavailable())
        val courseOffering =
            offering.courses.firstOrNull { it.course.id == request.externalCourseId }
                ?: throw CourseApplicationException(
                    HttpStatus.NOT_FOUND,
                    ApiErrorCode.COURSE_APPLICATION_COURSE_NOT_FOUND,
                    "이 과정에서 신청할 수 없는 반입니다.",
                )
        val course = courseOffering.course

        val existing = attemptRepository.findLive(memberId, course.id)
        if (existing?.status == CourseApplicationAttemptStatus.CREATED) {
            // The first response was lost after everything was recorded. Answer as the first
            // time did, even if the window has closed since.
            return finish(requireNotNull(existing.id), member, training, course, requireNotNull(existing.externalApplicationId))
        }

        if (!courseOffering.isEligible(member.gender)) {
            throw CourseApplicationException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.COURSE_APPLICATION_NOT_ELIGIBLE,
                "여자반은 자매만 신청할 수 있습니다.",
            )
        }
        if (!courseOffering.isOpen) {
            throw CourseApplicationException(HttpStatus.CONFLICT, ApiErrorCode.COURSE_APPLICATION_CLOSED, CLOSED_MESSAGE)
        }
        rejectActiveParticipation(memberId, requireNotNull(training.id))
        val applicant = resolveApplicant(member, request)

        val attempt = existing ?: insertPending(member, training, course)
        val attemptId = requireNotNull(attempt.id)
        if (existing != null) {
            // PENDING from an earlier try: the external API may already have it.
            unavailableAs503 { client.findApplicationByClientId(attempt.clientApplicationId) }
                ?.let { return finish(attemptId, member, training, course, it.id) }
        }

        val externalRequest = applicant.toExternal(attempt.clientApplicationId, member.publicId, course.id)
        val externalId =
            try {
                unavailableAs503 { client.createApplication(externalRequest) }.application.id
            } catch (e: CourseApplicationApiRejectedException) {
                if (e.code != IDEMPOTENCY_CONFLICT) throw e.toApplicationException()
                // Same id, different content: the external API has this application from an
                // earlier try whose details differed. It exists, so record it.
                unavailableAs503 { client.findApplicationByClientId(attempt.clientApplicationId) }?.id
                    ?: throw unavailable(e)
            }
        return finish(attemptId, member, training, course, externalId)
    }

    /**
     * Cancels the caller's latest application to a training.
     *
     * The external API first, this database second: when the call fails nothing changes here
     * and the member stays applied on both sides. The attempt becomes CANCELLED, which takes it
     * out of findLive, so applying again mints a new clientApplicationId instead of replaying
     * the cancelled application. A repeat answers from the cancelled attempt without calling out.
     */
    fun cancel(
        publicId: UUID,
        keycloakSubject: String,
    ): MyTrainingApplicationDto {
        val memberId = requireNotNull(currentMemberResolver.require(keycloakSubject).id)
        val (training, attempt) =
            requireNotNull(
                readTx.execute {
                    val training = requireTraining(publicId)
                    // findByMember is newest first: an older attempt belongs to an earlier round.
                    training to
                        attemptRepository
                            .findByMember(memberId, CourseApplicationAttemptStatus.entries.toSet())
                            .firstOrNull { it.training.id == training.id }
                },
            )
        if (attempt == null) throw noApplication()
        if (attempt.status == CourseApplicationAttemptStatus.CANCELLED) return attempt.toCancelledApplication()

        val participation = participationOf(memberId, requireNotNull(training.id))
        if (participation?.status == TrainingStatus.COMPLETED && participation.isFor(attempt.appliedOn())) {
            throw CourseApplicationException(
                HttpStatus.CONFLICT,
                ApiErrorCode.COURSE_APPLICATION_NOT_CANCELLABLE,
                "이미 수료한 과정은 취소할 수 없습니다.",
            )
        }

        val externalId =
            attempt.externalApplicationId
                // PENDING: the create call timed out, so the external API may or may not have it.
                ?: unavailableAs503 { client.findApplicationByClientId(attempt.clientApplicationId) }?.id
                ?: throw noApplication()
        val cancelled =
            try {
                unavailableAs503 { client.cancelApplication(externalId) }
            } catch (e: CourseApplicationApiRejectedException) {
                throw e.toApplicationException()
            }
        if (cancelled.status != EXTERNAL_CANCELLED) {
            log.error("Course application API did not cancel externalApplicationId={} status={}", externalId, cancelled.status)
            throw unavailable(IllegalStateException("Cancellation answered with status ${cancelled.status}"))
        }

        return requireNotNull(
            writeTx.execute {
                val row = attemptRepository.findById(requireNotNull(attempt.id)).orElseThrow()
                if (row.status != CourseApplicationAttemptStatus.CANCELLED) row.markCancelled(externalId)
                userTrainingRepository
                    .findByMemberIdAndTrainingIdAndVariantAndDeletedAtIsNull(memberId, requireNotNull(training.id), null)
                    .orElse(null)
                    // A row applied for before this application is older history, e.g. a
                    // 큐베세 completed years ago, and stays as it is.
                    ?.takeIf { it.isFor(row.appliedOn()) && it.status != TrainingStatus.COMPLETED }
                    ?.status = TrainingStatus.DROPPED
                log.info(
                    "Course application cancelled memberId={} trainingCode={} externalCourseId={}",
                    memberId,
                    training.code,
                    row.externalCourseId,
                )
                row.toCancelledApplication()
            },
        )
    }

    // ─── Application steps ────────────────────────────────────────────────────

    private fun insertPending(
        member: Member,
        training: Training,
        course: ExternalCourse,
    ): CourseApplicationAttempt =
        try {
            requireNotNull(
                writeTx.execute {
                    attemptRepository.saveAndFlush(
                        CourseApplicationAttempt(
                            member = member,
                            training = training,
                            externalCourseId = course.id,
                            externalCourseName = course.name,
                        ),
                    )
                },
            )
        } catch (e: DataIntegrityViolationException) {
            // uq_course_application_attempt_member_course: a concurrent request for the same
            // member and course inserted first. Continue with its id rather than minting a
            // second one.
            attemptRepository.findLive(requireNotNull(member.id), course.id) ?: throw e
        }

    /** Marks the attempt CREATED and records the participation, in one transaction. */
    private fun finish(
        attemptId: Long,
        member: Member,
        training: Training,
        course: ExternalCourse,
        externalApplicationId: Long,
    ): TrainingRegistrationDto =
        requireNotNull(
            writeTx.execute {
                val attempt = attemptRepository.findById(attemptId).orElseThrow()
                if (attempt.status != CourseApplicationAttemptStatus.CREATED) attempt.markCreated(externalApplicationId)
                val appliedOn = attempt.appliedOn()
                val participation = recordParticipation(member, training, appliedOn)
                log.info(
                    "Course application recorded memberId={} trainingCode={} externalCourseId={}",
                    member.id,
                    training.code,
                    course.id,
                )
                TrainingRegistrationDto(
                    trainingPublicId = training.publicId.toString(),
                    trainingName = training.name,
                    status = participation.statusFor(appliedOn).name,
                    appliedOn = appliedOn,
                    registeredCount =
                        userTrainingRepository.countByTrainingIdAndStatusInAndDeletedAtIsNull(
                            requireNotNull(training.id),
                            TrainingService.REGISTERED_STATUSES,
                        ),
                    capacity = course.capacity?.takeIf { it > 0 },
                    externalCourseId = course.id,
                    courseName = attempt.externalCourseName,
                )
            },
        )

    /**
     * The user_training row this application stands for.
     *
     * No row: one is created as APPLIED. A DROPPED row is reopened, its old start and end
     * cleared so the timeline constraint holds for the new application. Any other row is left
     * alone — a COMPLETED 큐베세 from years ago stays completed, and the earlier checks already
     * turned away anyone still applied or taking part.
     */
    private fun recordParticipation(
        member: Member,
        training: Training,
        appliedOn: LocalDate,
    ): UserTraining {
        val row =
            userTrainingRepository
                .findByMemberIdAndTrainingIdAndVariantAndDeletedAtIsNull(requireNotNull(member.id), requireNotNull(training.id), null)
                .orElse(null)
        return when {
            row == null ->
                userTrainingRepository.save(
                    UserTraining(member = member, training = training, status = TrainingStatus.APPLIED, appliedOn = appliedOn),
                )
            row.status == TrainingStatus.DROPPED -> {
                row.status = TrainingStatus.APPLIED
                row.appliedOn = appliedOn
                row.startedOn = null
                row.completedAt = null
                row
            }
            else -> row
        }
    }

    private fun rejectActiveParticipation(
        memberId: Long,
        trainingId: Long,
    ) {
        val row = participationOf(memberId, trainingId)
        if (row != null && row.status in ACTIVE_STATUSES) {
            throw CourseApplicationException(
                HttpStatus.CONFLICT,
                ApiErrorCode.COURSE_APPLICATION_ALREADY_APPLIED,
                "이미 신청했거나 참여 중인 과정입니다.",
            )
        }
    }

    /** Request values first, the profile where the request leaves a field out. */
    private fun resolveApplicant(
        member: Member,
        request: TrainingApplicationRequest,
    ): Applicant {
        val name = request.name.orIfBlank(member.getFullName())
        val birthDate = request.birthDate ?: member.birthDate
        val email = request.email.orIfBlank(member.email)
        val phone = request.phone.orIfBlank(member.phoneNumber)
        if (name == null || birthDate == null || email == null || phone == null) {
            val missing =
                listOfNotNull(
                    "name".takeIf { name == null },
                    "birthDate".takeIf { birthDate == null },
                    "email".takeIf { email == null },
                    "phone".takeIf { phone == null },
                )
            throw CourseApplicationException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.COURSE_APPLICATION_INVALID,
                "필수 항목을 입력해주세요.",
                fieldErrors = missing.associateWith { REQUIRED_MESSAGE },
            )
        }
        return Applicant(
            name = name,
            birthDate = birthDate,
            email = email,
            phone = phone,
            gender = request.gender.orIfBlank(member.gender?.name),
            residence = request.residence.orIfBlank(member.city),
            request = request,
        )
    }

    // ─── Reads ────────────────────────────────────────────────────────────────

    /** Must run inside a transaction: attempts and participations are read together. */
    private fun myApplicationsByTraining(
        memberId: Long,
        participations: List<UserTraining> = userTrainingRepository.findByMemberId(memberId),
    ): Map<Long, MyTrainingApplicationDto> =
        attemptRepository
            .findByMember(memberId, setOf(CourseApplicationAttemptStatus.CREATED))
            .groupBy { requireNotNull(it.training.id) }
            // findByMember is newest first, so the first of each group is the latest application.
            .mapValues { (_, attempts) -> attempts.first().toMyApplication(participations) }

    private fun CourseApplicationAttempt.toMyApplication(participations: List<UserTraining>): MyTrainingApplicationDto {
        val appliedAt = requireNotNull(createdAt)
        val participation = participations.firstOrNull { it.training.id == training.id && it.variant == null }
        return MyTrainingApplicationDto(
            trainingPublicId = training.publicId.toString(),
            trainingName = training.name,
            trainingNameKo = training.nameKo,
            externalCourseId = externalCourseId,
            courseName = externalCourseName,
            appliedAt = appliedAt,
            status = participation.statusFor(LocalDate.ofInstant(appliedAt, clock.zone)).name,
        )
    }

    /**
     * The status of the participation for an application made on [appliedOn]. A row applied
     * for before that is older history and says nothing about this application.
     */
    private fun UserTraining?.statusFor(appliedOn: LocalDate): TrainingStatus =
        this
            ?.takeIf { it.isFor(appliedOn) }
            ?.status
            ?: TrainingStatus.APPLIED

    /** Whether this participation was recorded for an application made on [appliedOn], not for an earlier one. */
    private fun UserTraining.isFor(appliedOn: LocalDate): Boolean = this.appliedOn?.let { !it.isBefore(appliedOn) } == true

    private fun CourseApplicationAttempt.appliedOn(): LocalDate = LocalDate.ofInstant(requireNotNull(createdAt), clock.zone)

    /** Must run inside a transaction, or on an attempt whose training was fetched with it. */
    private fun CourseApplicationAttempt.toCancelledApplication(): MyTrainingApplicationDto =
        MyTrainingApplicationDto(
            trainingPublicId = training.publicId.toString(),
            trainingName = training.name,
            trainingNameKo = training.nameKo,
            externalCourseId = externalCourseId,
            courseName = externalCourseName,
            appliedAt = requireNotNull(createdAt),
            status = TrainingStatus.DROPPED.name,
        )

    private fun participationOf(
        memberId: Long,
        trainingId: Long,
    ): UserTraining? =
        readTx.execute {
            userTrainingRepository.findByMemberIdAndTrainingIdAndVariantAndDeletedAtIsNull(memberId, trainingId, null).orElse(null)
        }

    private fun noApplication(): CourseApplicationException =
        CourseApplicationException(HttpStatus.NOT_FOUND, ApiErrorCode.COURSE_APPLICATION_NOT_FOUND, "취소할 신청 내역이 없습니다.")

    private fun requireTraining(publicId: UUID): Training =
        trainingRepository.findWithAliasesByPublicId(publicId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No training with that id exists.")

    private fun offeringFor(
        terms: TrainingSearchTerms,
        courses: List<ExternalCourse>,
    ): TrainingOffering =
        TrainingOffering.evaluate(
            TrainingCourseMatcher.match(listOf(terms), courses).getValue(terms.code),
            clock.instant(),
        )

    private fun coursesOrUnavailable(): List<ExternalCourse> = unavailableAs503 { client.listCourses() }

    private fun <T> unavailableAs503(block: () -> T): T =
        try {
            block()
        } catch (e: CourseApplicationApiUnavailableException) {
            throw unavailable(e)
        }

    private fun unavailable(cause: Throwable): CourseApplicationException =
        CourseApplicationException(
            HttpStatus.SERVICE_UNAVAILABLE,
            ApiErrorCode.COURSE_APPLICATION_UNAVAILABLE,
            "준비중입니다.",
            cause = cause,
        )

    private fun CourseApplicationApiRejectedException.toApplicationException(): CourseApplicationException =
        when (code) {
            "capacity_full" ->
                CourseApplicationException(HttpStatus.CONFLICT, ApiErrorCode.COURSE_APPLICATION_FULL, "정원이 마감되었습니다.")
            "registration_closed" ->
                CourseApplicationException(HttpStatus.CONFLICT, ApiErrorCode.COURSE_APPLICATION_CLOSED, CLOSED_MESSAGE)
            "validation_failed" ->
                CourseApplicationException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    ApiErrorCode.COURSE_APPLICATION_INVALID,
                    "입력값을 확인해주세요.",
                    fieldErrors = fieldErrors.mapKeys { (name, _) -> ApplicationFormFields.toRequestName(name) },
                )
            "unsupported_course_fields" ->
                CourseApplicationException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    ApiErrorCode.COURSE_APPLICATION_INVALID,
                    "이 과정은 앱에서 신청할 수 없습니다. 홈페이지를 이용해주세요.",
                )
            "course_not_found" ->
                CourseApplicationException(
                    HttpStatus.NOT_FOUND,
                    ApiErrorCode.COURSE_APPLICATION_COURSE_NOT_FOUND,
                    "신청할 수 없는 과정입니다.",
                )
            else -> {
                // Anything else — invalid_json, route_not_found — is this integration's fault,
                // not the applicant's. Logged loudly, shown as 준비중입니다.
                log.error("Unexpected rejection from course application API code={} status={}", code, httpStatus)
                unavailable(this)
            }
        }

    private fun CourseOffering.toDto(gender: Gender?): TrainingCourseDto =
        TrainingCourseDto(
            externalCourseId = course.id,
            name = course.name,
            dateText = course.dateText?.takeIf { it.isNotBlank() },
            description = course.description?.takeIf { it.isNotBlank() },
            secondaryText = course.secondaryText?.takeIf { it.isNotBlank() },
            registrationStartsAt = window?.startsAt,
            registrationEndsAt = window?.endsAt,
            isAlwaysOpen = isAlwaysOpen,
            isEligible = isEligible(gender),
            formFields = ApplicationFormFields.of(course),
        )

    private fun CourseOffering?.isAlwaysOpenFor(gender: Gender?): Boolean = this != null && isAlwaysOpen && isEligible(gender)

    /** Must run inside a transaction: each participation's training is read for its name. */
    private fun Member.toPrefill(participations: List<UserTraining>): ApplicantPrefillDto {
        val baptismCodes = baptism?.formCodes()
        return ApplicantPrefillDto(
            name = getFullName(),
            birthDate = birthDate,
            email = email,
            phone = phoneNumber,
            gender = gender?.name,
            residence = city,
            history =
                participations
                    .filter { it.status == TrainingStatus.COMPLETED }
                    .sortedWith(compareBy(nullsLast()) { it.completedAt })
                    .toLines { row -> row.completedAt?.let { "${row.label()} / ${it.year}년 ${it.monthValue}월" } ?: row.label() },
            waiting = participations.filter { it.status in WAITING_STATUSES }.toLines { it.label() },
            running = participations.filter { it.status == TrainingStatus.IN_PROGRESS }.toLines { it.label() },
            baptized = baptismCodes?.first,
            baptizeType = baptismCodes?.second,
        )
    }

    private fun List<UserTraining>.toLines(line: (UserTraining) -> String): String? =
        takeIf { it.isNotEmpty() }?.joinToString("\n", transform = line)

    /** The Korean name the congregation uses, with 구약 / 신약 for a repeated course. */
    private fun UserTraining.label(): String {
        val name = training.nameKo?.takeIf { it.isNotBlank() } ?: training.name
        return when (variant) {
            null -> name
            TrainingVariant.OLD_TESTAMENT -> "$name 구약"
            TrainingVariant.NEW_TESTAMENT -> "$name 신약"
        }
    }

    /** 세례 여부 and 세례 구분 as the legacy form's option values; the two lists number differently. */
    private fun Baptism.formCodes(): Pair<String, String> =
        when (this) {
            Baptism.INFANT_BAPTIZED -> "1" to "1"
            Baptism.CONFIRMATION -> "2" to "3"
            Baptism.GENERAL_BAPTIZED -> "3" to "4"
            Baptism.UNBAPTIZED -> "4" to "5"
        }

    private class DetailSnapshot(
        val detail: TrainingDetailDto,
        val terms: TrainingSearchTerms,
        val myApplication: MyTrainingApplicationDto?,
        val prefill: ApplicantPrefillDto,
    )

    /** The applicant as sent to the external API: required fields resolved, the rest passed on. */
    private class Applicant(
        val name: String,
        val birthDate: LocalDate,
        val email: String,
        val phone: String,
        val gender: String?,
        val residence: String?,
        val request: TrainingApplicationRequest,
    ) {
        fun toExternal(
            clientApplicationId: UUID,
            memberPublicId: UUID,
            courseId: Int,
        ) = ExternalCreateApplicationRequest(
            clientApplicationId = clientApplicationId,
            clientUserId = memberPublicId.toString(),
            courseId = courseId,
            aName = name,
            aBirthdate = birthDate.toString(),
            aEmail = email,
            aHandy = phone,
            aGender = gender,
            aBaptized = request.baptized.orIfBlank(null),
            aBaptizeType = request.baptizeType.orIfBlank(null),
            aResidence = residence,
            aGroup = ApplicationFormFields.YOUTH_GROUP_CODE,
            aGyogu = request.gyogu.orIfBlank(null),
            aSoon = request.soon.orIfBlank(null),
            aChildren = request.children.orIfBlank(null),
            aHistory = request.history.orIfBlank(null),
            aWaiting = request.waiting.orIfBlank(null),
            aRunning = request.running.orIfBlank(null),
            aComment = request.comment.orIfBlank(null),
        )

        override fun toString(): String = "Applicant(<redacted>)"
    }

    private companion object {
        /** Participations that make a new application to the same training a duplicate. */
        val ACTIVE_STATUSES = setOf(TrainingStatus.APPLIED, TrainingStatus.ENROLLED, TrainingStatus.IN_PROGRESS)

        /** 현재 신청한 양육: signed up, not started yet. */
        val WAITING_STATUSES = setOf(TrainingStatus.APPLIED, TrainingStatus.ENROLLED)

        const val IDEMPOTENCY_CONFLICT = "idempotency_conflict"

        const val EXTERNAL_CANCELLED = "cancelled"

        const val CLOSED_MESSAGE = "신청 기간이 아닙니다."

        const val REQUIRED_MESSAGE = "필수 항목입니다."
    }
}

/** What a training is matched by: its Korean name and its aliases. */
internal fun Training.searchTerms(): TrainingSearchTerms = TrainingSearchTerms(code, listOfNotNull(nameKo) + aliases)

/** Trimmed value, or [fallback] trimmed, or null when both are blank. */
private fun String?.orIfBlank(fallback: String?): String? =
    this?.trim()?.takeIf { it.isNotEmpty() } ?: fallback?.trim()?.takeIf { it.isNotEmpty() }
