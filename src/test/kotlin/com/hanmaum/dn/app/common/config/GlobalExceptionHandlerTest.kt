package com.hanmaum.dn.app.common.config

import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.resource.NoResourceFoundException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    private object EmptyHttpInputMessage : HttpInputMessage {
        override fun getBody(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun getHeaders(): HttpHeaders = HttpHeaders.EMPTY
    }

    private data class SampleRequest(
        val firstName: String,
        val lastName: String,
    )

    @Suppress("UNUSED_PARAMETER")
    private fun sampleHandlerMethod(from: LocalDate) = Unit

    @Test
    fun `handleTypeMismatch reports a 400 naming the parameter but never its value`() {
        val parameter = MethodParameter(javaClass.getDeclaredMethod("sampleHandlerMethod", LocalDate::class.java), 0)
        val exception = MethodArgumentTypeMismatchException("yesterday", LocalDate::class.java, "from", parameter, null)

        val response = handler.handleTypeMismatch(exception)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals("from: 형식이 올바르지 않습니다.", body.message)
        assertEquals(false, body.message.contains("yesterday"))
    }

    @Test
    fun `handleResponseStatus preserves status and reason`() {
        val response = handler.handleResponseStatus(ResponseStatusException(HttpStatus.CONFLICT, "Email already exists"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)

        val body = assertNotNull(response.body)
        assertEquals(HttpStatus.CONFLICT.value(), body.status)
        assertEquals("Conflict", body.error)
        assertEquals("Email already exists", body.message)
    }

    @Test
    fun `handleMessageNotReadable names the missing required field`() {
        val cause =
            runCatching { mapper.readValue("""{"lastName":"User"}""", SampleRequest::class.java) }
                .exceptionOrNull()
        assertNotNull(cause)

        val response = handler.handleMessageNotReadable(HttpMessageNotReadableException("boom", cause, EmptyHttpInputMessage))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals("firstName: 필수 항목입니다.", body.message)
    }

    @Test
    fun `handleMessageNotReadable falls back to a generic message for unrelated parse errors`() {
        val response =
            handler.handleMessageNotReadable(
                HttpMessageNotReadableException("boom", RuntimeException("not json"), EmptyHttpInputMessage),
            )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals("Malformed or missing request body.", body.message)
    }

    @Test
    fun `handleNotFound maps a missing static resource to 404`() {
        val exception =
            NoResourceFoundException(
                org.springframework.http.HttpMethod.GET,
                "api/v1/does-not-exist",
                "classpath:/static/",
            )

        val response = handler.handleNotFound(exception)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(HttpStatus.NOT_FOUND.value(), body.status)
        assertEquals("Not Found", body.error)
    }
}
