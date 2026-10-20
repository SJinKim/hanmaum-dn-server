package com.hanmaum.dn.app.features.groups.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberSummaryDto
import com.hanmaum.dn.app.features.members.api.v1.dto.SummaryTrainingDto
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.MemberService
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class TrainingMatrixServiceTest {
    @Mock private lateinit var members: MemberService

    @Mock private lateinit var repository: MemberRepository

    private fun service() = TrainingMatrixService(members, repository)

    @Test
    fun `matrix projection keeps page metadata and drops administrative fields`() {
        val id = UUID.randomUUID().toString()
        val row =
            MemberSummaryDto(
                publicId = id,
                lastName = "김",
                firstName = "테스트",
                memberStatus = "ACTIVE",
                email = "synthetic@example.org",
                baptism = "BAPTIZED",
                groupPublicId = "group",
                groupName = "1순",
                activeMinistries = listOf("private-ministry"),
                trainings = listOf(SummaryTrainingDto("ONE_ON_ONE", "일대일", "IN_PROGRESS")),
                oneOnOneSignupFilled = true,
                origin = "NEWCOMER_FORM",
                appLinked = true,
            )
        `when`(members.getMembers(null, MemberStatus.ACTIVE, null, null, null, null, null, null, 1, 2))
            .thenReturn(PageImpl(listOf(row), PageRequest.of(1, 2), 5))

        val page = service().getMembers(1, 2)
        assertEquals(1, page.number)
        assertEquals(2, page.size)
        assertEquals(5L, page.totalElements)
        assertEquals(3, page.totalPages)
        assertEquals(id, page.content.single().publicId)
        assertEquals(row.trainings, page.content.single().trainings)
        assertTrue(page.content.single().oneOnOneSignupFilled)
        val mapper = JsonMapper.builder().build()
        val json = mapper.readTree(mapper.writeValueAsString(page.content.single()))
        assertEquals(
            setOf(
                "publicId",
                "lastName",
                "firstName",
                "memberStatus",
                "baptism",
                "groupPublicId",
                "groupName",
                "trainings",
                "isNextGroupLeader",
                "oneOnOneSignupFilled",
                "isGroupLeader",
                "groupLeaderSince",
            ),
            json.propertyNames().toSet(),
        )
        verify(members).getMembers(null, MemberStatus.ACTIVE, null, null, null, null, null, null, 1, 2)
        verifyNoInteractions(repository)
    }

    @Test
    fun `invalid matrix pagination is rejected before reading members`() {
        for ((page, size) in listOf(-1 to 20, 0 to 0, 0 to 10_001)) {
            assertEquals(400, assertThrows<ResponseStatusException> { service().getMembers(page, size) }.statusCode.value())
        }
        verifyNoInteractions(members, repository)
    }

    @Test
    fun `flag writes change only next leader and preserve the other member data`() {
        val member = Member(lastName = "김", firstName = "테스트", email = "synthetic@example.org", memberStatus = MemberStatus.ACTIVE)
        member.oneOnOneSignupFilled = true
        `when`(repository.findForUpdateByPublicIdAndDeletedAtIsNull(member.publicId)).thenReturn(Optional.of(member))

        val result = service().setNextGroupLeader(member.publicId, true)
        assertEquals(member.publicId.toString(), result.publicId)
        assertTrue(result.isNextGroupLeader)
        assertTrue(member.isNextGroupLeader)
        assertTrue(member.oneOnOneSignupFilled)
        assertEquals("synthetic@example.org", member.email)
        assertEquals("테스트", member.firstName)
        verify(repository, never()).save(any<Member>())
        verifyNoInteractions(members)
    }

    @Test
    fun `inactive and missing members cannot receive a matrix flag write`() {
        val inactive = Member(lastName = "김", firstName = "테스트", memberStatus = MemberStatus.PENDING)
        `when`(repository.findForUpdateByPublicIdAndDeletedAtIsNull(inactive.publicId)).thenReturn(Optional.of(inactive))
        assertThrows<EntityNotFoundException> { service().setNextGroupLeader(inactive.publicId, true) }
        assertFalse(inactive.isNextGroupLeader)
        val missing = UUID.randomUUID()
        `when`(repository.findForUpdateByPublicIdAndDeletedAtIsNull(missing)).thenReturn(Optional.empty())
        assertThrows<EntityNotFoundException> { service().setNextGroupLeader(missing, true) }
        verify(repository, never()).save(any<Member>())
    }
}
