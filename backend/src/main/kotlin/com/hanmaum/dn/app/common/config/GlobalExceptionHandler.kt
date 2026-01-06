package com.hanmaum.dn.app.common.config

import com.hanmaum.dn.app.common.api.ErrorResponse
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.NoHandlerFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    // Ressource nicht gefunden: "Resource not found", aber kein warum oder welche tabelle
    @ExceptionHandler(EntityNotFoundException::class, NoSuchElementException::class, NoHandlerFoundException::class)
    fun handleNotFound(e: Exception): ResponseEntity<ErrorResponse> {
        // intern echten Fehler loggen:
        logger.warn("Not found requested: ${e.message}")

        val response = ErrorResponse(
            status = HttpStatus.NOT_FOUND.value(),
            error = "Not Found",
            message = "The requested resource could not be found."
        )
        return ResponseEntity(response, HttpStatus.NOT_FOUND)
    }

    // Datenbank-Fehler, NullPointer: verstecke ALLES, d.h: User sieht nur "Internal Server Error"
    @ExceptionHandler(Exception::class)
    fun hanleGlobalExceptionHandler(e: Exception): ResponseEntity<ErrorResponse> {
        // echten Fehler (Stacktrace) nur ins Server-Log schreiben
        logger.error("Critical internal error", e)

        val response = ErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = "Internal Server Error",
            message = "An unexpected error occurred. Please contact support."
        )
        return ResponseEntity(response, HttpStatus.INTERNAL_SERVER_ERROR)
    }
}