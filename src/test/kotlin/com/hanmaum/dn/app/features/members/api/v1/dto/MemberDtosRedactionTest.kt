package com.hanmaum.dn.app.features.members.api.v1.dto

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards against PII leaking through accidental toString() logging (e.g. `log.info("{}", dto)`).
 * @Redacted/@Unredacted are applied in MemberDtos.kt; these tests fail loudly if that
 * annotation is ever removed or a new PII field is added without redacting it.
 */
class MemberDtosRedactionTest {
    private val email = "jane.doe@example.com"
    private val phone = "010-1234-5678"
    private val name = "Doe"
    private val registrationDate = LocalDate.of(2024, 3, 17)

    @Test
    fun `MemberDto toString redacts PII but keeps non-PII fields`() {
        val dto =
            MemberDto(
                publicId = "pub-1",
                lastName = name,
                firstName = "Jane",
                phoneNumber = phone,
                email = email,
                street = "Main St",
                memberStatus = "ACTIVE",
                groupPublicId = "group-1",
                groupName = "Group A",
            )

        val output = dto.toString()

        assertFalse(output.contains(email), "email leaked into toString(): $output")
        assertFalse(output.contains(phone), "phone leaked into toString(): $output")
        assertFalse(output.contains(name), "lastName leaked into toString(): $output")
        assertTrue(output.contains("pub-1"), "non-PII publicId should still be visible: $output")
        assertTrue(output.contains("ACTIVE"), "non-PII memberStatus should still be visible: $output")
    }

    @Test
    fun `MemberSummaryDto toString redacts PII but keeps non-PII fields`() {
        val dto =
            MemberSummaryDto(
                publicId = "pub-2",
                lastName = name,
                firstName = "Jane",
                email = email,
                memberStatus = "ACTIVE",
            )

        val output = dto.toString()

        assertFalse(output.contains(email), "email leaked into toString(): $output")
        assertFalse(output.contains(name), "lastName leaked into toString(): $output")
        assertTrue(output.contains("pub-2"), "non-PII publicId should still be visible: $output")
    }

    @Test
    fun `MemberResponse toString redacts PII but keeps non-PII fields`() {
        val response =
            MemberResponse(
                publicId = "pub-3",
                firstName = "Jane",
                lastName = name,
                email = email,
                status = MemberStatus.ACTIVE,
                phoneNumber = phone,
                registrationDate = registrationDate,
            )

        val output = response.toString()

        assertFalse(output.contains(email), "email leaked into toString(): $output")
        assertFalse(output.contains(phone), "phone leaked into toString(): $output")
        assertFalse(
            output.contains(registrationDate.toString()),
            "registrationDate leaked into toString(): $output",
        )
        assertTrue(output.contains("pub-3"), "non-PII publicId should still be visible: $output")
        assertTrue(output.contains("ACTIVE"), "non-PII status should still be visible: $output")
    }

    @Test
    fun `MemberResponse toString keeps ministry names, which are org units and not PII`() {
        val response =
            MemberResponse(
                publicId = "pub-4",
                firstName = "Jane",
                lastName = name,
                status = MemberStatus.ACTIVE,
                email = email,
                activeMinistries = listOf("찬양팀"),
            )

        val output = response.toString()

        // Deliberately @Unredacted: a ministry is an org unit like groupName, so it stays
        // readable in a log. The member's own identity in the same line is still redacted.
        assertTrue(output.contains("찬양팀"), "ministry name should stay visible: $output")
        assertFalse(output.contains(email), "email leaked into toString(): $output")
        assertFalse(output.contains(name), "lastName leaked into toString(): $output")
    }

    @Test
    fun `CreateMemberRequest toString redacts PII`() {
        val request = CreateMemberRequest(lastName = name, firstName = "Jane", email = email, phoneNumber = phone)

        val output = request.toString()

        assertFalse(output.contains(email), "email leaked into toString(): $output")
        assertFalse(output.contains(phone), "phone leaked into toString(): $output")
        assertFalse(output.contains(name), "lastName leaked into toString(): $output")
    }

    @Test
    fun `RegisterMemberRequest toString redacts PII and password`() {
        val credentialValue = "not-a-real-credential"
        val request =
            RegisterMemberRequest(firstName = "Jane", lastName = name, password = credentialValue, email = email)

        val output = request.toString()

        assertFalse(output.contains(email), "email leaked into toString(): $output")
        assertFalse(output.contains(credentialValue), "password leaked into toString(): $output")
        assertFalse(output.contains(name), "lastName leaked into toString(): $output")
    }
}
