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
    fun `import fingerprints match the JPA varchar mapping after Flyway migration`() {
        val columns =
            jdbcTemplate
                .query(
                    """
                    SELECT column_name, data_type, character_maximum_length
                    FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'newcomer_import_records'
                      AND column_name IN ('source_fingerprint', 'payload_fingerprint')
                    """.trimIndent(),
                ) { resultSet, _ ->
                    resultSet.getString("column_name") to
                        (resultSet.getString("data_type") to resultSet.getInt("character_maximum_length"))
                }.toMap()

        assertEquals(
            mapOf(
                "source_fingerprint" to ("character varying" to 64),
                "payload_fingerprint" to ("character varying" to 64),
            ),
            columns,
        )
    }

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
