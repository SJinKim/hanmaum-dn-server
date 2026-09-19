package com.hanmaum.dn.app.features.newcomers.service

import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.GraduateNewcomerRequest
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerGraduation
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerLifecycle
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerProfile
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerGraduationRepository
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerProfileRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NewcomerGraduationServiceTest {
    private val profiles = mock<NewcomerProfileRepository>()
    private val graduations = mock<NewcomerGraduationRepository>()
    private val groups = mock<ChurchGroupRepository>()
    private val members = mock<MemberRepository>()
    private val service = NewcomerGraduationService(profiles, graduations, groups, members)

    @Test
    fun `graduate reuses member and derives cohort label from graduation year`() {
        val member = Member(lastName = "김", firstName = "새봄").apply { id = 1 }
        val profile = NewcomerProfile(member).apply { id = 10 }
        val group = ChurchGroup(name = "다니엘순").apply { id = 20 }
        whenever(profiles.findForUpdate(profile.publicId)).thenReturn(profile)
        whenever(groups.findByPublicIdAndDeletedAtIsNull(group.publicId)).thenReturn(Optional.of(group))
        whenever(graduations.findByNewcomerProfileIdAndDeletedAtIsNull(10)).thenReturn(null)
        whenever(members.save(member)).thenReturn(member)
        whenever(profiles.save(profile)).thenReturn(profile)
        whenever(graduations.save(any<NewcomerGraduation>())).thenAnswer { it.arguments[0] }

        val result =
            service.graduate(
                profile.publicId,
                GraduateNewcomerRequest(group.publicId.toString(), 3, LocalDate.of(2027, 1, 5), "정착"),
                "admin-sub",
            )

        assertEquals("2027-3기", result.cohortLabel)
        assertEquals(group, member.group)
        assertEquals(NewcomerLifecycle.GRADUATED, profile.lifecycleStatus)
    }

    @Test
    fun `identical retry returns the original graduation without another write`() {
        val member = Member(lastName = "김", firstName = "새봄").apply { id = 1 }
        val profile = NewcomerProfile(member).apply { id = 10 }
        val group = ChurchGroup(name = "다니엘순").apply { id = 20 }
        val existing =
            NewcomerGraduation(
                newcomerProfile = profile,
                member = member,
                group = group,
                cohortNumber = 3,
                cohortLabel = "2027-3기",
                graduatedOn = LocalDate.of(2027, 1, 5),
                assignmentReason = "정착",
                graduatedBy = "admin-sub",
            )
        whenever(profiles.findForUpdate(profile.publicId)).thenReturn(profile)
        whenever(groups.findByPublicIdAndDeletedAtIsNull(group.publicId)).thenReturn(Optional.of(group))
        whenever(graduations.findByNewcomerProfileIdAndDeletedAtIsNull(10)).thenReturn(existing)

        val result =
            service.graduate(
                profile.publicId,
                GraduateNewcomerRequest(group.publicId.toString(), 3, LocalDate.of(2027, 1, 5), "정착"),
                "admin-sub",
            )

        assertEquals(existing.publicId.toString(), result.publicId)
        verify(graduations, never()).save(any())
        verify(members, never()).save(any())
    }

    @Test
    fun `different retry is rejected as a conflict`() {
        val member = Member(lastName = "김", firstName = "새봄").apply { id = 1 }
        val profile = NewcomerProfile(member).apply { id = 10 }
        val existingGroup = ChurchGroup(name = "다니엘순").apply { id = 20 }
        val requestedGroup = ChurchGroup(name = "요한순").apply { id = 21 }
        val existing =
            NewcomerGraduation(
                newcomerProfile = profile,
                member = member,
                group = existingGroup,
                cohortNumber = 3,
                cohortLabel = "2027-3기",
                graduatedOn = LocalDate.of(2027, 1, 5),
                assignmentReason = null,
                graduatedBy = "admin-sub",
            )
        whenever(profiles.findForUpdate(profile.publicId)).thenReturn(profile)
        whenever(groups.findByPublicIdAndDeletedAtIsNull(requestedGroup.publicId)).thenReturn(Optional.of(requestedGroup))
        whenever(graduations.findByNewcomerProfileIdAndDeletedAtIsNull(10)).thenReturn(existing)

        val error =
            assertFailsWith<NewcomerException> {
                service.graduate(
                    profile.publicId,
                    GraduateNewcomerRequest(requestedGroup.publicId.toString(), 3, LocalDate.of(2027, 1, 5)),
                    "admin-sub",
                )
            }

        assertEquals(HttpStatus.CONFLICT, error.status)
        verify(graduations, never()).save(any())
    }

    @Test
    fun `newcomer group cannot be selected as a destination`() {
        val member = Member(lastName = "김", firstName = "새봄").apply { id = 1 }
        val profile = NewcomerProfile(member).apply { id = 10 }
        val newcomerGroup = ChurchGroup(name = "새가족").apply { id = 20 }
        whenever(profiles.findForUpdate(profile.publicId)).thenReturn(profile)
        whenever(groups.findByPublicIdAndDeletedAtIsNull(newcomerGroup.publicId)).thenReturn(Optional.of(newcomerGroup))

        val error =
            assertFailsWith<NewcomerException> {
                service.graduate(profile.publicId, GraduateNewcomerRequest(newcomerGroup.publicId.toString(), 1), "admin-sub")
            }

        assertEquals(HttpStatus.BAD_REQUEST, error.status)
        verify(graduations, never()).save(any())
    }
}
