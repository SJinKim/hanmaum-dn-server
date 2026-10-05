package com.hanmaum.dn.app.features.newcomers.service

import com.hanmaum.dn.app.common.domainvalue.Baptism
import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.CreateFormLinkRequest
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.CreateNewcomerRequest
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.NewcomerResponse
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.PublicNewcomerSubmissionRequest
import com.hanmaum.dn.app.features.newcomers.domain.ChurchExperience
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerFormLink
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerFormSubmission
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerProfile
import com.hanmaum.dn.app.features.newcomers.domain.ReconciliationReason
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerFormLinkRepository
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerFormSubmissionRepository
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerProfileRepository
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NewcomerFormServiceTest {
    private val links = mock<NewcomerFormLinkRepository>()
    private val submissions = mock<NewcomerFormSubmissionRepository>()
    private val profiles = mock<NewcomerProfileRepository>()
    private val newcomers = mock<NewcomerService>()
    private val members = mock<MemberRepository>()
    private val intake = mock<MemberReconciliationIntake>()
    private val service = NewcomerFormService(links, submissions, profiles, newcomers, "2026-09", 10)
    private val serviceWithIntake =
        NewcomerFormService(links, submissions, profiles, newcomers, "2026-09", 10, members, intake)

    @Test
    fun `created link returns plaintext token but stores only its hash`() {
        whenever(links.save(any<NewcomerFormLink>())).thenAnswer { it.arguments[0] }

        val response = service.createLink(CreateFormLinkRequest(), "admin-sub")

        assertNotNull(response.token)
        assertFalse(response.token!!.matches(Regex("[0-9a-f]{64}")))
        assertEquals(43, response.token!!.length)
    }

    @Test
    fun `link cannot be valid for more than 24 hours`() {
        val error =
            assertThrows<NewcomerException> {
                service.createLink(CreateFormLinkRequest(Instant.now().plusSeconds(25 * 3600)), "admin-sub")
            }

        assertEquals(HttpStatus.BAD_REQUEST, error.status)
    }

    @Test
    fun `an email that already belongs to an app account goes to review instead of failing`() {
        val appMember = Member(lastName = "김", firstName = "새봄").apply { id = 2 }
        val formMember = Member(lastName = "김", firstName = "새봄").apply { id = 3 }
        val profile = NewcomerProfile(formMember)
        stubSubmission(profile)
        whenever(members.findByEmailAndDeletedAtIsNull("app@example.com")).thenReturn(appMember)

        serviceWithIntake.submit("token", "idempotency-key", "203.0.113.1", submission(email = " App@Example.com "))

        val created = argumentCaptor<CreateNewcomerRequest>()
        verify(newcomers).create(created.capture())
        assertNull(created.firstValue.email)
        verify(intake).open(formMember, listOf(appMember), listOf(ReconciliationReason.FORM_EMAIL_MATCH))
        verify(intake, never()).openForPossibleMatches(any())
    }

    @Test
    fun `a submission with a new email is checked for possible matches`() {
        val formMember = Member(lastName = "김", firstName = "새봄").apply { id = 3 }
        stubSubmission(NewcomerProfile(formMember))
        whenever(members.findByEmailAndDeletedAtIsNull("new@example.com")).thenReturn(null)

        serviceWithIntake.submit("token", "idempotency-key", "203.0.113.1", submission(email = "new@example.com"))

        verify(intake).openForPossibleMatches(formMember)
        verify(intake, never()).open(any(), any(), any(), any())
    }

    private fun stubSubmission(profile: NewcomerProfile) {
        val link = NewcomerFormLink("hash", Instant.now().plusSeconds(3600), "admin-sub").apply { id = 7 }
        val response = mock<NewcomerResponse> { on { publicId } doReturn profile.publicId.toString() }
        whenever(links.findByTokenHashForUpdate(any())).thenReturn(link)
        whenever(submissions.findByFormLinkIdAndIdempotencyHash(any(), any())).thenReturn(null)
        whenever(newcomers.create(any())).thenReturn(response)
        whenever(profiles.findByPublicIdAndDeletedAtIsNull(profile.publicId)).thenReturn(Optional.of(profile))
        whenever(submissions.save(any<NewcomerFormSubmission>())).thenAnswer { it.arguments[0] }
        whenever(links.save(anyOrNull<NewcomerFormLink>())).thenAnswer { it.arguments[0] }
    }

    private fun submission(email: String?) =
        PublicNewcomerSubmissionRequest(
            lastName = "김",
            firstName = "새봄",
            gender = Gender.F,
            birthDate = LocalDate.of(1990, 3, 1),
            phoneNumber = "0151 2345678",
            churchExperience = ChurchExperience.FIRST_TIME,
            baptism = Baptism.UNBAPTIZED,
            visitMotives = listOf("friend"),
            email = email,
            consentAccepted = true,
        )
}
