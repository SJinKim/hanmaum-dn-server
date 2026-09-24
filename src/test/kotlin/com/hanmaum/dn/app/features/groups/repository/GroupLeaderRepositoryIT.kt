package com.hanmaum.dn.app.features.groups.repository

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.domain.GroupLeader
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.LocalDate

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class GroupLeaderRepositoryIT {
    @Autowired lateinit var repository: GroupLeaderRepository

    @Autowired lateinit var entityManager: EntityManager

    @Test
    fun `latest retained tenure includes ended terms and excludes soft deleted rows`() {
        val member = Member(lastName = "김", firstName = "철수")
        val oldGroup = ChurchGroup(name = "이전 조", division = "A")
        val newGroup = ChurchGroup(name = "새 조", division = "A")
        entityManager.persist(member)
        entityManager.persist(oldGroup)
        entityManager.persist(newGroup)
        val older = GroupLeader(oldGroup, member, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 1))
        val latest = GroupLeader(newGroup, member, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 24))
        val deleted = GroupLeader(oldGroup, member, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1))
        deleted.deletedAt = Instant.now()
        entityManager.persist(older)
        entityManager.persist(latest)
        entityManager.persist(deleted)
        entityManager.flush()
        entityManager.clear()

        val result = repository.findFirstByMemberIdAndDeletedAtIsNullOrderByStartDateDescIdDesc(member.id!!)

        assertEquals(latest.id, result?.id)
        assertEquals(newGroup.publicId, result?.group?.publicId)
        assertEquals(LocalDate.of(2026, 9, 24), result?.endDate)
    }

    @Test
    fun `latest retained tenure is absent for a member who never led`() {
        val member = Member(lastName = "김", firstName = "영희")
        entityManager.persist(member)
        entityManager.flush()

        assertNull(repository.findFirstByMemberIdAndDeletedAtIsNullOrderByStartDateDescIdDesc(member.id!!))
    }

    @Test
    fun `same day reassignment returns the newer open tenure`() {
        val member = Member(lastName = "김", firstName = "민수")
        val churchGroup = ChurchGroup(name = "다니엘조", division = "B")
        entityManager.persist(member)
        entityManager.persist(churchGroup)
        val day = LocalDate.of(2026, 9, 24)
        val ended = GroupLeader(churchGroup, member, day, day)
        val reopened = GroupLeader(churchGroup, member, day)
        entityManager.persist(ended)
        entityManager.persist(reopened)
        entityManager.flush()
        entityManager.clear()

        val result = repository.findFirstByMemberIdAndDeletedAtIsNullOrderByStartDateDescIdDesc(member.id!!)

        assertEquals(reopened.id, result?.id)
        assertNull(result?.endDate)
    }
}
