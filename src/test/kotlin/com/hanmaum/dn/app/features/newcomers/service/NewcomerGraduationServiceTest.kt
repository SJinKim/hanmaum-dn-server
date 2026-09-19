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
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
