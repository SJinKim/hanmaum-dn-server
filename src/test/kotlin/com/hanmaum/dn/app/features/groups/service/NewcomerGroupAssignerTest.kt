package com.hanmaum.dn.app.features.groups.service

import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.members.domain.Member
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NewcomerGroupAssignerTest {
    private val groups = mock<ChurchGroupRepository>()
    private val assigner = NewcomerGroupAssigner(groups)
    private val newcomerGroup =
        ChurchGroup(name = "새가족").apply {
            id = 20
            newcomerGroup = true
        }

    @Test
    fun `member without a group goes into the newcomer group`() {
        val member = Member(lastName = "홍", firstName = "길동").apply { id = 1 }
        whenever(groups.findFirstByNewcomerGroupTrueAndDeletedAtIsNull()).thenReturn(newcomerGroup)

        assigner.assignIfUngrouped(member)

        assertEquals(newcomerGroup, member.group)
    }

    @Test
    fun `existing group is kept`() {
        val existing = ChurchGroup(name = "다니엘순").apply { id = 21 }
        val member = Member(lastName = "홍", firstName = "길동", group = existing).apply { id = 1 }

        assigner.assignIfUngrouped(member)

        assertEquals(existing, member.group)
        verify(groups, never()).findFirstByNewcomerGroupTrueAndDeletedAtIsNull()
    }

    @Test
    fun `member stays without a group when no newcomer group is marked`() {
        val member = Member(lastName = "홍", firstName = "길동").apply { id = 1 }
        whenever(groups.findFirstByNewcomerGroupTrueAndDeletedAtIsNull()).thenReturn(null)

        assigner.assignIfUngrouped(member)

        assertNull(member.group)
    }
}
