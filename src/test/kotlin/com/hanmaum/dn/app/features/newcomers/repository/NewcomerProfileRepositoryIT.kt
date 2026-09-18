package com.hanmaum.dn.app.features.newcomers.repository

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerProfile
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class NewcomerProfileRepositoryIT {
    @Autowired lateinit var repository: NewcomerProfileRepository

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `profile is one-to-one with member and encrypts newcomer PII`() {
        val member = Member(lastName = "김", firstName = "새봄")
        entityManager.persist(member)
        val profile =
            repository.saveAndFlush(
                NewcomerProfile(
                    member = member,
                    englishName = "Saebom Kim",
                    firstVisitDate = LocalDate.of(2026, 9, 18),
                    additionalNotes = "private note",
                ),
            )
        entityManager.clear()

        val raw =
            jdbcTemplate.queryForMap(
                "SELECT english_name, first_visit_date, additional_notes FROM newcomer_profiles WHERE id = ?",
                profile.id,
            )
        assertFalse(raw.values.any { it.toString().contains("Saebom") })
        assertFalse(raw.values.any { it.toString().contains("2026-09-18") })
        assertFalse(raw.values.any { it.toString().contains("private note") })
        assertEquals(LocalDate.of(2026, 9, 18), repository.findByPublicIdAndDeletedAtIsNull(profile.publicId).get().firstVisitDate)

        assertThrows<DataIntegrityViolationException> {
            repository.saveAndFlush(NewcomerProfile(member = member))
        }
    }
}
