package com.hanmaum.dn.app.features.courseapplication.client

import com.hanmaum.dn.app.features.courseapplication.config.CourseApplicationApiProperties
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpCourseApplicationApiClientTest {
    private val baseUrl = "https://application.test/api/v1"

    // Named rather than inline, as in HttpBibleApiClientTest: the secret-scan hook flags a
    // credential-shaped field assigned a quoted string.
    private val bearerFixture = "test-bearer-value"

    private val properties = CourseApplicationApiProperties(baseUrl = baseUrl, clientApiKey = bearerFixture)

    private fun client(props: CourseApplicationApiProperties = properties): Pair<HttpCourseApplicationApiClient, MockRestServiceServer> {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        return HttpCourseApplicationApiClient(props, builder) to server
    }

    private val coursesBody =
        """
        {"data":[
          {"id":3,"name":"일대일 제자양육","dateText":"상시 접수","capacity":0,
           "registrationStartsAt":"0000-00-00 00:00:00","registrationEndsAt":"2099-03-12 23:59:59",
           "requiredOptionalFields":["aBaptized","aHistory"],"excludedFields":[],"targetGroups":[4,5],
           "applicationFields":[
             {"name":"aGyogu","type":"enum","required":false,"label":"교구/순","dependsOn":"aGroup",
              "requiredWhen":{"aGroup":["4","5"]},"options":[{"value":"99","label":"새가족","groups":["4","5"]}]}
           ],
           "someFieldAddedLater":{"nested":true}}
        ]}
        """.trimIndent()

    private val request =
        ExternalCreateApplicationRequest(
            clientApplicationId = UUID.fromString("0b71c08b-9cda-4a33-849e-0a3ce12f7329"),
            clientUserId = "member-public-id",
            courseId = 3,
            aName = "홍길동",
            aBirthdate = "1990-01-02",
            aEmail = "person@example.com",
            aHandy = "+49 170 0000000",
            aGender = "M",
        )

    private val applicationBody =
        """{"data":{"id":123,"courseId":3,"status":"active","createdAt":"2026-09-14T12:00:00+02:00"},
           "meta":{"idempotentReplay":true,"emailSent":false}}"""

    @Test
    fun `the course list is read with the bearer key and tolerates fields it does not know`() {
        val (client, server) = client()
        server
            .expect(requestTo("$baseUrl/courses"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer $bearerFixture"))
            .andRespond(withSuccess(coursesBody, MediaType.APPLICATION_JSON))

        val courses = client.listCourses()

        server.verify()
        assertEquals(1, courses.size)
        assertEquals("일대일 제자양육", courses[0].name)
        assertEquals("2099-03-12 23:59:59", courses[0].registrationEndsAt)
        assertEquals(listOf(4, 5), courses[0].targetGroups)
        assertEquals(mapOf("aGroup" to listOf("4", "5")), courses[0].applicationFields?.single()?.requiredWhen)
    }

    @Test
    fun `the course list is fetched once within the cache window`() {
        val (client, server) = client()
        server
            .expect(ExpectedCount.once(), requestTo("$baseUrl/courses"))
            .andRespond(withSuccess(coursesBody, MediaType.APPLICATION_JSON))

        repeat(3) { client.listCourses() }

        server.verify()
    }

    @Test
    fun `an application is posted with the external wire names and the replay flag is read`() {
        val (client, server) = client()
        server
            .expect(requestTo("$baseUrl/applications"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.clientApplicationId").value("0b71c08b-9cda-4a33-849e-0a3ce12f7329"))
            .andExpect(jsonPath("$.aName").value("홍길동"))
            .andExpect(jsonPath("$.aBirthdate").value("1990-01-02"))
            .andExpect(jsonPath("$.aHandy").value("+49 170 0000000"))
            .andExpect(jsonPath("$.aname").doesNotExist())
            // Unset optional fields are left out rather than sent as null.
            .andExpect(jsonPath("$.aComment").doesNotExist())
            .andRespond(withSuccess(applicationBody, MediaType.APPLICATION_JSON))

        val created = client.createApplication(request)

        server.verify()
        assertEquals(123L, created.application.id)
        assertTrue(created.idempotentReplay)
    }

    @Test
    fun `a business rejection keeps the external code and message`() {
        val (client, server) = client()
        server
            .expect(requestTo("$baseUrl/applications"))
            .andRespond(
                withStatus(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"error":{"code":"capacity_full","message":"정원이 마감되었습니다.","details":{}}}"""),
            )

        val e = assertThrows<CourseApplicationApiRejectedException> { client.createApplication(request) }

        assertEquals(409, e.httpStatus)
        assertEquals("capacity_full", e.code)
        assertEquals("정원이 마감되었습니다.", e.message)
    }

    @Test
    fun `validation details are carried per field`() {
        val (client, server) = client()
        server
            .expect(requestTo("$baseUrl/applications"))
            .andRespond(
                withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        """{"error":{"code":"validation_failed","message":"입력값을 확인해주세요.",
                           "details":{"aEmail":"올바른 이메일 주소여야 합니다."}}}""",
                    ),
            )

        val e = assertThrows<CourseApplicationApiRejectedException> { client.createApplication(request) }

        assertEquals(mapOf("aEmail" to "올바른 이메일 주소여야 합니다."), e.fieldErrors)
    }

    @Test
    fun `a server error, an HTML error page and a rejected key are all unavailability`() {
        listOf(
            withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"error":{"code":"database_unavailable","message":"x"}}"""),
            withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML).body("<html>bad gateway</html>"),
            withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"error":{"code":"invalid_api_key","message":"x"}}"""),
        ).forEach { response ->
            val (client, server) = client()
            server.expect(requestTo("$baseUrl/courses")).andRespond(response)

            assertThrows<CourseApplicationApiUnavailableException> { client.listCourses() }
            server.verify()
        }
    }

    @Test
    fun `a failed course list is not cached`() {
        val (client, server) = client()
        server
            .expect(ExpectedCount.once(), requestTo("$baseUrl/courses"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))
        server
            .expect(ExpectedCount.once(), requestTo("$baseUrl/courses"))
            .andRespond(withSuccess(coursesBody, MediaType.APPLICATION_JSON))

        assertThrows<CourseApplicationApiUnavailableException> { client.listCourses() }
        assertEquals(1, client.listCourses().size)
        server.verify()
    }

    @Test
    fun `an unknown client application id is null, not an error`() {
        val (client, server) = client()
        val id = UUID.randomUUID()
        server
            .expect(requestTo("$baseUrl/applications/by-client-id/$id"))
            .andRespond(
                withStatus(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"error":{"code":"application_not_found","message":"x"}}"""),
            )

        assertNull(client.findApplicationByClientId(id))
        server.verify()
    }

    @Test
    fun `an unconfigured key never reaches the network`() {
        val (client, server) = client(properties.copy(clientApiKey = ""))

        assertThrows<CourseApplicationApiUnavailableException> { client.listCourses() }
        server.verify()
        assertFalse(properties.copy(clientApiKey = "").isConfigured())
    }
}
