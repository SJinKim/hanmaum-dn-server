package com.hanmaum.dn.app.features.training.service

import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.training.api.toDetailDto
import com.hanmaum.dn.app.features.training.api.toDto
import com.hanmaum.dn.app.features.training.api.toRegistrationDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingCatalogDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingCohortDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDetailDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingRegistrationDto
import com.hanmaum.dn.app.features.training.domain.Training
import com.hanmaum.dn.app.features.training.domain.TrainingStatus
import com.hanmaum.dn.app.features.training.domain.UserTraining
import com.hanmaum.dn.app.features.training.repository.TrainingCohortRepository
import com.hanmaum.dn.app.features.training.repository.TrainingRepository
import com.hanmaum.dn.app.features.training.repository.UserTrainingRepository
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.ErrorResponseException
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@Service
class TrainingService(
    private val trainingRepository: TrainingRepository,
    private val cohortRepository: TrainingCohortRepository,
    private val userTrainingRepository: UserTrainingRepository,
    private val memberRepository: MemberRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(TrainingService::class.java)

    /**
     * The training catalog, ordered by progression (sort order).
     *
     * [activeOnly] defaults to false so the admin member-edit form keeps seeing the
     * discontinued Kairos courses it has always seen; the 양육 list passes true.
     */
    @Transactional(readOnly = true)
    fun getTrainings(activeOnly: Boolean = false): List<TrainingDto> = findCatalogEntries(activeOnly).map { it.toDto() }

    /**
     * The catalog with the fields the admin grid needs for its columns.
     *
     * [activeOnly] exists because KAIROS and KAIROS_FT are discontinued and kept only so
     * archived records have a valid course to point at. Selection lists must not offer
     * them; the grid still shows them where data exists.
     */
    @Transactional(readOnly = true)
    fun getCatalog(activeOnly: Boolean): List<TrainingCatalogDto> =
        findCatalogEntries(activeOnly).map { training ->
            TrainingCatalogDto(
                publicId = training.publicId.toString(),
                code = training.code.name,
                name = training.name,
                nameKo = training.nameKo,
                category = training.category?.name,
                sortOrder = training.sortOrder,
                hasCohorts = training.hasCohorts,
                isActive = training.isActive,
                prerequisiteCode = training.prerequisite?.code?.name,
            )
        }

    /** Everything the 양육 detail page renders, including the seat counter. */
    @Transactional(readOnly = true)
    fun getTraining(publicId: UUID): TrainingDetailDto {
        val training = requireTraining(publicId)
        return training.toDetailDto(countRegistered(training))
    }

    /**
     * Signs the authenticated member up for a course — the 신청하기 button.
     *
     * Unlike `POST /members/{publicId}/trainings`, which is an admin assignment, this
     * writes exactly one row for the caller and never for anyone else. The row starts at
     * [TrainingStatus.APPLIED]; moving it to ENROLLED and onwards stays an admin action.
     *
     * The capacity check is not serialized against concurrent callers: two members
     * applying for the last seat in the same instant can both pass it and overfill the
     * course by one. Locking the catalog row on every application is not worth it for
     * twelve-seat courses that a leader can rebalance by hand; the duplicate check, which
     * would corrupt data rather than inconvenience someone, is backed by a unique index.
     */
    @Transactional
    fun registerCurrentMember(
        publicId: UUID,
        keycloakSub: String,
    ): TrainingRegistrationDto {
        val member =
            memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakSub)
                ?: throw EntityNotFoundException("Member not found for authenticated subject")
        val training = requireTraining(publicId)
        val today = LocalDate.now(clock)

        if (!training.openForRegistration) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 신청을 받고 있지 않은 과정입니다.")
        }
        // No explicit deadline means applications close when the course starts.
        val deadline = training.registrationDeadline ?: training.startDate
        if (deadline != null && today.isAfter(deadline)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "신청 마감일이 지났습니다.")
        }
        // Matches uq_user_training_member_training_variant exactly (variant IS NULL for a
        // self-registration), so a duplicate is reported as a conflict rather than
        // surfacing as a constraint violation.
        val existing =
            userTrainingRepository.findByMemberIdAndTrainingIdAndVariantAndDeletedAtIsNull(
                memberId = member.id!!,
                trainingId = training.id!!,
                variant = null,
            )
        if (existing.isPresent) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 신청한 과정입니다.")
        }
        val registeredBefore = countRegistered(training)
        val capacity = training.capacity
        if (capacity != null && registeredBefore >= capacity) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "정원이 모두 찼습니다.")
        }

        val saved =
            try {
                userTrainingRepository.saveAndFlush(
                    UserTraining(
                        member = member,
                        training = training,
                        status = TrainingStatus.APPLIED,
                        appliedOn = today,
                    ),
                )
            } catch (e: DataIntegrityViolationException) {
                // uq_user_training_member_training_variant: the member got a row in
                // between the check above and this insert. Same outcome, same message.
                throw ResponseStatusException(HttpStatus.CONFLICT, "이미 신청한 과정입니다.", e)
            }
        log.info("Registered member for training memberId={} trainingCode={}", member.id, training.code)
        return saved.toRegistrationDto(registeredBefore + 1)
    }

    @Transactional(readOnly = true)
    fun getCohorts(trainingPublicId: UUID): List<TrainingCohortDto> {
        val training = requireTraining(trainingPublicId)
        return cohortRepository
            .findAllByTrainingIdAndDeletedAtIsNullOrderBySeriesAscOrdinalAsc(training.id!!)
            .map { cohort ->
                TrainingCohortDto(
                    publicId = cohort.publicId.toString(),
                    series = cohort.series.name,
                    ordinal = cohort.ordinal,
                    label = cohort.label,
                    cohortYear = cohort.cohortYear,
                    term = cohort.term?.name,
                    startedOn = cohort.startedOn,
                    endedOn = cohort.endedOn,
                )
            }
    }

    private fun findCatalogEntries(activeOnly: Boolean): List<Training> =
        if (activeOnly) {
            trainingRepository.findAllByIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAsc()
        } else {
            trainingRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc()
        }

    private fun requireTraining(publicId: UUID): Training =
        trainingRepository.findByPublicIdAndDeletedAtIsNull(publicId).orElseThrow {
            ErrorResponseException(
                HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No training with that id exists."),
                null,
            )
        }

    private fun countRegistered(training: Training): Int =
        userTrainingRepository.countByTrainingIdAndStatusInAndDeletedAtIsNull(training.id!!, REGISTERED_STATUSES)

    companion object {
        /**
         * The statuses that occupy a seat in the run a course is currently offering.
         *
         * Historical participation an admin records lands on COMPLETED, DROPPED,
         * IN_PROGRESS or UNKNOWN. Counting those would show "436 / 12명" on a course that
         * has been taught for years, so the seat counter only sees people who signed up
         * for the run that is open now.
         *
         * The trade-off: rows left at ENROLLED after a run finishes keep counting until
         * an admin moves them on. Acceptable while one course has one open run — a course
         * running two concurrent intakes needs the offering on its own table.
         */
        val REGISTERED_STATUSES = setOf(TrainingStatus.APPLIED, TrainingStatus.ENROLLED)
    }
}
