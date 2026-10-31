package com.hanmaum.dn.app.features.attendance.api.v1

import com.hanmaum.dn.app.common.security.securityProblemDetail
import com.hanmaum.dn.app.features.attendance.service.LongAbsenceException
import org.hibernate.exception.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.sql.SQLException

/**
 * Error answers for [LongAbsenceController] only, ahead of the global handler.
 *
 * Request bodies here carry memos that may name an illness or a family matter. The global
 * handler logs the message of an unreadable body and the stack trace of anything unexpected,
 * both of which can quote the memo, so every error of this controller ends here and is logged
 * as a fixed text plus status or SQL state. Nothing from the request or the exception goes into
 * a log line or a response.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = [LongAbsenceController::class])
class LongAbsenceExceptionHandler {
    private val log = LoggerFactory.getLogger(LongAbsenceExceptionHandler::class.java)

    @ExceptionHandler(LongAbsenceException::class)
    fun domain(exception: LongAbsenceException): ResponseEntity<ProblemDetail> {
        log.warn("Long-absence request rejected status={} code={}", exception.status.value(), exception.code)
        return problem(exception.status, exception.code, exception.safeDetail)
    }

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentNotValidException::class,
        HandlerMethodValidationException::class,
        MethodArgumentTypeMismatchException::class,
    )
    fun invalid(exception: Exception): ResponseEntity<ProblemDetail> {
        log.warn("Long-absence request invalid reason={}", exception.javaClass.simpleName)
        return problem(
            HttpStatus.BAD_REQUEST,
            LongAbsenceException.INVALID_REQUEST,
            "Invalid request. Ids must be UUIDs, page >= 0 and size 1 to 100; writes need lastSundayDate and version, " +
                "a memo write the memo key; memo is at most 2000 characters.",
        )
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun stale(): ResponseEntity<ProblemDetail> = domain(LongAbsenceException.versionConflict())

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun integrity(exception: DataIntegrityViolationException): ResponseEntity<ProblemDetail> {
        val causes = generateSequence<Throwable>(exception) { it.cause }.toList()
        val constraint = causes.filterIsInstance<ConstraintViolationException>().firstOrNull()?.constraintName
        if (constraint in RACE_CONSTRAINTS) return stale()
        return failure(sqlState(causes))
    }

    /** Method security runs inside the controller call; its denial stays a 403. */
    @ExceptionHandler(AuthorizationDeniedException::class)
    fun denied(): ResponseEntity<ProblemDetail> {
        log.warn("Long-absence request denied status=403")
        return ResponseEntity(securityProblemDetail(HttpStatus.FORBIDDEN), HttpStatus.FORBIDDEN)
    }

    /** A token without a usable subject cannot be an actor of a write. */
    @ExceptionHandler(InvalidActorSubjectException::class)
    fun unauthenticated(): ResponseEntity<ProblemDetail> {
        log.warn("Long-absence request unauthenticated status=401")
        return ResponseEntity(securityProblemDetail(HttpStatus.UNAUTHORIZED), HttpStatus.UNAUTHORIZED)
    }

    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception): ResponseEntity<ProblemDetail> =
        failure(sqlState(generateSequence<Throwable>(exception) { it.cause }.toList()), exception.javaClass.simpleName)

    private fun failure(
        sqlState: String,
        type: String = "DataIntegrityViolationException",
    ): ResponseEntity<ProblemDetail> {
        // Never the exception message or stack: PostgreSQL details and bind values can quote the memo.
        log.error("Long-absence request failed type={} sqlState={}", type, sqlState)
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, null, "The request could not be completed.")
    }

    private fun sqlState(causes: List<Throwable>): String = causes.filterIsInstance<SQLException>().firstOrNull()?.sqlState ?: "none"

    private fun problem(
        status: HttpStatus,
        code: String?,
        detail: String,
    ): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(status, detail)
        problem.title = status.reasonPhrase
        code?.let { problem.setProperty("code", it) }
        return ResponseEntity(problem, status)
    }

    private companion object {
        /** A parallel first write of the same phase or revision; the member lock should prevent both. */
        val RACE_CONSTRAINTS =
            setOf("uq_attendance_absence_followup_phase_generation", "uq_attendance_absence_change_revision")
    }
}
