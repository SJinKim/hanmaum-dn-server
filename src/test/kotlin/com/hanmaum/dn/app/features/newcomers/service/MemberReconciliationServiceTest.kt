package com.hanmaum.dn.app.features.newcomers.service

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.domain.MemberReconciliation
import com.hanmaum.dn.app.features.newcomers.domain.ReconciliationStatus
import com.hanmaum.dn.app.features.newcomers.repository.MemberReconciliationRepository
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerProfileRepository
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MemberReconciliationServiceTest {
    private val reviews = mock<MemberReconciliationRepository>()
    private val members = mock<MemberRepository>()
    private val profiles = mock<NewcomerProfileRepository>()
    private val service = MemberReconciliationService(reviews, members, profiles)

    @Test
    fun `link transfers the keycloak subject and closes the duplicate`() {
        val registration = member(1).apply { keycloakId = "kc-registration" }
        val selected = member(2)
        val review =
            MemberReconciliation(registration, "EMAIL_MATCH_IDENTITY_MISMATCH").apply {
                candidateMemberIds += selected.id!!
            }
        whenever(reviews.findForUpdate(review.publicId)).thenReturn(review)
        whenever(members.findForUpdateByPublicIdAndDeletedAtIsNull(selected.publicId)).thenReturn(Optional.of(selected))
        whenever(profiles.findByMemberIdAndDeletedAtIsNull(1)).thenReturn(null)
        whenever(profiles.findByMemberIdAndDeletedAtIsNull(2)).thenReturn(null)
        whenever(members.saveAndFlush(registration)).thenReturn(registration)
        whenever(members.save(selected)).thenReturn(selected)
        whenever(reviews.saveAndFlush(review)).thenReturn(review)

        val response = service.link(review.publicId, selected.publicId, 0, "kc-admin", false)

        assertEquals(ReconciliationStatus.LINKED, response.status)
        assertEquals("kc-registration", selected.keycloakId)
        assertNull(registration.keycloakId)
        assertNotNull(registration.deletedAt)
        assertEquals(selected, review.selectedMember)
        verify(members).findForUpdateByPublicIdAndDeletedAtIsNull(selected.publicId)
    }

    @Test
    fun `stale version is rejected before it can link another account`() {
        val registration = member(1).apply { keycloakId = "kc-registration" }
        val review = MemberReconciliation(registration, "MULTIPLE_CANDIDATES").apply { version = 2 }
        whenever(reviews.findForUpdate(review.publicId)).thenReturn(review)

        assertThrows<NewcomerException> { service.link(review.publicId, UUID.randomUUID(), 1, "kc-admin", false) }
    }

    @Test
    fun `dismissal is idempotent and retains the decision`() {
        val registration = member(1).apply { keycloakId = "kc-registration" }
        val review = MemberReconciliation(registration, "POSSIBLE_NAME_BIRTH_MATCH")
        whenever(reviews.findForUpdate(review.publicId)).thenReturn(review)
        whenever(reviews.saveAndFlush(review)).thenReturn(review)

        val dismissed = service.dismiss(review.publicId, 0, "kc-admin")
        val repeated = service.dismiss(review.publicId, 999, "kc-other-admin")

        assertEquals(ReconciliationStatus.DISMISSED, dismissed.status)
        assertEquals(ReconciliationStatus.DISMISSED, repeated.status)
        assertEquals("kc-admin", review.resolvedBy)
    }

    private fun member(id: Long): Member = Member(lastName = "김", firstName = "새봄").apply { this.id = id }
}
