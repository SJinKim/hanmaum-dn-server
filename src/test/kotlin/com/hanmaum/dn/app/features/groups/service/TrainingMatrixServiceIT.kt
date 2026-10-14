package com.hanmaum.dn.app.features.groups.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.MemberService
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class, TrainingMatrixService::class)
@Tag("integration")
class TrainingMatrixServiceIT {
    @Autowired private lateinit var service: TrainingMatrixService

    @Autowired private lateinit var members: MemberRepository

    @Autowired private lateinit var entityManager: EntityManager

    @MockitoBean private lateinit var memberService: MemberService

    @Test
    fun `setting and clearing the flag persists without changing contact or other flags`() {
        val member =
            members.saveAndFlush(
                Member(lastName = "김", firstName = "테스트", email = "matrix-test@example.org", memberStatus = MemberStatus.ACTIVE)
                    .also { it.oneOnOneSignupFilled = true },
            )
        for (value in listOf(true, false)) {
            val response = service.setNextGroupLeader(member.publicId, value)
            members.flush()
            entityManager.clear()
            val saved = members.findByPublicIdAndDeletedAtIsNull(member.publicId).orElseThrow()
            assertEquals(value, response.isNextGroupLeader)
            assertEquals(value, saved.isNextGroupLeader)
            assertEquals("matrix-test@example.org", saved.email)
            assertEquals(true, saved.oneOnOneSignupFilled)
            assertEquals(MemberStatus.ACTIVE, saved.memberStatus)
        }
    }

    @Test
    fun `soft deleted and pending members cannot be changed through the scoped flag API`() {
        for (member in listOf(
            Member(lastName = "김", firstName = "테스트", memberStatus = MemberStatus.ACTIVE).also { it.deletedAt = Instant.now() },
            Member(lastName = "김", firstName = "테스트", memberStatus = MemberStatus.PENDING),
        )) {
            members.saveAndFlush(member)
            assertThrows<EntityNotFoundException> { service.setNextGroupLeader(member.publicId, true) }
            assertFalse(member.isNextGroupLeader)
        }
    }
}
