package com.hanmaum.dn.app.common.pii

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerProfile
import db.migration.V20261021100000__DropNewcomerLegacyIdentityColumns
import jakarta.persistence.EntityManager
import org.flywaydb.core.api.configuration.Configuration
import org.flywaydb.core.api.migration.Context
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.test.context.ActiveProfiles
import java.sql.Connection
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Flyway has already applied the migration to the test database, so each test re-adds the
 * legacy columns inside its rolled-back transaction and runs the migration against them.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class DropNewcomerLegacyIdentityColumnsMigrationIT {
    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Autowired lateinit var dataSource: DataSource

    @BeforeEach
    fun restoreLegacyColumns() {
        jdbcTemplate.execute(
            "ALTER TABLE newcomer_profiles ADD COLUMN identity_status VARCHAR(30), ADD COLUMN work_or_school TEXT",
        )
    }

    @Test
    fun `re-encrypts the legacy value onto the member and drops the columns`() {
        val memberId = seed(memberValue = null, legacyValue = "  TU Berlin  ")

        migrate()

        assertEquals("TU Berlin", memberRepository.findById(memberId).get().workOrSchool)
        assertEquals(0, legacyColumnCount())
    }

    @Test
    fun `keeps a value the member already has`() {
        val memberId = seed(memberValue = "Siemens", legacyValue = "TU Berlin")

        migrate()

        assertEquals("Siemens", memberRepository.findById(memberId).get().workOrSchool)
    }

    @Test
    fun `skips a blank legacy value`() {
        val memberId = seed(memberValue = null, legacyValue = "   ")

        migrate()

        assertNull(memberRepository.findById(memberId).get().workOrSchool)
    }

    private fun seed(
        memberValue: String?,
        legacyValue: String,
    ): Long {
        val member = Member(lastName = "김", firstName = "새봄", workOrSchool = memberValue)
        entityManager.persist(member)
        val profile = NewcomerProfile(member = member)
        entityManager.persist(profile)
        entityManager.flush()
        jdbcTemplate.update(
            "UPDATE newcomer_profiles SET work_or_school = ? WHERE id = ?",
            PiiCryptoContext.encrypt(legacyValue, "newcomer_profiles.work_or_school"),
            profile.id,
        )
        entityManager.clear()
        return member.id!!
    }

    private fun migrate() {
        val connection = DataSourceUtils.getConnection(dataSource)
        V20261021100000__DropNewcomerLegacyIdentityColumns().migrate(
            object : Context {
                override fun getConfiguration(): Configuration = throw UnsupportedOperationException()

                override fun getConnection(): Connection = connection
            },
        )
        entityManager.clear()
    }

    private fun legacyColumnCount(): Int =
        jdbcTemplate.queryForObject(
            """
            SELECT count(*) FROM information_schema.columns
            WHERE table_name = 'newcomer_profiles' AND column_name IN ('identity_status', 'work_or_school')
            """.trimIndent(),
            Int::class.java,
        )!!
}
