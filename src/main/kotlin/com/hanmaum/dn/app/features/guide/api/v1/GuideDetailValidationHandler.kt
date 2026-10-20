package com.hanmaum.dn.app.features.guide.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideValidationErrors
import com.hanmaum.dn.app.features.guide.service.GuideDetailValidationException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import tools.jackson.core.JacksonException

/** Never log malformed payloads: they can contain the free-text notes. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = [GuideDetailsController::class, GuideAreaController::class])
class GuideDetailValidationHandler {
    private fun isArea(request: HttpServletRequest) = request.requestURI.contains("/guide/counting-areas")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun parameter(
        exception: MethodArgumentTypeMismatchException,
        request: HttpServletRequest,
    ): ApiResponse<GuideValidationErrors> = error(mapOf(exception.name to "Invalid parameter format."), request)

    @ExceptionHandler(GuideDetailValidationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun fields(
        exception: GuideDetailValidationException,
        request: HttpServletRequest,
    ): ApiResponse<GuideValidationErrors> = error(exception.fieldErrors, request)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun validation(
        exception: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ApiResponse<GuideValidationErrors> =
        error(
            exception.bindingResult.fieldErrors.associate { it.field to "Invalid or missing field." },
            request,
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun json(
        exception: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ): ApiResponse<GuideValidationErrors> {
        val jackson = generateSequence<Throwable>(exception) { it.cause }.filterIsInstance<JacksonException>().firstOrNull()
        val field =
            buildString {
                jackson?.path?.forEach { reference ->
                    val name = reference.propertyName
                    if (name != null) {
                        if (isNotEmpty()) append('.')
                        append(name)
                    } else if (reference.index >= 0) {
                        append("[${reference.index}]")
                    }
                }
            }.ifBlank { "body" }
        val hint =
            if (isArea(
                    request,
                )
            ) {
                "Invalid or missing counting-area setting."
            } else {
                "Invalid or missing value; counts require nonnegative integer JSON numbers or null."
            }
        return error(mapOf(field to hint), request)
    }

    private fun error(
        fields: Map<String, String>,
        request: HttpServletRequest,
    ) = ApiResponse(
        false,
        if (isArea(request)) "Invalid counting-area settings." else "Invalid attendance details.",
        GuideValidationErrors(fields),
    )
}
