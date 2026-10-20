package com.hanmaum.dn.app.common.pii

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerProfile
import db.migration.V20261021100000__CopyNewcomerWorkOrSchoolToMembers
import jakarta.persistence.EntityManager
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.Configuration
import org.flywaydb.core.api.migration.Context
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
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
import kotlin.test.assertTrue

/**
 * Flyway has already applied the migration to the test database, so each test seeds legacy
 * values inside its rolled-back transaction and runs the migration against them again.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class CopyNewcomerWorkOrSchoolToMembersMigrationIT {
    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Autowired lateinit var dataSource: DataSource

    @Autowired lateinit var beanFactory: ConfigurableListableBeanFactory

    @Test
    fun `re-encrypts the legacy value onto the member and leaves the old column in place`() {
        val (memberId, profileId) = seed(memberValue = null, legacyValue = encryptLegacy("  TU Berlin  "))

        migrate()

        assertEquals("TU Berlin", memberRepository.findById(memberId).get().workOrSchool)
        assertEquals(PiiCryptoContext.storageKeyId(), memberColumn(memberId, "pii_key_id"))
        assertTrue(encryptedLegacyPresent(profileId))
    }

    @Test
    fun `keeps a value the member already has`() {
        val (memberId, _) = seed(memberValue = "Siemens", legacyValue = encryptLegacy("TU Berlin"))

        migrate()

        assertEquals("Siemens", memberRepository.findById(memberId).get().workOrSchool)
    }

    @Test
    fun `skips a blank legacy value`() {
        val (memberId, _) = seed(memberValue = null, legacyValue = encryptLegacy("   "))

        migrate()

        assertNull(memberRepository.findById(memberId).get().workOrSchool)
    }

    @Test
    fun `skips a soft-deleted newcomer profile`() {
        val (memberId, profileId) = seed(memberValue = null, legacyValue = encryptLegacy("TU Berlin"))
        jdbcTemplate.update("UPDATE newcomer_profiles SET deleted_at = now() WHERE id = ?", profileId)

        migrate()

        assertNull(memberRepository.findById(memberId).get().workOrSchool)
    }

    @Test
    fun `skips an undecryptable row and still copies the others`() {
        val (brokenMemberId, _) =
            seed(memberValue = null, legacyValue = PiiCryptoContext.encrypt("TU Berlin", "some.other_column")!!)
        val (memberId, _) = seed(memberValue = null, legacyValue = encryptLegacy("LMU München"))

        migrate()

        assertNull(memberRepository.findById(brokenMemberId).get().workOrSchool)
        assertEquals("LMU München", memberRepository.findById(memberId).get().workOrSchool)
    }

    @Test
    fun `flyway waits for the PII keyring`() {
        val piiConfiguration = beanFactory.getBeanNamesForType(PiiCryptoConfiguration::class.java).single()
        val flywayBeans = beanFactory.getBeanNamesForType(Flyway::class.java)

        assertTrue(flywayBeans.isNotEmpty())
        flywayBeans.forEach { name ->
            assertTrue(piiConfiguration in beanFactory.getBeanDefinition(name).dependsOn.orEmpty(), name)
        }
    }

    private fun encryptLegacy(value: String): String =
        PiiCryptoContext.encrypt(value, V20261021100000__CopyNewcomerWorkOrSchoolToMembers.LEGACY_CONTEXT)!!

    private fun seed(
        memberValue: String?,
        legacyValue: String,
    ): Pair<Long, Long> {
        val member = Member(lastName = "김", firstName = "새봄", workOrSchool = memberValue)
        entityManager.persist(member)
        val profile = NewcomerProfile(member = member)
        entityManager.persist(profile)
        entityManager.flush()
        jdbcTemplate.update("UPDATE newcomer_profiles SET work_or_school = ? WHERE id = ?", legacyValue, profile.id)
        entityManager.clear()
        return member.id!! to profile.id!!
    }

    private fun migrate() {
        val connection = DataSourceUtils.getConnection(dataSource)
        V20261021100000__CopyNewcomerWorkOrSchoolToMembers().migrate(
            object : Context {
                override fun getConfiguration(): Configuration = throw UnsupportedOperationException()

                override fun getConnection(): Connection = connection
            },
        )
        entityManager.clear()
    }

    private fun memberColumn(
        memberId: Long,
        column: String,
    ): String? = jdbcTemplate.queryForObject("SELECT $column FROM members WHERE id = ?", String::class.java, memberId)

    private fun encryptedLegacyPresent(profileId: Long): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT work_or_school IS NOT NULL FROM newcomer_profiles WHERE id = ?",
            Boolean::class.java,
            profileId,
        )!!
}
