package com.hanmaum.dn.app.common.config

import com.hanmaum.dn.app.common.api.ApiErrorCode
import com.hanmaum.dn.app.common.api.ErrorResponse
import com.hanmaum.dn.app.features.members.service.MemberProfileNotFoundException
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import tools.jackson.module.kotlin.KotlinInvalidNullException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(
        EntityNotFoundException::class,
        NoSuchElementException::class,
        NoHandlerFoundException::class,
        NoResourceFoundException::class,
    )
    fun handleNotFound(e: Exception): ResponseEntity<ErrorResponse> {
        logger.warn("Not found: {}", e.message)
        val response =
            ErrorResponse(
                status = HttpStatus.NOT_FOUND.value(),
                error = "Not Found",
                message = "The requested resource could not be found.",
            )
        return ResponseEntity(response, HttpStatus.NOT_FOUND)
    }

    /**
     * Authenticated, but no member row belongs to the caller.
     *
     * Still a 404 — the profile genuinely is not there — but carrying a code, because the
     * status alone reads exactly like "this route does not exist". A client has to tell them
     * apart: one is a bug to report, the other is a person who belongs outside the member
     * area and for whom a retry can never succeed.
     */
    @ExceptionHandler(MemberProfileNotFoundException::class)
    fun handleMemberProfileNotFound(e: MemberProfileNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("No member profile for the authenticated caller")
        val response =
            ErrorResponse(
                status = HttpStatus.NOT_FOUND.value(),
                error = "Not Found",
                message = e.message ?: "Member profile not found.",
                code = ApiErrorCode.MEMBER_PROFILE_NOT_FOUND,
            )
        return ResponseEntity(response, HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val fieldErrors =
            e.bindingResult.fieldErrors
                .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
                .ifBlank { "Validation failed" }
        logger.warn("Validation failed: {}", fieldErrors)
        val response =
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Bad Request",
                message = fieldErrors,
            )
        return ResponseEntity(response, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        logger.warn("Malformed request body: {}", e.message)
        val missingField = findKotlinInvalidNull(e)?.kotlinPropertyName
        val message = if (missingField != null) "$missingField: 필수 항목입니다." else "Malformed or missing request body."
        val response =
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Bad Request",
                message = message,
            )
        return ResponseEntity(response, HttpStatus.BAD_REQUEST)
    }

    private tailrec fun findKotlinInvalidNull(t: Throwable?): KotlinInvalidNullException? =
        when (t) {
            null -> null
            is KotlinInvalidNullException -> t
            else -> findKotlinInvalidNull(t.cause)
        }

    /**
     * A query or path parameter that Spring could not convert to its declared type — a
     * date written as "yesterday", a UUID that is not one. Without this the generic
     * handler below turns a client mistake into a 500.
     *
     * The parameter name is echoed; its value is not, since it is unvalidated input that
     * would land verbatim in the response and the log.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        logger.warn("Unparseable request parameter: name={} expectedType={}", e.name, e.requiredType?.simpleName)
        val response =
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Bad Request",
                message = "${e.name}: 형식이 올바르지 않습니다.",
            )
        return ResponseEntity(response, HttpStatus.BAD_REQUEST)
    }

    /**
     * A known path called with a method it does not offer — a DELETE on an insert-only
     * collection, say. Without this the generic handler below turns a client mistake into a
     * 500, which reads like a server fault and hides the actual problem.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(e: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> {
        logger.warn("Method not allowed: method={} supported={}", e.method, e.supportedMethods?.joinToString(","))
        val response =
            ErrorResponse(
                status = HttpStatus.METHOD_NOT_ALLOWED.value(),
                error = "Method Not Allowed",
                message = "This endpoint does not support ${e.method}.",
            )
        return ResponseEntity(response, HttpStatus.METHOD_NOT_ALLOWED)
    }

    @ExceptionHandler(AuthorizationDeniedException::class)
    fun handleAuthorizationDenied(e: AuthorizationDeniedException): ResponseEntity<ErrorResponse> {
        logger.warn("Authorization denied: {}", e.message)
        val response =
            ErrorResponse(
                status = HttpStatus.FORBIDDEN.value(),
                error = "Forbidden",
                message = "Access denied.",
            )
        return ResponseEntity(response, HttpStatus.FORBIDDEN)
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(e: ResponseStatusException): ResponseEntity<ErrorResponse> {
        logger.warn("Request rejected: status={}", e.statusCode.value())
        val response =
            ErrorResponse(
                status = e.statusCode.value(),
                error = HttpStatus.resolve(e.statusCode.value())?.reasonPhrase ?: "Error",
                message = e.reason ?: e.message,
            )
        return ResponseEntity(response, e.statusCode)
    }

    @ExceptionHandler(Exception::class)
    fun handleGlobalException(e: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Critical internal error", e)
        val response =
            ErrorResponse(
                status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                error = "Internal Server Error",
                message = "An unexpected error occurred. Please contact support.",
            )
        return ResponseEntity(response, HttpStatus.INTERNAL_SERVER_ERROR)
    }
}
