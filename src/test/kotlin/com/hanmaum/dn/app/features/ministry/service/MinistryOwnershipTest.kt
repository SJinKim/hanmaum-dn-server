package com.hanmaum.dn.app.features.ministry.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.service.CurrentMemberResolver
import com.hanmaum.dn.app.features.members.service.MemberProfileNotFoundException
import com.hanmaum.dn.app.features.ministry.domain.Ministry
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignment
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignmentRole
import com.hanmaum.dn.app.features.ministry.repository.MinistryAssignmentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class MinistryOwnershipTest {
    @Mock private lateinit var assignments: MinistryAssignmentRepository

    @Mock private lateinit var resolver: CurrentMemberResolver

    private val worship = Ministry("찬양팀", "찬양", "찬양 사역").also { it.id = 1L }
    private val media = Ministry("미디어팀", "미디어", "미디어 사역").also { it.id = 2L }
    private val worshipLeader = member(10L, "이", "리더")
    private val mediaLeader = member(20L, "박", "미디어")

    private fun member(
        id: Long,
        lastName: String,
        firstName: String,
    ) = Member(lastName = lastName, firstName = firstName, memberStatus = MemberStatus.ACTIVE).also { it.id = id }

    private fun assignment(
        ministry: Ministry,
        member: Member,
        role: MinistryAssignmentRole,
    ) = MinistryAssignment(ministry, member, LocalDate.of(2026, 1, 1), role = role)

    private fun ownership() = MinistryOwnership(assignments, resolver)

    @Test
    fun `admin passes without resolving a member profile`() {
        assertDoesNotThrow { ownership().requireLeader(worship, "admin-sub", bypassOwnership = true) }

        verifyNoInteractions(resolver, assignments)
    }

    @Test
    fun `the current leader of a ministry passes for that ministry`() {
        `when`(resolver.require("worship-sub")).thenReturn(worshipLeader)
        `when`(assignments.findCurrentByMinistryIds(listOf(1L)))
            .thenReturn(listOf(assignment(worship, worshipLeader, MinistryAssignmentRole.LEADER)))

        assertDoesNotThrow { ownership().requireLeader(worship, "worship-sub", bypassOwnership = false) }
    }

    @Test
    fun `the leader of another ministry gets 403`() {
        `when`(resolver.require("media-sub")).thenReturn(mediaLeader)
        `when`(assignments.findCurrentByMinistryIds(listOf(1L)))
            .thenReturn(listOf(assignment(worship, worshipLeader, MinistryAssignmentRole.LEADER)))

        val ex = assertThrows<ResponseStatusException> { ownership().requireLeader(worship, "media-sub", bypassOwnership = false) }

        assertEquals(403, ex.statusCode.value())
        assertEquals("Only this ministry's leader can change it", ex.reason)
    }

    @Test
    fun `a sub-leader of the ministry is not its owner`() {
        `when`(resolver.require("media-sub")).thenReturn(mediaLeader)
        `when`(assignments.findCurrentByMinistryIds(listOf(1L)))
            .thenReturn(
                listOf(
                    assignment(worship, worshipLeader, MinistryAssignmentRole.LEADER),
                    assignment(worship, mediaLeader, MinistryAssignmentRole.SUB_LEADER),
                ),
            )

        val ex = assertThrows<ResponseStatusException> { ownership().requireLeader(worship, "media-sub", bypassOwnership = false) }

        assertEquals(403, ex.statusCode.value())
    }

    @Test
    fun `a ministry without a leader admits no non-admin and keeps the caller's message`() {
        `when`(resolver.require("media-sub")).thenReturn(mediaLeader)
        `when`(assignments.findCurrentByMinistryIds(listOf(2L))).thenReturn(emptyList())

        val ex =
            assertThrows<ResponseStatusException> {
                ownership().requireLeader(media, "media-sub", bypassOwnership = false, deniedMessage = "custom")
            }

        assertEquals(403, ex.statusCode.value())
        assertEquals("custom", ex.reason)
    }

    @Test
    fun `a caller without a member profile gets 403, not the profile 404`() {
        `when`(resolver.require("no-profile-sub")).thenThrow(MemberProfileNotFoundException())

        val ex = assertThrows<ResponseStatusException> { ownership().requireLeader(worship, "no-profile-sub", bypassOwnership = false) }

        assertEquals(403, ex.statusCode.value())
        assertEquals("Only this ministry's leader can change it", ex.reason)
        verifyNoInteractions(assignments)
    }

    @Test
    fun `currentLeader ignores sub-leaders and members`() {
        val leader = assignment(worship, worshipLeader, MinistryAssignmentRole.LEADER)
        `when`(assignments.findCurrentByMinistryIds(listOf(1L)))
            .thenReturn(listOf(assignment(worship, mediaLeader, MinistryAssignmentRole.SUB_LEADER), leader))

        assertSame(leader, ownership().currentLeader(worship))
    }
}
