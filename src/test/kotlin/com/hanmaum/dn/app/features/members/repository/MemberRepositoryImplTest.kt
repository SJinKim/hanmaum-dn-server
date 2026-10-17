package com.hanmaum.dn.app.features.members.repository

import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.common.pii.PiiProperties
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.statistics.api.v1.dto.ChartDataDto
import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals

class MemberRepositoryImplTest {
    @Test
    fun `gender distribution counts missing values with a Korean label`() {
        val entityManager = mock<EntityManager>()
        val query = mock<TypedQuery<Member>>()
        whenever(entityManager.createQuery(any<String>(), eq(Member::class.java))).thenReturn(query)
        whenever(query.resultList).thenReturn(
            listOf(
                Member(lastName = "Test", firstName = "One", gender = Gender.M),
                Member(lastName = "Test", firstName = "Two", gender = Gender.F),
                Member(lastName = "Test", firstName = "Three", gender = null),
                Member(lastName = "Test", firstName = "Four", gender = null),
            ),
        )
        val repository = MemberRepositoryImpl(entityManager, PiiProperties())

        val distribution = repository.getGenderDistribution()

        assertEquals(
            listOf(ChartDataDto("형제", 1L), ChartDataDto("자매", 1L), ChartDataDto("미입력", 2L)),
            distribution,
        )
    }
}
