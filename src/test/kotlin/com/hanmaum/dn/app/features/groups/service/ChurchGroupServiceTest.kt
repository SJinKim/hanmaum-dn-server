package com.hanmaum.dn.app.features.groups.service

import com.hanmaum.dn.app.features.groups.api.v1.dto.AssignGroupLeaderRequest
import com.hanmaum.dn.app.features.groups.api.v1.dto.ChurchGroupSummaryDto
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.domain.GroupLeader
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.groups.repository.GroupLeaderRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class ChurchGroupServiceTest {
    @Mock private lateinit var churchGroupRepository: ChurchGroupRepository

    @Mock private lateinit var groupLeaderRepository: GroupLeaderRepository

    @Mock private lateinit var memberRepository: MemberRepository

    private lateinit var service: ChurchGroupService

    @BeforeEach
    fun setUp() {
        service = ChurchGroupService(churchGroupRepository, groupLeaderRepository, memberRepository)
    }

    private fun group(
        id: Long,
        division: String? = "1구역",
        name: String = "다니엘조",
    ): ChurchGroup = ChurchGroup(division = division, name = name).also { it.id = id }

    private fun member(
        id: Long,
        group: ChurchGroup?,
        lastName: String = "김",
        firstName: String = "철수",
    ): Member =
        Member(lastName = lastName, firstName = firstName).also {
            it.id = id
            it.group = group
        }

    private fun leader(
        group: ChurchGroup,
        member: Member,
        startDate: LocalDate,
        id: Long = 100L,
    ): GroupLeader = GroupLeader(group = group, member = member, startDate = startDate).also { it.id = id }

    // ─── getGroups ────────────────────────────────────────────────────────────

    @Test
    fun `getGroups maps entities to summary dtos preserving repository order`() {
        val a = group(id = 1L)
        val b = group(id = 2L, division = null, name = "새가족")
        `when`(churchGroupRepository.findAllByDeletedAtIsNullOrderByDivisionAscNameAsc())
            .thenReturn(listOf(a, b))
        `when`(groupLeaderRepository.findActiveByGroupIds(listOf(1L, 2L))).thenReturn(emptyList())

        val result = service.getGroups()

        assertEquals(2, result.size)
        assertEquals(a.publicId.toString(), result[0].publicId)
        assertEquals("1구역", result[0].division)
        assertEquals("다니엘조", result[0].name)
        assertEquals(b.publicId.toString(), result[1].publicId)
        assertNull(result[1].division)
        assertEquals("새가족", result[1].name)
    }

    @Test
    fun `getGroups attaches the current leader to its own group only`() {
        val led = group(id = 1L)
        val vacant = group(id = 2L, division = null, name = "새가족")
        val leaderMember = member(id = 7L, group = led, lastName = "박", firstName = "민수")
        `when`(churchGroupRepository.findAllByDeletedAtIsNullOrderByDivisionAscNameAsc())
            .thenReturn(listOf(led, vacant))
        `when`(groupLeaderRepository.findActiveByGroupIds(listOf(1L, 2L)))
            .thenReturn(listOf(leader(led, leaderMember, LocalDate.of(2026, 1, 15))))

        val result = service.getGroups()

        assertEquals(leaderMember.publicId.toString(), result[0].leaderPublicId)
        assertEquals("박민수", result[0].leaderName)
        assertEquals(LocalDate.of(2026, 1, 15), result[0].leaderSince)
        assertNull(result[1].leaderPublicId)
        assertNull(result[1].leaderName)
        assertNull(result[1].leaderSince)
    }

    @Test
    fun `getGroups skips the leader lookup when there are no groups`() {
        `when`(churchGroupRepository.findAllByDeletedAtIsNullOrderByDivisionAscNameAsc())
            .thenReturn(emptyList())

        assertEquals(emptyList<ChurchGroupSummaryDto>(), service.getGroups())

        verify(groupLeaderRepository, never()).findActiveByGroupIds(any())
    }

    // ─── assignLeader ─────────────────────────────────────────────────────────

    @Test
    fun `assignLeader creates a tenure for a group that has none`() {
        val g = group(id = 1L)
        val m = member(id = 7L, group = g)
        `when`(churchGroupRepository.findByPublicIdAndDeletedAtIsNull(g.publicId)).thenReturn(Optional.of(g))
        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(m.publicId)).thenReturn(Optional.of(m))
        `when`(groupLeaderRepository.findActiveByGroupId(1L)).thenReturn(null)
        `when`(groupLeaderRepository.save(any<GroupLeader>())).thenAnswer { it.arguments[0] }

        val result =
            service.assignLeader(
                g.publicId,
                AssignGroupLeaderRequest(m.publicId.toString(), LocalDate.of(2026, 8, 9)),
            )

        val saved = argumentCaptor<GroupLeader>()
        verify(groupLeaderRepository).save(saved.capture())
        assertSame(m, saved.firstValue.member)
        assertSame(g, saved.firstValue.group)
        assertEquals(LocalDate.of(2026, 8, 9), saved.firstValue.startDate)
        assertNull(saved.firstValue.endDate)
        assertEquals(m.publicId.toString(), result.leaderPublicId)
        assertEquals(LocalDate.of(2026, 8, 9), result.leaderSince)
    }

    @Test
    fun `assignLeader defaults the start date to today when omitted`() {
        val g = group(id = 1L)
        val m = member(id = 7L, group = g)
        `when`(churchGroupRepository.findByPublicIdAndDeletedAtIsNull(g.publicId)).thenReturn(Optional.of(g))
        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(m.publicId)).thenReturn(Optional.of(m))
        `when`(groupLeaderRepository.findActiveByGroupId(1L)).thenReturn(null)
        `when`(groupLeaderRepository.save(any<GroupLeader>())).thenAnswer { it.arguments[0] }

        val result = service.assignLeader(g.publicId, AssignGroupLeaderRequest(m.publicId.toString()))

        assertEquals(LocalDate.now(), result.leaderSince)
    }

    @Test
    fun `assignLeader closes the sitting tenure before opening the new one`() {
        val g = group(id = 1L)
        val outgoing = member(id = 7L, group = g, lastName = "박", firstName = "민수")
        val incoming = member(id = 8L, group = g, lastName = "이", firstName = "영희")
        val sitting = leader(g, outgoing, LocalDate.of(2026, 1, 15))
        `when`(churchGroupRepository.findByPublicIdAndDeletedAtIsNull(g.publicId)).thenReturn(Optional.of(g))
        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(incoming.publicId)).thenReturn(Optional.of(incoming))
        `when`(groupLeaderRepository.findActiveByGroupId(1L)).thenReturn(sitting)
        `when`(groupLeaderRepository.saveAndFlush(any<GroupLeader>())).thenAnswer { it.arguments[0] }
        `when`(groupLeaderRepository.save(any<GroupLeader>())).thenAnswer { it.arguments[0] }

        val handover = LocalDate.of(2026, 8, 9)
        val result = service.assignLeader(g.publicId, AssignGroupLeaderRequest(incoming.publicId.toString(), handover))

        // Outgoing tenure closed on the handover date, its start left intact.
        assertEquals(handover, sitting.endDate)
        assertEquals(LocalDate.of(2026, 1, 15), sitting.startDate)

        // New tenure is open and belongs to the incoming member.
        val saved = argumentCaptor<GroupLeader>()
        verify(groupLeaderRepository).save(saved.capture())
        assertSame(incoming, saved.firstValue.member)
        assertNull(saved.firstValue.endDate)
        assertEquals(handover, saved.firstValue.startDate)

        // The close must be flushed first, or both rows are briefly active and the
        // partial unique index rejects the insert.
        verify(groupLeaderRepository).saveAndFlush(sitting)
        assertEquals(incoming.publicId.toString(), result.leaderPublicId)
        assertEquals("이영희", result.leaderName)
    }

    @Test
    fun `assignLeader is a no-op when the member already leads the group`() {
        val g = group(id = 1L)
        val m = member(id = 7L, group = g)
        val sitting = leader(g, m, LocalDate.of(2026, 1, 15))
        `when`(churchGroupRepository.findByPublicIdAndDeletedAtIsNull(g.publicId)).thenReturn(Optional.of(g))
        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(m.publicId)).thenReturn(Optional.of(m))
        `when`(groupLeaderRepository.findActiveByGroupId(1L)).thenReturn(sitting)

        val result =
            service.assignLeader(
                g.publicId,
                AssignGroupLeaderRequest(m.publicId.toString(), LocalDate.of(2026, 8, 9)),
            )

        // The tenure must stay open and keep its original start — re-sending the current
        // leader must not split one continuous term into two same-day rows.
        assertNull(sitting.endDate)
        assertEquals(LocalDate.of(2026, 1, 15), sitting.startDate)
        assertEquals(LocalDate.of(2026, 1, 15), result.leaderSince)
        verify(groupLeaderRepository, never()).save(any<GroupLeader>())
        verify(groupLeaderRepository, never()).saveAndFlush(any<GroupLeader>())
    }

    @Test
    fun `assignLeader rejects a member who does not belong to the group`() {
        val g = group(id = 1L)
        val otherGroup = group(id = 2L, name = "새가족")
        val outsider = member(id = 7L, group = otherGroup)
        `when`(churchGroupRepository.findByPublicIdAndDeletedAtIsNull(g.publicId)).thenReturn(Optional.of(g))
        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(outsider.publicId)).thenReturn(Optional.of(outsider))

        val ex =
            assertThrows<ResponseStatusException> {
                service.assignLeader(g.publicId, AssignGroupLeaderRequest(outsider.publicId.toString()))
            }

        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        verify(groupLeaderRepository, never()).save(any<GroupLeader>())
    }

    @Test
    fun `assignLeader rejects an ungrouped member`() {
        val g = group(id = 1L)
        val ungrouped = member(id = 7L, group = null)
        `when`(churchGroupRepository.findByPublicIdAndDeletedAtIsNull(g.publicId)).thenReturn(Optional.of(g))
        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(ungrouped.publicId)).thenReturn(Optional.of(ungrouped))

        val ex =
            assertThrows<ResponseStatusException> {
                service.assignLeader(g.publicId, AssignGroupLeaderRequest(ungrouped.publicId.toString()))
            }

        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `assignLeader rejects a start date before the sitting leader's start`() {
        val g = group(id = 1L)
        val outgoing = member(id = 7L, group = g)
        val incoming = member(id = 8L, group = g, lastName = "이", firstName = "영희")
        val sitting = leader(g, outgoing, LocalDate.of(2026, 6, 1))
        `when`(churchGroupRepository.findByPublicIdAndDeletedAtIsNull(g.publicId)).thenReturn(Optional.of(g))
        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(incoming.publicId)).thenReturn(Optional.of(incoming))
        `when`(groupLeaderRepository.findActiveByGroupId(1L)).thenReturn(sitting)

        val ex =
            assertThrows<ResponseStatusException> {
                service.assignLeader(
                    g.publicId,
                    AssignGroupLeaderRequest(incoming.publicId.toString(), LocalDate.of(2026, 3, 1)),
                )
            }

        // Would otherwise write endDate < startDate on the outgoing tenure.
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertNull(sitting.endDate)
        verify(groupLeaderRepository, never()).save(any<GroupLeader>())
    }

    @Test
    fun `assignLeader rejects a malformed member id`() {
        val g = group(id = 1L)
        `when`(churchGroupRepository.findByPublicIdAndDeletedAtIsNull(g.publicId)).thenReturn(Optional.of(g))

        val ex =
            assertThrows<ResponseStatusException> {
                service.assignLeader(g.publicId, AssignGroupLeaderRequest("not-a-uuid"))
            }

        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }
}
