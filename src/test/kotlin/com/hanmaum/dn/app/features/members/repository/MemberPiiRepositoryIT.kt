package com.hanmaum.dn.app.features.members.repository

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class MemberPiiRepositoryIT {
    @Autowired lateinit var repository: MemberRepository

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `persists ciphertext while returning plaintext through JPA`() {
        val member =
            repository.saveAndFlush(
                Member(
                    lastName = "김",
                    firstName = "철수",
                    email = "member@example.com",
                    city = "Berlin",
                ),
            )
        entityManager.clear()

        val raw =
            jdbcTemplate.queryForMap(
                "SELECT last_name, first_name, email, city FROM members WHERE id = ?",
                member.id,
            )
        assertFalse(raw.values.any { value -> value.toString().contains("member@example.com") })
        assertFalse(raw.values.any { value -> value.toString().contains("Berlin") })
        assertFalse(raw.values.any { value -> value.toString().contains("철수") })

        val reloaded = repository.findByEmailAndDeletedAtIsNull(" MEMBER@example.com ")
        assertEquals("철수", reloaded?.firstName)
        assertEquals("Berlin", reloaded?.city)
    }

    @Test
    fun `encrypted member search preserves substring matching and stable name ordering`() {
        repository.saveAllAndFlush(
            listOf(
                Member(lastName = "Zimmer", firstName = "Anna", email = "anna@example.com"),
                Member(lastName = "Kim", firstName = "Min", email = "min@example.com"),
                Member(lastName = "Kim", firstName = "Ara", email = "ara@example.com"),
            ),
        )
        entityManager.clear()

        val page =
            repository.findActiveMembers(
                search = "im",
                status = null,
                baptism = null,
                pageable = PageRequest.of(0, 20),
            )

        assertEquals(
            listOf("Ara", "Min", "Anna"),
            page.content.map(Member::firstName),
        )
    }
}
