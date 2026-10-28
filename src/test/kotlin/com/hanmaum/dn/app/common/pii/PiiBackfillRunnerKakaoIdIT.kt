package com.hanmaum.dn.app.common.pii

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 카카오 아이디 is an encrypted member column, so the backfill has to see it: without it in
 * ENCRYPTED_COLUMNS a member whose only legacy value is `members.kakao_id` is reported as
 * `remaining=0` and silently skipped (HDN-360).
 *
 * Detection alone is not enough — the runner also has to write the value back, which is what
 * these tests assert on the raw column.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class PiiBackfillRunnerKakaoIdIT {
    @Autowired lateinit var properties: PiiProperties

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Autowired lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `re-encrypts a member whose only legacy value is the kakao id`() {
        val memberId = seedPlaintext(memberColumn = "kakao_id", value = "saebom.kim")

        runner(backfillEnabled = true).run(DefaultApplicationArguments())

        val stored = memberColumn(memberId, "kakao_id")!!
        assertTrue(stored.startsWith("ENC1\$${PiiCryptoContext.activeKeyId()}\$"), "not re-encrypted: $stored")
        assertEquals("saebom.kim", memberRepository.findById(memberId).get().kakaoId)
    }

    /** `@PreUpdate` keeps the lookup hash in step with the value; a direct write has to as well. */
    @Test
    fun `restores the lookup hash of a re-encrypted email`() {
        val memberId = seedPlaintext(memberColumn = "email", value = "saebom.kim@example.com")
        jdbcTemplate.update("UPDATE members SET email_lookup_hash = NULL WHERE id = ?", memberId)

        runner(backfillEnabled = true).run(DefaultApplicationArguments())

        assertTrue(memberColumn(memberId, "email")!!.startsWith("ENC1\$"))
        assertEquals(PiiCryptoContext.lookupHash("saebom.kim@example.com"), memberColumn(memberId, "email_lookup_hash"))
        assertNotNull(memberColumn(memberId, "pii_key_id"))
    }

    @Test
    fun `aborts instead of leaving a plaintext kakao id behind`() {
        seedPlaintext(memberColumn = "kakao_id", value = "gildong.hong")

        val failure =
            assertFailsWith<IllegalStateException> {
                runner(backfillEnabled = false).run(DefaultApplicationArguments())
            }

        assertEquals("Found plaintext PII while app.pii.backfill-enabled=false.", failure.message)
    }

    /** Proves the abort above comes from the seeded row and not from leftover data. */
    @Test
    fun `stays quiet when every kakao id is already encrypted`() {
        val member = Member(lastName = "김", firstName = "새봄", kakaoId = "office.handle")
        entityManager.persist(member)
        entityManager.flush()
        entityManager.clear()

        runner(backfillEnabled = false).run(DefaultApplicationArguments())
    }

    /**
     * Writes the plaintext past the converters, the way a row written before encryption or under an
     * older key looks in the database.
     */
    private fun seedPlaintext(
        memberColumn: String,
        value: String,
    ): Long {
        val member = Member(lastName = "김", firstName = "새봄")
        entityManager.persist(member)
        entityManager.flush()
        jdbcTemplate.update("UPDATE members SET $memberColumn = ? WHERE id = ?", value, member.id)
        entityManager.clear()
        return member.id!!
    }

    private fun runner(backfillEnabled: Boolean) =
        PiiBackfillRunner(
            properties = properties.copy(backfillEnabled = backfillEnabled),
            jdbcTemplate = jdbcTemplate,
            transactionTemplate = TransactionTemplate(transactionManager),
        )

    private fun memberColumn(
        memberId: Long,
        column: String,
    ): String? = jdbcTemplate.queryForObject("SELECT $column FROM members WHERE id = ?", String::class.java, memberId)
}
