package com.hanmaum.dn.app.features.members.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Member is a plain JPA entity (not a data class), so it gets no auto-generated toString().
 * This guards the hand-written override in Member.kt: it must never grow to include PII
 * or touch the lazy `group` association.
 */
class MemberTest {
    @Test
    fun `toString exposes only non-PII fields`() {
        val member =
            Member(
                lastName = "Doe",
                firstName = "Jane",
                email = "jane.doe@example.com",
                phoneNumber = "010-1234-5678",
                street = "Main St",
            )

        val output = member.toString()

        assertFalse(output.contains("Doe"), "lastName leaked into toString(): $output")
        assertFalse(output.contains("Jane"), "firstName leaked into toString(): $output")
        assertFalse(output.contains("jane.doe@example.com"), "email leaked into toString(): $output")
        assertFalse(output.contains("010-1234-5678"), "phone leaked into toString(): $output")
        assertFalse(output.contains("Main St"), "street leaked into toString(): $output")
        assertTrue(output.contains(member.publicId.toString()), "publicId should still be visible: $output")
        assertTrue(output.contains("PENDING"), "memberStatus should still be visible: $output")
    }
}
