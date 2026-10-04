package com.hanmaum.dn.app.features.newcomers.repository

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerProfile
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerVisit
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.LocalDate

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class NewcomerVisitRepositoryIT {
    @Autowired lateinit var repository: NewcomerVisitRepository

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    private val sunday = LocalDate.of(2026, 6, 14)

    private fun visit(
        firstName: String,
        date: LocalDate = sunday,
        profile: NewcomerProfile? = null,
    ) = NewcomerVisit(visitDate = date, lastName = "홍", firstName = firstName, newcomerProfile = profile)
        .also { entityManager.persist(it) }

    @Test
    fun `findInRange keeps the range, newest day first, and drops soft-deleted rows`() {
        visit("길동", date = sunday.minusWeeks(1))
        visit("길순")
        visit("길자").deletedAt = Instant.now()
        visit("길남", date = sunday.minusWeeks(2))
        entityManager.flush()
        entityManager.clear()

        val rows = repository.findInRange(sunday.minusWeeks(1), sunday)

        assertEquals(listOf("길순", "길동"), rows.map { it.firstName })
    }

    @Test
    fun `names are stored encrypted`() {
        val row = visit("길동")
        entityManager.flush()

        val stored = jdbcTemplate.queryForObject("SELECT first_name FROM newcomer_visits WHERE id = ?", String::class.java, row.id)

        assertTrue(stored != "길동")
    }

    @Test
    fun `a purged newcomer profile leaves the visit without a link`() {
        val member = Member(lastName = "홍", firstName = "길동").also { entityManager.persist(it) }
        val profile = NewcomerProfile(member = member).also { entityManager.persist(it) }
        val row = visit("길동", profile = profile)
        entityManager.flush()
        entityManager.clear()
        jdbcTemplate.update("DELETE FROM newcomer_profiles WHERE id = ?", profile.id)

        val reloaded = repository.findActive(row.publicId).orElseThrow()

        assertNull(reloaded.newcomerProfile)
    }
}
