package com.hanmaum.dn.app.features.newcomers.service

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.domain.MemberReconciliation
import com.hanmaum.dn.app.features.newcomers.domain.ReconciliationStatus
import com.hanmaum.dn.app.features.newcomers.repository.MemberReconciliationRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemberReconciliationIntakeTest {
    private val members = mock<MemberRepository>()
    private val reviews = mock<MemberReconciliationRepository>()
    private val intake = MemberReconciliationIntake(members, reviews)

    private val birthDate = LocalDate.of(1990, 3, 1)

    @Test
    fun `same name and birth date is a possible match`() {
        val registration = member(1, birthDate = birthDate).apply { keycloakId = "kc-app" }
        val formMember = member(2, lastName = "김 ", firstName = "새 봄", birthDate = birthDate)
        whenever(members.findAllByDeletedAtIsNull()).thenReturn(listOf(registration, formMember))

        assertEquals(listOf(formMember), intake.findPossibleMatches(registration))
    }

    @Test
    fun `same name and phone number is a possible match when the birth date differs`() {
        val registration = member(1, phone = "+49 151 2345678")
        val other = member(2, birthDate = birthDate, phone = "0151-2345678")
        whenever(members.findAllByDeletedAtIsNull()).thenReturn(listOf(other))

        assertEquals(listOf(other), intake.findPossibleMatches(registration))
    }

    @Test
    fun `an app account is never offered another app account`() {
        val registration = member(1, birthDate = birthDate).apply { keycloakId = "kc-new" }
        val otherApp = member(2, birthDate = birthDate).apply { keycloakId = "kc-old" }
        whenever(members.findAllByDeletedAtIsNull()).thenReturn(listOf(otherApp))

        assertTrue(intake.findPossibleMatches(registration).isEmpty())
    }

    @Test
    fun `name alone is not enough`() {
        val registration = member(1)
        whenever(members.findAllByDeletedAtIsNull()).thenReturn(listOf(member(2, birthDate = birthDate)))

        assertTrue(intake.findPossibleMatches(registration).isEmpty())
        verify(members, never()).findAllByDeletedAtIsNull()
    }

    @Test
    fun `several hits open one case flagged with multiple candidates`() {
        val registration = member(1, birthDate = birthDate)
        whenever(members.findAllByDeletedAtIsNull())
            .thenReturn(listOf(member(2, birthDate = birthDate), member(3, birthDate = birthDate)))
        whenever(reviews.findAllByRegistrationMemberIdAndDeletedAtIsNull(1)).thenReturn(emptyList())

        assertTrue(intake.openForPossibleMatches(registration))

        val saved = argumentCaptor<MemberReconciliation>()
        verify(reviews).save(saved.capture())
        assertEquals("POSSIBLE_NAME_BIRTH_MATCH,MULTIPLE_CANDIDATES", saved.firstValue.reasons)
        assertEquals(setOf(2L, 3L), saved.firstValue.candidateMemberIds)
    }

    @Test
    fun `a dismissed case is not reopened`() {
        val registration = member(1, birthDate = birthDate)
        whenever(members.findAllByDeletedAtIsNull()).thenReturn(listOf(member(2, birthDate = birthDate)))
        whenever(reviews.findAllByRegistrationMemberIdAndDeletedAtIsNull(1))
            .thenReturn(listOf(MemberReconciliation(registration, "POSSIBLE_NAME_BIRTH_MATCH", status = ReconciliationStatus.DISMISSED)))

        assertFalse(intake.openForPossibleMatches(registration))
        verify(reviews, never()).save(any<MemberReconciliation>())
    }

    @Test
    fun `phone numbers fold the german prefix and drop formatting`() {
        assertEquals("01512345678", MemberReconciliationIntake.normalizePhone("+49 (151) 234-5678"))
        assertEquals("01512345678", MemberReconciliationIntake.normalizePhone("0049 151 2345678"))
        assertEquals("01512345678", MemberReconciliationIntake.normalizePhone("0151 2345678"))
        assertNull(MemberReconciliationIntake.normalizePhone("123"))
        assertNull(MemberReconciliationIntake.normalizePhone(" "))
    }

    private fun member(
        id: Long,
        lastName: String = "김",
        firstName: String = "새봄",
        birthDate: LocalDate? = null,
        phone: String? = null,
    ): Member =
        Member(lastName = lastName, firstName = firstName, birthDate = birthDate, phoneNumber = phone).apply {
            this.id = id
        }
}
