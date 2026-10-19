package com.hanmaum.dn.app.features.guide.api.v1

import com.hanmaum.dn.app.features.guide.service.GuideException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = [WorshipCountController::class])
class GuideExceptionHandler {
    @ExceptionHandler(GuideException::class)
    fun domain(exception: GuideException): ProblemDetail = ProblemDetail.forStatusAndDetail(exception.status, exception.safeDetail)

    @ExceptionHandler(DataIntegrityViolationException::class, ObjectOptimisticLockingFailureException::class)
    fun conflict(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Report changed or the date is already in use; reload and retry.")
}
