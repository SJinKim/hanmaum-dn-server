package com.hanmaum.dn.app.common.config

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.io.ByteArrayInputStream
import java.io.InputStream
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
}
