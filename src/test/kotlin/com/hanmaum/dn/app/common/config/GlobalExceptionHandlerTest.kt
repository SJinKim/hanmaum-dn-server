package com.hanmaum.dn.app.common.config

import com.hanmaum.dn.app.common.api.ApiErrorCode
import com.hanmaum.dn.app.features.members.service.MemberProfileNotFoundException
import com.hanmaum.dn.app.features.members.service.ProfileImageInvalidException
import com.hanmaum.dn.app.features.members.service.ProfileImageStorageUnavailableException
import com.hanmaum.dn.app.features.members.service.ProfileImageTooLargeException
import com.hanmaum.dn.app.features.members.service.ProfileImageUnsupportedTypeException
import jakarta.persistence.EntityNotFoundException
import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
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
import kotlin.test.assertNull

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

    @Test
    fun `handleMethodNotSupported maps a wrong verb to 405, not 500`() {
        // Same class of bug as the unknown-route 404 above: without an explicit handler the
        // generic one turns a client mistake into a server fault. It matters wherever a
        // missing verb is the contract — an insert-only collection offering no DELETE.
        val exception = HttpRequestMethodNotSupportedException("DELETE", listOf("GET", "POST"))

        val response = handler.handleMethodNotSupported(exception)

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED.value(), body.status)
        assertEquals("Method Not Allowed", body.error)
    }

    @Test
    fun `handleMemberProfileNotFound carries a code the client can branch on`() {
        val response = handler.handleMemberProfileNotFound(MemberProfileNotFoundException())

        // Still a 404 — the profile genuinely is not there — but a bare one reads exactly
        // like "this route does not exist". Those are different problems: one is a bug to
        // report, the other a person who belongs outside the member area, for whom a retry
        // can never succeed. Looking for a deployed-but-invisible endpoint cost an afternoon.
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(ApiErrorCode.MEMBER_PROFILE_NOT_FOUND, body.code)
    }

    @Test
    fun `an ordinary not-found carries no code`() {
        val response = handler.handleNotFound(EntityNotFoundException("nothing here"))

        // The code is opt-in. A client branching on it must not be handed one for every 404.
        assertNull(assertNotNull(response.body).code)
    }

    @Test
    fun `profile image errors map to their status and code`() {
        val cases =
            listOf(
                handler.handleProfileImageTooLarge(ProfileImageTooLargeException()) to
                    (HttpStatus.CONTENT_TOO_LARGE to ApiErrorCode.PROFILE_IMAGE_TOO_LARGE),
                handler.handleProfileImageUnsupportedType(ProfileImageUnsupportedTypeException()) to
                    (HttpStatus.UNSUPPORTED_MEDIA_TYPE to ApiErrorCode.PROFILE_IMAGE_UNSUPPORTED_TYPE),
                handler.handleProfileImageInvalid(ProfileImageInvalidException()) to
                    (HttpStatus.UNPROCESSABLE_CONTENT to ApiErrorCode.PROFILE_IMAGE_INVALID),
                handler.handleProfileImageStorageUnavailable(ProfileImageStorageUnavailableException(RuntimeException("down"))) to
                    (HttpStatus.SERVICE_UNAVAILABLE to ApiErrorCode.PROFILE_IMAGE_STORAGE_UNAVAILABLE),
            )

        cases.forEach { (response, expected) ->
            assertEquals(expected.first, response.statusCode)
            assertEquals(expected.second, assertNotNull(response.body).code)
        }
    }

    @Test
    fun `the multipart size limit reads like the processor's own limit`() {
        val response = handler.handleProfileImageTooLarge(MaxUploadSizeExceededException(5L * 1024 * 1024))

        // Tomcat rejects an oversized upload before the processor sees it; the client must
        // not be able to tell which of the two caught it.
        assertEquals(HttpStatus.CONTENT_TOO_LARGE, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(ApiErrorCode.PROFILE_IMAGE_TOO_LARGE, body.code)
        assertEquals(ProfileImageTooLargeException().message, body.message)
    }

    @Test
    fun `a storage outage never leaks the SDK message`() {
        val response =
            handler.handleProfileImageStorageUnavailable(
                ProfileImageStorageUnavailableException(RuntimeException("bucket hdn-st at fsn1 refused key AKIA")),
            )

        assertEquals(false, assertNotNull(response.body).message.contains("AKIA"))
    }
}
