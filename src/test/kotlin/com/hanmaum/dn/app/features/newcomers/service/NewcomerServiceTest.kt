package com.hanmaum.dn.app.features.newcomers.service

import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.repository.MinistryAssignmentRepository
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.CreateNewcomerRequest
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.UpdateNewcomerRequest
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerLifecycle
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerProfile
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerProfileRepository
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NewcomerServiceTest {
    private val profiles = mock<NewcomerProfileRepository>()
    private val members = mock<MemberRepository>()
    private val groups = mock<ChurchGroupRepository>()
    private val assignments = mock<MinistryAssignmentRepository>()
    private val service = NewcomerService(profiles, members, groups, assignments)

    @Test
    fun `create stores one member and one linked profile without a keycloak id`() {
        whenever(members.findByEmailAndDeletedAtIsNull("new@example.com")).thenReturn(null)
        whenever(members.save(any<Member>())).thenAnswer { it.arguments[0] as Member }
        whenever(profiles.save(any<NewcomerProfile>())).thenAnswer { it.arguments[0] as NewcomerProfile }

        val response = service.create(CreateNewcomerRequest(lastName = "김", firstName = "새봄", email = " NEW@example.com "))

        assertEquals("김", response.lastName)
        assertEquals("새봄", response.firstName)
        assertEquals("new@example.com", response.email)
        assertNull(response.assignedGroup)
        verify(members).save(any<Member>())
        verify(profiles).save(any<NewcomerProfile>())
    }

    @Test
    fun `create rejects an email already owned by an active member`() {
        whenever(members.findByEmailAndDeletedAtIsNull(any()))
            .thenReturn(Member(lastName = "김", firstName = "기존", email = "same@example.com"))

        val error =
            assertThrows<NewcomerException> {
                service.create(CreateNewcomerRequest(lastName = "이", firstName = "신규", email = "same@example.com"))
            }

        assertEquals(HttpStatus.CONFLICT, error.status)
    }

    @Test
    fun `list applies protected filters before paging`() {
        val submitted = NewcomerProfile(Member(lastName = "김", firstName = "새봄"), lifecycleStatus = NewcomerLifecycle.SUBMITTED)
        val graduated = NewcomerProfile(Member(lastName = "이", firstName = "하늘"), lifecycleStatus = NewcomerLifecycle.GRADUATED)
        whenever(profiles.findAllByDeletedAtIsNull()).thenReturn(listOf(submitted, graduated))

        val result =
            service.list(
                search = "새봄",
                lifecycleStatus = NewcomerLifecycle.SUBMITTED,
                hasVisited = null,
                caregiverPublicId = null,
                groupPublicId = null,
                identityStatus = null,
                attendance = null,
                registeredFrom = null,
                registeredTo = null,
                sort = "name",
                direction = "asc",
                page = 0,
                size = 20,
            )

        assertEquals(1, result.totalElements)
        assertEquals("새봄", result.content.single().firstName)
    }

    @Test
    fun `update rejects a stale optimistic locking version`() {
        val profile = NewcomerProfile(Member(lastName = "김", firstName = "새봄"))
        whenever(profiles.findForUpdate(profile.publicId)).thenReturn(profile)

        val error = assertThrows<NewcomerException> { service.update(profile.publicId, UpdateNewcomerRequest(version = 1)) }

        assertEquals(HttpStatus.CONFLICT, error.status)
    }
}
