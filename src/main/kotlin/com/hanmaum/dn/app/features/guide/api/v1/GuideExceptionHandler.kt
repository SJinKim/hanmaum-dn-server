package com.hanmaum.dn.app.features.guide.api.v1

import com.hanmaum.dn.app.features.guide.service.GuideException
import org.hibernate.exception.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.sql.SQLException

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = [WorshipCountController::class, GuideDetailsController::class])
class GuideExceptionHandler {
    @ExceptionHandler(GuideException::class)
    fun domain(exception: GuideException): ProblemDetail = ProblemDetail.forStatusAndDetail(exception.status, exception.safeDetail)

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun conflict(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Report changed or the date is already in use; reload and retry.")

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun integrity(exception: DataIntegrityViolationException): ProblemDetail {
        val causes = generateSequence<Throwable>(exception) { it.cause }.toList()
        val constraint = causes.filterIsInstance<ConstraintViolationException>().firstOrNull()
        val sqlState = causes.filterIsInstance<SQLException>().firstOrNull()?.sqlState ?: "unknown"
        if (constraint?.constraintName == "guide_worship_reports_active_date" && sqlState == "23505") return conflict()
        // Never log the exception message/stack: PostgreSQL details can contain notes or actor values.
        LoggerFactory.getLogger(GuideExceptionHandler::class.java).error(
            "Guide report persistence failed operation=write sqlState={}",
            sqlState,
        )
        // Returning a safe 500 here also avoids the generic handler logging PostgreSQL row details.
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Guide report could not be saved.")
    }
}
