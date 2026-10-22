package com.hanmaum.dn.app.features.newcomers.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.groups.service.NewcomerGroupAssigner
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
import org.mockito.kotlin.check
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
    private val service = NewcomerService(profiles, members, groups, assignments, NewcomerGroupAssigner(groups))

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
    fun `create starts the member active so it never waits for app approval`() {
        whenever(members.save(any<Member>())).thenAnswer { it.arguments[0] as Member }
        whenever(profiles.save(any<NewcomerProfile>())).thenAnswer { it.arguments[0] as NewcomerProfile }

        service.create(CreateNewcomerRequest(lastName = "김", firstName = "새봄"))

        verify(members).save(check<Member> { assertEquals(MemberStatus.ACTIVE, it.memberStatus) })
    }

    @Test
    fun `create puts the active newcomer into the newcomer group`() {
        val newcomerGroup = ChurchGroup(name = "새가족").apply { newcomerGroup = true }
        whenever(groups.findFirstByNewcomerGroupTrueAndDeletedAtIsNull()).thenReturn(newcomerGroup)
        whenever(members.save(any<Member>())).thenAnswer { it.arguments[0] as Member }
        whenever(profiles.save(any<NewcomerProfile>())).thenAnswer { it.arguments[0] as NewcomerProfile }

        service.create(CreateNewcomerRequest(lastName = "홍", firstName = "길동"))

        verify(profiles).save(check<NewcomerProfile> { assertEquals(newcomerGroup, it.member.group) })
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

    @Test
    fun `update clears an address field sent as blank`() {
        val member =
            Member(lastName = "김", firstName = "새봄", street = "Hauptstr.", houseNumber = "1", zipCode = "60311", city = "Frankfurt")
        val profile = NewcomerProfile(member)
        whenever(profiles.findForUpdate(profile.publicId)).thenReturn(profile)
        whenever(profiles.saveAndFlush(any<NewcomerProfile>())).thenAnswer { it.arguments[0] }

        service.update(profile.publicId, UpdateNewcomerRequest(version = 0, street = "", houseNumber = " ", zipCode = "", city = ""))

        assertNull(member.street)
        assertNull(member.houseNumber)
        assertNull(member.zipCode)
        assertNull(member.city)
    }

    @Test
    fun `update keeps an address field the request omits`() {
        val member = Member(lastName = "김", firstName = "새봄", street = "Hauptstr.", zipCode = "60311", city = "Frankfurt")
        val profile = NewcomerProfile(member)
        whenever(profiles.findForUpdate(profile.publicId)).thenReturn(profile)
        whenever(profiles.saveAndFlush(any<NewcomerProfile>())).thenAnswer { it.arguments[0] }

        service.update(profile.publicId, UpdateNewcomerRequest(version = 0, city = " Offenbach "))

        assertEquals("Hauptstr.", member.street)
        assertEquals("60311", member.zipCode)
        assertEquals("Offenbach", member.city)
    }
}
