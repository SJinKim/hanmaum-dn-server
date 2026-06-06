package com.hanmaum.dn.app.features.groups.service

import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class ChurchGroupServiceTest {
    @Mock private lateinit var churchGroupRepository: ChurchGroupRepository

    @Test
    fun `getGroups maps entities to summary dtos preserving repository order`() {
        val service = ChurchGroupService(churchGroupRepository)
        val a = ChurchGroup(division = "1구역", name = "다니엘조").also { it.id = 1L }
        val b = ChurchGroup(division = null, name = "새가족").also { it.id = 2L }
        `when`(churchGroupRepository.findAllByDeletedAtIsNullOrderByDivisionAscNameAsc())
            .thenReturn(listOf(a, b))

        val result = service.getGroups()

        assertEquals(2, result.size)
        assertEquals(a.publicId.toString(), result[0].publicId)
        assertEquals("1구역", result[0].division)
        assertEquals("다니엘조", result[0].name)
        assertEquals(b.publicId.toString(), result[1].publicId)
        assertEquals(null, result[1].division)
        assertEquals("새가족", result[1].name)
    }
}
