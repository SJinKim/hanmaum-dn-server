package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.attendance.LongAbsenceFixtures
import com.hanmaum.dn.app.features.attendance.MutableClock
import com.hanmaum.dn.app.features.attendance.NAMED_SERVICE_ID
import com.hanmaum.dn.app.features.attendance.NAMED_SERVICE_PROPERTY
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceMemoRequest
import com.hanmaum.dn.app.features.attendance.berlin
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * HDN-375 writes in real, committed PostgreSQL transactions: the member lock orders a
 * follow-up write against a check-in and against a second first write of the same phase.
 */
@DataJpaTest(properties = ["spring.jpa.show-sql=false", NAMED_SERVICE_PROPERTY])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
@Import(
    PiiCryptoConfiguration::class,
    LongAbsenceService::class,
    LongAbsenceRetentionService::class,
    SundayAttendancePolicy::class,
    LongAbsenceConcurrencyIT.ClockConfig::class,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LongAbsenceConcurrencyIT {
    @TestConfiguration
    class ClockConfig {
        @Bean
        fun clock() = MutableClock(berlin("2026-10-26", "10:00"))
    }

    @Autowired private lateinit var service: LongAbsenceService

    @Autowired private lateinit var retention: LongAbsenceRetentionService

    @Autowired private lateinit var members: MemberRepository

    @Autowired private lateinit var entityManager: EntityManager

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val tx by lazy { TransactionTemplate(transactionManager) }
    private val created = mutableListOf<Long>()

    private fun absentee(): Member =
        tx
            .execute {
                val fx = LongAbsenceFixtures(entityManager, jdbcTemplate)
                fx.member().also { fx.log(it, "2026-01-04") }
            }!!
            .also { created += it.id!! }

    private fun memoRequest(version: Long) = LongAbsenceMemoRequest(LocalDate.parse("2026-01-04"), version, "메모")

    @AfterEach
    fun cleanUp() {
        created.forEach { id ->
            jdbcTemplate.update("DELETE FROM attendance_absence_followups WHERE member_id = ?", id)
            jdbcTemplate.update("DELETE FROM attendance_logs WHERE member_id = ?", id)
            jdbcTemplate.update("DELETE FROM members WHERE id = ?", id)
        }
        jdbcTemplate.update("DELETE FROM attendance_definitions WHERE public_id = ?", NAMED_SERVICE_ID)
        jdbcTemplate.update(
            "DELETE FROM church_groups WHERE id NOT IN (SELECT group_id FROM members WHERE group_id IS NOT NULL) AND name LIKE '1순-%'",
        )
    }

    @Test
    fun `a follow-up write waits for a running check-in and is then rejected for the ended phase`() {
        val member = absentee()
        val checkedIn = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            // What the check-in writers do: member lock, insert, close returned phases, commit.
            val checkIn =
                pool.submit {
                    tx.executeWithoutResult {
                        members.findForUpdateByPublicIdAndDeletedAtIsNull(member.publicId).orElseThrow()
                        jdbcTemplate.update(
                            "INSERT INTO attendance_logs " +
                                "(public_id, definition_id, member_id, attendance_date, attended, presence, created_at) " +
                                "SELECT ?, id, ?, DATE '2026-07-05', TRUE, 'UNCONFIRMED', TIMESTAMPTZ '2026-07-05 10:00:00+02' " +
                                "FROM attendance_definitions WHERE public_id = ?",
                            UUID.randomUUID(),
                            member.id,
                            NAMED_SERVICE_ID,
                        )
                        retention.markReturned(member.id!!)
                        checkedIn.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                    }
                }
            assertTrue(checkedIn.await(10, TimeUnit.SECONDS))
            val write =
                pool.submit(
                    Callable {
                        runCatching { service.setMemo(member.publicId, memoRequest(0)) }.exceptionOrNull()
                    },
                )
            assertFailsWith<TimeoutException> { write.get(300, TimeUnit.MILLISECONDS) }
            release.countDown()
            checkIn.get(10, TimeUnit.SECONDS)

            val failure = write.get(10, TimeUnit.SECONDS)
            assertTrue(failure is LongAbsenceException)
            assertEquals(LongAbsenceException.NOT_CURRENT, failure.code)
            assertEquals(0, followups(member))
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `two first writes of the same phase leave one row and one version conflict`() {
        val member = absentee()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results =
                (1..2)
                    .map {
                        pool.submit(
                            Callable {
                                check(start.await(10, TimeUnit.SECONDS))
                                runCatching { service.setMemo(member.publicId, memoRequest(0)) }
                            },
                        )
                    }.also { start.countDown() }
                    .map { it.get(10, TimeUnit.SECONDS) }

            assertEquals(1, results.count { it.isSuccess })
            val conflict = results.single { it.isFailure }.exceptionOrNull() as LongAbsenceException
            assertEquals(LongAbsenceException.VERSION_CONFLICT, conflict.code)
            assertEquals(1, followups(member))
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private fun followups(member: Member): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM attendance_absence_followups WHERE member_id = ?", Int::class.java, member.id)!!
}
