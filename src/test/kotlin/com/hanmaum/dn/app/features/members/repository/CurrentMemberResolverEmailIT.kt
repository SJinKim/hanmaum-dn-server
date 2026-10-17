package com.hanmaum.dn.app.features.members.repository

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.service.CurrentMemberResolver
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class, CurrentMemberResolver::class)
@Tag("integration")
class CurrentMemberResolverEmailIT {
    @Autowired lateinit var members: MemberRepository

    @Autowired lateinit var resolver: CurrentMemberResolver

    @Autowired lateinit var entityManager: EntityManager

    @Test
    fun `verified email survives reload after the dashboard member was soft deleted`() {
        val deleted =
            members.saveAndFlush(
                Member(lastName = "Test", firstName = "Member", email = "recovery@example.com").apply {
                    deletedAt = Instant.now()
                },
            )
        val registration =
            members.saveAndFlush(Member(lastName = "Test", firstName = "Member", keycloakId = "recovery-subject"))
        entityManager.clear()

        resolver.resolveAndLink("recovery-subject", "recovery@example.com", true)
        entityManager.flush()
        entityManager.clear()

        val reloaded = members.findByKeycloakIdAndDeletedAtIsNull("recovery-subject")!!
        assertEquals(registration.id, reloaded.id)
        assertEquals("recovery@example.com", reloaded.email)
        assertEquals(registration.id, members.findByEmailAndDeletedAtIsNull("recovery@example.com")?.id)
        assertEquals("recovery@example.com", members.findById(deleted.id!!).orElseThrow().email)
    }

    @Test
    fun `email owned by a live member stays with that member and opens a conflict`() {
        val existing =
            members.saveAndFlush(
                Member(lastName = "Test", firstName = "Other", email = "occupied@example.com", birthDate = LocalDate.of(1990, 1, 1)),
            )
        val registration =
            members.saveAndFlush(
                Member(lastName = "Test", firstName = "Member", keycloakId = "occupied-subject", birthDate = LocalDate.of(2000, 1, 1)),
            )
        entityManager.clear()

        resolver.resolveAndLink("occupied-subject", "occupied@example.com", true)
        entityManager.flush()
        entityManager.clear()

        val reloaded = members.findByKeycloakIdAndDeletedAtIsNull("occupied-subject")!!
        assertEquals(registration.id, reloaded.id)
        assertNull(reloaded.email)
        assertEquals(existing.id, members.findByEmailAndDeletedAtIsNull("occupied@example.com")?.id)
        assertEquals(
            1L,
            entityManager.createQuery("SELECT COUNT(c) FROM MemberClaimConflict c", Long::class.javaObjectType).singleResult,
        )
    }
}
