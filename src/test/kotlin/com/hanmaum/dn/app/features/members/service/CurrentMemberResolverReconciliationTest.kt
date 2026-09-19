package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberClaimConflictRepository
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.domain.MemberReconciliation
import com.hanmaum.dn.app.features.newcomers.repository.MemberReconciliationRepository
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerProfileRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurrentMemberResolverReconciliationTest {
    private val members = mock<MemberRepository>()
    private val claimConflicts = mock<MemberClaimConflictRepository>()
    private val reconciliations = mock<MemberReconciliationRepository>()
    private val profiles = mock<NewcomerProfileRepository>()
    private val resolver = CurrentMemberResolver(members, claimConflicts, reconciliations, profiles)

    @Test
    fun `verified exact identity atomically claims the existing member`() {
        val registration =
            member(1, "김", "새봄", LocalDate.of(1998, 2, 3)).apply {
                keycloakId = "kc-1"
                email = null
            }
        val existing = member(2, "김", "새봄", LocalDate.of(1998, 2, 3)).apply { email = "same@example.com" }
        whenever(members.findByKeycloakIdAndDeletedAtIsNull("kc-1")).thenReturn(registration)
        whenever(members.findByEmailAndDeletedAtIsNullForUpdate("same@example.com")).thenReturn(existing)
        whenever(members.saveAndFlush(registration)).thenReturn(registration)
        whenever(members.save(existing)).thenReturn(existing)

        val resolved = resolver.resolveAndLink("kc-1", "same@example.com", true)

        assertEquals(existing, resolved)
        assertEquals("kc-1", existing.keycloakId)
        assertNull(registration.keycloakId)
        assertEquals(MemberStatus.DELETED, registration.memberStatus)
        assertNotNull(registration.deletedAt)
    }

    @Test
    fun `verified email with mismatching birth date creates review instead of linking`() {
        val registration =
            member(1, "김", "새봄", LocalDate.of(1998, 2, 4)).apply {
                keycloakId = "kc-1"
                email = null
            }
        val existing = member(2, "김", "새봄", LocalDate.of(1998, 2, 3)).apply { email = "same@example.com" }
        whenever(members.findByKeycloakIdAndDeletedAtIsNull("kc-1")).thenReturn(registration)
        whenever(members.findByEmailAndDeletedAtIsNullForUpdate("same@example.com")).thenReturn(existing)
        whenever(reconciliations.findAllByRegistrationMemberIdAndDeletedAtIsNull(1)).thenReturn(emptyList())
        whenever(reconciliations.save(any<MemberReconciliation>())).thenAnswer { it.arguments[0] }

        val resolved = resolver.resolveAndLink("kc-1", "same@example.com", true)

        assertEquals(registration, resolved)
        assertEquals("kc-1", registration.keycloakId)
        assertNull(existing.keycloakId)
        verify(reconciliations).save(any<MemberReconciliation>())
    }

    @Test
    fun `conflicting submitted profile values create review before any link`() {
        val registration =
            member(1, "김", "새봄", LocalDate.of(1998, 2, 3)).apply {
                keycloakId = "kc-1"
                email = null
                phoneNumber = "010-2222-3333"
            }
        val existing =
            member(2, "김", "새봄", LocalDate.of(1998, 2, 3)).apply {
                email = "same@example.com"
                phoneNumber = "010-1111-1111"
            }
        whenever(members.findByKeycloakIdAndDeletedAtIsNull("kc-1")).thenReturn(registration)
        whenever(members.findByEmailAndDeletedAtIsNullForUpdate("same@example.com")).thenReturn(existing)
        whenever(reconciliations.findAllByRegistrationMemberIdAndDeletedAtIsNull(1)).thenReturn(emptyList())
        whenever(reconciliations.save(any<MemberReconciliation>())).thenAnswer { it.arguments[0] }

        val resolved = resolver.resolveAndLink("kc-1", "same@example.com", true)

        val review = argumentCaptor<MemberReconciliation>()
        verify(reconciliations).save(review.capture())
        assertEquals(registration, resolved)
        assertEquals("kc-1", registration.keycloakId)
        assertNull(existing.keycloakId)
        assertEquals("PROFILE_VALUE_CONFLICT", review.firstValue.reasons)
        assertTrue(review.firstValue.conflictFields!!.contains("phoneNumber"))
    }

    private fun member(
        id: Long,
        lastName: String,
        firstName: String,
        birthDate: LocalDate,
    ) = Member(lastName = lastName, firstName = firstName, birthDate = birthDate).apply { this.id = id }
}
