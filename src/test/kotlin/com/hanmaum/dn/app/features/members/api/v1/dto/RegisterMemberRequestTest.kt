package com.hanmaum.dn.app.features.members.api.v1.dto

import com.hanmaum.dn.app.features.members.domain.IdentityStatus
import jakarta.validation.Validation
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegisterMemberRequestTest {
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `email is trimmed during deserialization`() {
        val json = """{"firstName":"A","lastName":"B","password":"secret","email":"  foo@bar.com  "}"""

        val request = mapper.readValue(json, RegisterMemberRequest::class.java)

        assertEquals("foo@bar.com", request.email)
    }

    @Test
    fun `password keeps surrounding whitespace`() {
        val json = """{"firstName":"A","lastName":"B","password":" secret ","email":"foo@bar.com"}"""

        val request = mapper.readValue(json, RegisterMemberRequest::class.java)

        assertEquals(" secret ", request.password)
    }

    @Test
    fun `work or school is trimmed during deserialization`() {
        val json = """{"firstName":"A","lastName":"B","password":"secret","email":"foo@bar.com","workOrSchool":"  TU Berlin  "}"""

        val request = mapper.readValue(json, RegisterMemberRequest::class.java)

        assertEquals("TU Berlin", request.workOrSchool)
    }

    @Test
    fun `birth date and identity status are required`() {
        val violations = validator.validate(request(birthDate = null, identityStatus = null))

        assertEquals(setOf("birthDate", "identityStatus"), violations.map { it.propertyPath.toString() }.toSet())
    }

    @Test
    fun `work or school is optional`() {
        assertTrue(validator.validate(request(workOrSchool = null)).isEmpty())
    }

    @Test
    fun `work or school is capped at 100 characters`() {
        val violations = validator.validate(request(workOrSchool = "x".repeat(101)))

        assertEquals(listOf("workOrSchool"), violations.map { it.propertyPath.toString() })
    }

    private fun request(
        birthDate: LocalDate? = LocalDate.of(1995, 3, 14),
        identityStatus: IdentityStatus? = IdentityStatus.EMPLOYEE,
        workOrSchool: String? = "Siemens",
    ) = RegisterMemberRequest(
        firstName = "A",
        lastName = "B",
        password = "secret",
        email = "foo@bar.com",
        birthDate = birthDate,
        identityStatus = identityStatus,
        workOrSchool = workOrSchool,
    )
}
