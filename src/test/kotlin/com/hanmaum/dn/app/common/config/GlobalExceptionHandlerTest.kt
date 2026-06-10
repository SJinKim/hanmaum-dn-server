package com.hanmaum.dn.app.common.config

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun `handleResponseStatus preserves status and reason`() {
        val response = handler.handleResponseStatus(ResponseStatusException(HttpStatus.CONFLICT, "Email already exists"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)

        val body = assertNotNull(response.body)
        assertEquals(HttpStatus.CONFLICT.value(), body.status)
        assertEquals("Conflict", body.error)
        assertEquals("Email already exists", body.message)
    }
}
