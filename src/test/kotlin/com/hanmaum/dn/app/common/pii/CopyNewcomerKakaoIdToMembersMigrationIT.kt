package com.hanmaum.dn.app.common.pii

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerProfile
import db.migration.V20261029120100__CopyNewcomerKakaoIdToMembers
import jakarta.persistence.EntityManager
import org.flywaydb.core.api.configuration.Configuration
import org.flywaydb.core.api.migration.Context
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
import kotlin.test.assertTrue

/**
 * Flyway has already applied the migration to the test database, and V20261029120200 has dropped
 * the legacy column since. Each test re-adds the column and seeds legacy values inside its
 * rolled-back transaction, then runs the migration against them again (HDN-360).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class CopyNewcomerKakaoIdToMembersMigrationIT {
    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Autowired lateinit var dataSource: DataSource

    @Test
    fun `re-encrypts the legacy value onto the member and leaves the old column in place`() {
        val (memberId, profileId) = seed(memberValue = null, legacyValue = encryptLegacy("  saebom.kim  "))

        migrate()

        assertEquals("saebom.kim", memberRepository.findById(memberId).get().kakaoId)
        assertEquals(PiiCryptoContext.storageKeyId(), memberColumn(memberId, "pii_key_id"))
        assertTrue(encryptedLegacyPresent(profileId))
    }

    @Test
    fun `keeps a value the member already has`() {
        val (memberId, _) = seed(memberValue = "office.handle", legacyValue = encryptLegacy("saebom.kim"))

        migrate()

        assertEquals("office.handle", memberRepository.findById(memberId).get().kakaoId)
    }

    @Test
    fun `skips a blank legacy value`() {
        val (memberId, _) = seed(memberValue = null, legacyValue = encryptLegacy("   "))

        migrate()

        assertNull(memberRepository.findById(memberId).get().kakaoId)
    }

    @Test
    fun `skips a soft-deleted newcomer profile`() {
        val (memberId, profileId) = seed(memberValue = null, legacyValue = encryptLegacy("saebom.kim"))
        jdbcTemplate.update("UPDATE newcomer_profiles SET deleted_at = now() WHERE id = ?", profileId)

        migrate()

        assertNull(memberRepository.findById(memberId).get().kakaoId)
    }

    @Test
    fun `skips an undecryptable row and still copies the others`() {
        val (broken, readable) =
            seed(
                null to PiiCryptoContext.encrypt("saebom.kim", "some.other_column")!!,
                null to encryptLegacy("gildong.hong"),
            )

        migrate()

        assertNull(memberRepository.findById(broken.first).get().kakaoId)
        assertEquals("gildong.hong", memberRepository.findById(readable.first).get().kakaoId)
    }

    @Test
    fun `the legacy newcomer column is dropped`() {
        val remaining =
            jdbcTemplate.queryForList(
                """
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'newcomer_profiles' AND column_name = 'kakao_id'
                """.trimIndent(),
                String::class.java,
            )

        assertTrue(remaining.isEmpty(), remaining.toString())
    }

    private fun encryptLegacy(value: String): String =
        PiiCryptoContext.encrypt(value, V20261029120100__CopyNewcomerKakaoIdToMembers.LEGACY_CONTEXT)!!

    private fun seed(
        memberValue: String?,
        legacyValue: String,
    ): Pair<Long, Long> = seed(memberValue to legacyValue).single()

    /**
     * Inserts every row before re-adding the column: an insert prepared against the table before
     * the ALTER fails afterwards with "cached plan must not change result type".
     */
    private fun seed(vararg rows: Pair<String?, String>): List<Pair<Long, Long>> {
        val seeded =
            rows.map { (memberValue, legacyValue) ->
                val member = Member(lastName = "김", firstName = "새봄", kakaoId = memberValue)
                entityManager.persist(member)
                val profile = NewcomerProfile(member = member)
                entityManager.persist(profile)
                Triple(member, profile, legacyValue)
            }
        entityManager.flush()
        jdbcTemplate.execute("ALTER TABLE newcomer_profiles ADD COLUMN IF NOT EXISTS kakao_id TEXT")
        seeded.forEach { (_, profile, legacyValue) ->
            jdbcTemplate.update("UPDATE newcomer_profiles SET kakao_id = ? WHERE id = ?", legacyValue, profile.id)
        }
        entityManager.clear()
        return seeded.map { (member, profile, _) -> member.id!! to profile.id!! }
    }

    private fun migrate() {
        val connection = DataSourceUtils.getConnection(dataSource)
        V20261029120100__CopyNewcomerKakaoIdToMembers().migrate(
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
            "SELECT kakao_id IS NOT NULL FROM newcomer_profiles WHERE id = ?",
            Boolean::class.java,
            profileId,
        )!!
}
