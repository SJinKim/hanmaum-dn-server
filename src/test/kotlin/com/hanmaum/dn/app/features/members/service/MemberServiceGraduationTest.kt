package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.features.members.api.toDto
import com.hanmaum.dn.app.features.members.api.toSummaryDto
import com.hanmaum.dn.app.features.members.domain.Member
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * The graduated flag is derived, never stored. These pin the derivation so a future
 * denormalised column cannot silently disagree with the graduation rows.
 */
class MemberServiceGraduationTest {
    private fun member(): Member = Member(lastName = "김", firstName = "철수")

    @Test
    fun `a member with an open graduation is reported as graduated`() {
        val dto = member().toDto(graduatedOn = LocalDate.of(2026, 5, 1))

        assertTrue(dto.graduated)
        assertEquals(LocalDate.of(2026, 5, 1), dto.graduatedOn)
    }

    @Test
    fun `a member with no open graduation is not graduated`() {
        val dto = member().toDto()

        assertFalse(dto.graduated)
        assertEquals(null, dto.graduatedOn)
    }

    @Test
    fun `the summary dto derives the flag the same way`() {
        val graduated = member().toSummaryDto(graduatedOn = LocalDate.of(2026, 5, 1))
        val current = member().toSummaryDto()

        assertTrue(graduated.graduated)
        assertEquals(LocalDate.of(2026, 5, 1), graduated.graduatedOn)
        assertFalse(current.graduated)
    }
}
