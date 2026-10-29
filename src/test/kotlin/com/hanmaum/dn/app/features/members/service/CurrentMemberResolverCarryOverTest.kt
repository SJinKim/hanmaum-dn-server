package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.features.members.domain.IdentityStatus
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberClaimConflictRepository
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.domain.MemberReconciliation
import com.hanmaum.dn.app.features.newcomers.domain.ReconciliationStatus
import com.hanmaum.dn.app.features.newcomers.repository.MemberReconciliationRepository
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerProfileRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 신분, 소속 and 카카오 아이디 live on the member since HDN-332 and HDN-360, so a claim has to
 * carry them over. None of the three blocks a login: they change over a lifetime, so a difference
 * is an update rather than a contradiction. A dropped value is only noted in 중복 확인.
 */
class CurrentMemberResolverCarryOverTest {
    private val members = mock<MemberRepository>()
    private val claimConflicts = mock<MemberClaimConflictRepository>()
    private val reconciliations = mock<MemberReconciliationRepository>()
    private val profiles = mock<NewcomerProfileRepository>()
    private val resolver = CurrentMemberResolver(members, claimConflicts, reconciliations, profiles)

    @Test
    fun `the submitted 신분 wins while the office keeps 소속 and 카카오 아이디`() {
        val registration =
            member(1).apply {
                keycloakId = "kc-1"
                identityStatus = IdentityStatus.EMPLOYEE
                workOrSchool = "새 회사"
                kakaoId = "saebom.kim"
            }
        val existing =
            member(2).apply {
                email = "same@example.com"
                identityStatus = IdentityStatus.UNIVERSITY_STUDENT
                workOrSchool = "옛 회사"
                kakaoId = "office.handle"
            }
        stubClaim(registration, existing)

        val resolved = resolver.resolveAndLink("kc-1", "same@example.com", true)

        assertEquals(existing, resolved)
        assertEquals(IdentityStatus.EMPLOYEE, existing.identityStatus)
        assertEquals("옛 회사", existing.workOrSchool)
        assertEquals("office.handle", existing.kakaoId)
    }

    @Test
    fun `a dropped value is noted as superseded instead of blocking the link`() {
        val registration =
            member(1).apply {
                keycloakId = "kc-1"
                identityStatus = IdentityStatus.EMPLOYEE
                workOrSchool = "새 회사"
                kakaoId = "saebom.kim"
            }
        val existing =
            member(2).apply {
                email = "same@example.com"
                identityStatus = IdentityStatus.UNIVERSITY_STUDENT
                workOrSchool = "옛 회사"
                kakaoId = "office.handle"
            }
        stubClaim(registration, existing)
        whenever(reconciliations.save(any<MemberReconciliation>())).thenAnswer { it.arguments[0] }

        resolver.resolveAndLink("kc-1", "same@example.com", true)

        val note = argumentCaptor<MemberReconciliation>()
        verify(reconciliations).save(note.capture())
        assertEquals("VALUE_SUPERSEDED", note.firstValue.reasons)
        assertEquals("identityStatus,kakaoId,workOrSchool", note.firstValue.conflictFields)
        assertEquals(ReconciliationStatus.LINKED, note.firstValue.status)
        assertEquals(existing, note.firstValue.selectedMember)
        assertNotNull(note.firstValue.resolvedAt)
    }

    @Test
    fun `filling an empty field needs no note`() {
        val registration =
            member(1).apply {
                keycloakId = "kc-1"
                identityStatus = IdentityStatus.EMPLOYEE
                workOrSchool = "새 회사"
                kakaoId = "saebom.kim"
            }
        val existing = member(2).apply { email = "same@example.com" }
        stubClaim(registration, existing)

        resolver.resolveAndLink("kc-1", "same@example.com", true)

        assertEquals(IdentityStatus.EMPLOYEE, existing.identityStatus)
        assertEquals("새 회사", existing.workOrSchool)
        assertEquals("saebom.kim", existing.kakaoId)
        verify(reconciliations, never()).save(any<MemberReconciliation>())
    }

    @Test
    fun `identical values need no note`() {
        val registration =
            member(1).apply {
                keycloakId = "kc-1"
                identityStatus = IdentityStatus.EMPLOYEE
                workOrSchool = "새 회사"
                kakaoId = "saebom.kim"
            }
        val existing =
            member(2).apply {
                email = "same@example.com"
                identityStatus = IdentityStatus.EMPLOYEE
                workOrSchool = "새 회사"
                kakaoId = "saebom.kim"
            }
        stubClaim(registration, existing)

        resolver.resolveAndLink("kc-1", "same@example.com", true)

        verify(reconciliations, never()).save(any<MemberReconciliation>())
    }

    private fun stubClaim(
        registration: Member,
        existing: Member,
    ) {
        whenever(members.findByKeycloakIdAndDeletedAtIsNull("kc-1")).thenReturn(registration)
        whenever(members.findByEmailAndDeletedAtIsNullForUpdate("same@example.com")).thenReturn(existing)
        whenever(members.saveAndFlush(registration)).thenReturn(registration)
        whenever(members.save(existing)).thenReturn(existing)
    }

    private fun member(id: Long) = Member(lastName = "김", firstName = "새봄", birthDate = BIRTH).apply { this.id = id }

    private companion object {
        val BIRTH = LocalDate.of(1998, 2, 3)
    }
}
