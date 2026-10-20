package com.hanmaum.dn.app.features.guide.service

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailsUpdateRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideSummaryConfirmation
import com.hanmaum.dn.app.features.guide.api.v1.dto.WorshipCountRequest
import com.hanmaum.dn.app.features.guide.guideYouthExample
import com.hanmaum.dn.app.features.guide.repository.GuideWorshipDetailRepository
import com.hanmaum.dn.app.features.guide.repository.WorshipCountRepository
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DataJpaTest(properties = ["spring.jpa.show-sql=false"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
@Import(PiiCryptoConfiguration::class, GuideDetailsService::class, GuideDetailsCalculator::class, WorshipCountService::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GuideDetailsConcurrencyIT {
    @Autowired private lateinit var details: GuideDetailsService

    @Autowired private lateinit var summaries: WorshipCountService

    @Autowired private lateinit var reports: WorshipCountRepository

    @Autowired private lateinit var rows: GuideWorshipDetailRepository

    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    @Test fun `legacy summary writer waits for detail conversion then rejects its stale version without lost updates`() {
        val date = LocalDate.of(2088, 1, 4)
        val original = summaries.create(WorshipCountRequest(date, 10, 20, 30), "legacy")
        val written = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val tx = TransactionTemplate(transactionManager)
        try {
            val conversion =
                pool.submit {
                    tx.executeWithoutResult {
                        details.update(
                            original.publicId,
                            GuideDetailsUpdateRequest(
                                date,
                                null,
                                original.version,
                                guideYouthExample(),
                                GuideSummaryConfirmation(10, 20, 30),
                            ),
                            "guide-editor",
                        )
                        written.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                    }
                }
            assertTrue(written.await(10, TimeUnit.SECONDS))
            val legacy =
                pool.submit<GuideException?> {
                    secondStarted.countDown()
                    try {
                        summaries.update(original.publicId, WorshipCountRequest(date, 9, 9, 9, version = original.version), "legacy")
                        null
                    } catch (exception: GuideException) {
                        exception
                    }
                }
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
            assertFailsWith<TimeoutException> { legacy.get(200, TimeUnit.MILLISECONDS) }
            release.countDown()
            conversion.get(10, TimeUnit.SECONDS)
            assertEquals(HttpStatus.CONFLICT, legacy.get(10, TimeUnit.SECONDS)?.status)
            val saved = details.get(original.publicId)
            assertEquals("DETAILED", saved.report.reportMode)
            assertEquals(70L, saved.report.totalCount)
            assertEquals(65L, saved.totals?.compatibleAdultCount)
            assertEquals(12, rows.findAllByReportPublicId(original.publicId).size)
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
            tx.executeWithoutResult {
                reports.findByPublicIdAndDeletedAtIsNull(original.publicId)?.let(reports::delete)
                reports.flush()
            }
        }
    }
}
