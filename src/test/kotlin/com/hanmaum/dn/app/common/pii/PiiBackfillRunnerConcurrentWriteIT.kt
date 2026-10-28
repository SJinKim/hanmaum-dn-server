package com.hanmaum.dn.app.common.pii

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A re-encryption must not undo a business change (HDN-360). The runner reads a batch and writes
 * it back; if another writer commits the same value in between, writing the snapshot would restore
 * the old value.
 *
 * Needs real commits from two connections, so the test runs outside the usual rollback
 * transaction and removes its row itself.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Tag("integration")
class PiiBackfillRunnerConcurrentWriteIT {
    @Autowired lateinit var properties: PiiProperties

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Autowired lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `keeps a value another writer committed while the batch was being rewritten`() {
        val member = memberRepository.saveAndFlush(Member(lastName = "김", firstName = "새봄"))
        val memberId = member.id!!
        jdbcTemplate.update("UPDATE members SET kakao_id = ? WHERE id = ?", "old-handle", memberId)
        val executor = Executors.newSingleThreadExecutor()
        try {
            // The other writer holds the row, changes the value and only then lets go.
            val run =
                TransactionTemplate(transactionManager).execute {
                    jdbcTemplate.queryForList("SELECT id FROM members WHERE id = ? FOR UPDATE", memberId)
                    val pending = executor.submit { runner().run(DefaultApplicationArguments()) }
                    awaitBlockedConnection()
                    jdbcTemplate.update("UPDATE members SET kakao_id = ? WHERE id = ?", "new-handle", memberId)
                    pending // the commit happens when this block returns; the runner proceeds afterwards
                }!!
            run.get(15, TimeUnit.SECONDS)

            val stored = jdbcTemplate.queryForObject("SELECT kakao_id FROM members WHERE id = ?", String::class.java, memberId)!!
            assertTrue(stored.startsWith("ENC1\$${PiiCryptoContext.activeKeyId()}\$"), "not re-encrypted: $stored")
            assertEquals("new-handle", PiiCryptoContext.decrypt(stored, "members.kakao_id"))
        } finally {
            executor.shutdownNow()
            jdbcTemplate.update("DELETE FROM members WHERE id = ?", memberId)
        }
    }

    private fun awaitBlockedConnection() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val waiting =
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND datname = current_database()",
                    Long::class.java,
                ) ?: 0
            if (waiting > 0) {
                return
            }
            Thread.sleep(50)
        }
        error("backfill runner never waited on the row lock")
    }

    private fun runner() =
        PiiBackfillRunner(
            properties = properties.copy(backfillEnabled = true),
            jdbcTemplate = jdbcTemplate,
            transactionTemplate = TransactionTemplate(transactionManager),
        )
}
