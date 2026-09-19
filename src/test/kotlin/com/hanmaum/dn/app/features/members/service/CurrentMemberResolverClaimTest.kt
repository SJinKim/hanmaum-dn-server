package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberClaimConflictRepository
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CurrentMemberResolverClaimTest {
    private val members = mock<MemberRepository>()
    private val conflicts = mock<MemberClaimConflictRepository>()
    private val resolver = CurrentMemberResolver(members, conflicts)

    @Test
    fun `verified exact korean identity claims the existing member`() {
        val registration = member(1, "김", " 새봄 ", LocalDate.of(1998, 2, 3)).apply { keycloakId = "kc-1" }
        val existing = member(2, "김", "새봄", LocalDate.of(1998, 2, 3)).apply { email = "same@example.com" }
        whenever(members.findByKeycloakIdAndDeletedAtIsNull("kc-1")).thenReturn(registration)
        whenever(conflicts.existsByRegistrationMemberIdAndDeletedAtIsNull(1)).thenReturn(false)
        whenever(members.findByEmailAndDeletedAtIsNullForUpdate("same@example.com")).thenReturn(existing)
        whenever(members.saveAndFlush(registration)).thenReturn(registration)
        whenever(members.save(existing)).thenReturn(existing)

        val resolved = resolver.resolveAndLink("kc-1", "same@example.com", true)

        assertEquals(existing, resolved)
        assertEquals("kc-1", existing.keycloakId)
        assertEquals(MemberStatus.DELETED, registration.memberStatus)
        assertNotNull(registration.deletedAt)
        assertNull(registration.keycloakId)
    }

    @Test
    fun `unverified email never queries or claims an existing member`() {
        val registration = member(1, "Kim", "Saebom", LocalDate.of(1998, 2, 3)).apply { keycloakId = "kc-1" }
        whenever(members.findByKeycloakIdAndDeletedAtIsNull("kc-1")).thenReturn(registration)
        whenever(conflicts.existsByRegistrationMemberIdAndDeletedAtIsNull(1)).thenReturn(false)

        assertEquals(registration, resolver.resolveAndLink("kc-1", "same@example.com", false))
        verify(members, org.mockito.kotlin.never()).findByEmailAndDeletedAtIsNullForUpdate("same@example.com")
    }

    @Test
    fun `different birth date leaves both members separate`() {
        val registration = member(1, "Kim", "Saebom", LocalDate.of(1998, 2, 4)).apply { keycloakId = "kc-1" }
        val existing = member(2, "KIM", "saebom", LocalDate.of(1998, 2, 3)).apply { email = "same@example.com" }
        whenever(members.findByKeycloakIdAndDeletedAtIsNull("kc-1")).thenReturn(registration)
        whenever(members.findByEmailAndDeletedAtIsNullForUpdate("same@example.com")).thenReturn(existing)

        assertEquals(registration, resolver.resolveAndLink("kc-1", "same@example.com", true))
        assertNull(existing.keycloakId)
        verify(members).findByEmailAndDeletedAtIsNullForUpdate("same@example.com")
        verify(conflicts).save(org.mockito.kotlin.any())
    }

    @Test
    fun `existing conflict skips the candidate lock on retry`() {
        val registration = member(1, "Kim", "Saebom", LocalDate.of(1998, 2, 3)).apply { keycloakId = "kc-1" }
        whenever(members.findByKeycloakIdAndDeletedAtIsNull("kc-1")).thenReturn(registration)
        whenever(conflicts.existsByRegistrationMemberIdAndDeletedAtIsNull(1)).thenReturn(true)

        assertEquals(registration, resolver.resolveAndLink("kc-1", "same@example.com", true))
        verify(members, org.mockito.kotlin.never()).findByEmailAndDeletedAtIsNullForUpdate("same@example.com")
    }

    @Test
    fun `successful claim records conflicting non-empty profile values`() {
        val registration =
            member(1, "Kim", "Saebom", LocalDate.of(1998, 2, 3)).apply {
                keycloakId = "kc-1"
                phoneNumber = "new"
            }
        val existing =
            member(2, "KIM", "saebom", LocalDate.of(1998, 2, 3)).apply {
                email = "same@example.com"
                phoneNumber = "old"
            }
        whenever(members.findByKeycloakIdAndDeletedAtIsNull("kc-1")).thenReturn(registration)
        whenever(conflicts.existsByRegistrationMemberIdAndDeletedAtIsNull(1)).thenReturn(false)
        whenever(members.findByEmailAndDeletedAtIsNullForUpdate("same@example.com")).thenReturn(existing)
        whenever(members.saveAndFlush(registration)).thenReturn(registration)
        whenever(members.save(existing)).thenReturn(existing)

        resolver.resolveAndLink("kc-1", "same@example.com", true)

        verify(conflicts).save(org.mockito.kotlin.any())
        assertEquals("old", existing.phoneNumber)
    }

    private fun member(
        id: Long,
        lastName: String,
        firstName: String,
        birthDate: LocalDate,
    ) = Member(lastName = lastName, firstName = firstName, birthDate = birthDate).apply { this.id = id }
}
