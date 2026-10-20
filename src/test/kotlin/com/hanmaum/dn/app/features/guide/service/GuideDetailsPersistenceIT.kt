package com.hanmaum.dn.app.features.guide.service

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailsCreateRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailsUpdateRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideSummaryConfirmation
import com.hanmaum.dn.app.features.guide.api.v1.dto.WorshipCountRequest
import com.hanmaum.dn.app.features.guide.guideYouthExample
import com.hanmaum.dn.app.features.guide.guideZeroRows
import com.hanmaum.dn.app.features.guide.repository.GuideSummaryConversionRepository
import com.hanmaum.dn.app.features.guide.repository.GuideWorshipDetailRepository
import com.hanmaum.dn.app.features.guide.repository.WorshipCountRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DataJpaTest(properties = ["spring.jpa.show-sql=false"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
@Import(PiiCryptoConfiguration::class, GuideDetailsService::class, GuideDetailsCalculator::class, WorshipCountService::class)
class GuideDetailsPersistenceIT {
    @Autowired private lateinit var service: GuideDetailsService

    @Autowired private lateinit var summaries: WorshipCountService

    @Autowired private lateinit var reports: WorshipCountRepository

    @Autowired private lateinit var details: GuideWorshipDetailRepository

    @Autowired private lateinit var conversions: GuideSummaryConversionRepository

    @Autowired private lateinit var em: EntityManager
    private val date = LocalDate.of(2026, 10, 25)

    @Test fun `detail create persists one parent and twelve unique rows consumed by summary history`() {
        summaries.create(WorshipCountRequest(date.minusWeeks(1), 20, 20, 20), "legacy")
        val created = service.create(GuideDetailsCreateRequest(date, "detail note", guideYouthExample()), "editor")
        em.clear()
        val saved = service.get(created.report.publicId)
        assertEquals("DETAILED", saved.report.reportMode)
        assertEquals(listOf(0, 0, 70), listOf(saved.report.service1Count, saved.report.service2Count, saved.report.service3Count))
        assertEquals(70L, saved.totals?.totalCount)
        assertEquals(65L, saved.totals?.compatibleAdultCount)
        assertEquals(10L, saved.report.previousWeekDelta)
        assertEquals(12L, details.count())
        assertEquals(2L, reports.count())
        val history = summaries.list(date, date, 0, 20, "serviceDate,desc").content.single()
        assertEquals(saved.report, history)
        assertEquals("detail note", history.notes)
        assertEquals(0L, conversions.count())
    }

    @Test fun `reading historical summary invents no detail values and conversion requires explicit matching comparison`() {
        val original = summaries.create(WorshipCountRequest(date, 10, 20, 30, "original note"), "legacy")
        repeat(2) {
            val read = service.get(original.publicId)
            assertEquals("SUMMARY_ONLY", read.report.reportMode)
            assertEquals(emptyList(), read.entries)
            assertNull(read.totals)
            assertEquals(0L, details.count())
        }
        assertEquals(original, summaries.get(original.publicId))
        val request = GuideDetailsUpdateRequest(date, "original note", original.version, guideYouthExample())
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<GuideException> { service.update(original.publicId, request, "editor") }.status)
        assertFailsWith<GuideException> {
            service.update(original.publicId, request.copy(expectedSummary = GuideSummaryConfirmation(1, 2, 3)), "editor")
        }
        assertEquals(0L, conversions.count())
        assertEquals(original, summaries.get(original.publicId))
        val converted = service.update(original.publicId, request.copy(expectedSummary = GuideSummaryConfirmation(10, 20, 30)), "editor")
        em.clear()
        assertEquals(original.publicId, converted.report.publicId)
        assertEquals("DETAILED", converted.report.reportMode)
        assertEquals(70L, summaries.get(original.publicId).totalCount)
        assertEquals(1L, reports.count())
        val audit = conversions.findAll().single()
        assertEquals(listOf(10, 20, 30), listOf(audit.sourceService1Count, audit.sourceService2Count, audit.sourceService3Count))
        assertEquals("original note", audit.sourceNotes)
        assertEquals(date, audit.sourceDate)
        assertEquals(original.version, audit.sourceVersion)
        assertEquals("editor", audit.convertedBy)
        assertNotNull(audit.convertedAt)
    }

    @Test fun `same-total redistribution advances parent version and rejects stale detail and direct summary writes`() {
        val initial = service.create(GuideDetailsCreateRequest(date, entries = guideYouthExample()), "editor")
        val moved =
            guideYouthExample().map {
                when {
                    it.roomCode == "MAIN_1F_A" && it.serviceNumber == 3 -> it.copy(youthCount = 0)
                    it.roomCode == "MAIN_1F_B" && it.serviceNumber == 3 -> it.copy(youthCount = 25)
                    else -> it
                }
            }
        val request = GuideDetailsUpdateRequest(date, null, initial.report.version, moved)
        val updated = service.update(initial.report.publicId, request, "editor")
        assertEquals(initial.report.version + 1, updated.report.version)
        assertEquals(70L, updated.report.totalCount)
        em.clear()
        assertEquals(
            25L,
            service
                .get(initial.report.publicId)
                .entries
                .single {
                    it.roomCode == "MAIN_1F_B" && it.serviceNumber == 3
                }.counts.youthCount,
        )
        assertFailsWith<GuideException> { service.update(initial.report.publicId, request, "editor") }
        assertFailsWith<GuideException> {
            summaries.update(initial.report.publicId, WorshipCountRequest(date, 9, 9, 9, version = updated.report.version), "editor")
        }
        assertEquals(updated.report, summaries.get(initial.report.publicId))
        assertEquals(12L, details.count())
    }

    @Test fun `empty draft and explicit zero remain distinct and an incomplete update clears canonical dependent sums`() {
        val draft = service.create(GuideDetailsCreateRequest(date), "editor")
        assertNull(draft.report.totalCount)
        assertEquals(false, draft.report.complete)
        assertEquals(12L, details.count())
        val zeros =
            service.update(
                draft.report.publicId,
                GuideDetailsUpdateRequest(date, null, draft.report.version, guideZeroRows()),
                "editor",
            )
        assertEquals(0L, zeros.report.totalCount)
        assertEquals(true, zeros.report.complete)
        val partial = guideZeroRows().map { if (it.roomCode == "PRESCHOOL" && it.serviceNumber == 2) it.copy(schoolCount = null) else it }
        val result = service.update(draft.report.publicId, GuideDetailsUpdateRequest(date, null, zeros.report.version, partial), "editor")
        em.clear()
        assertNull(result.report.service2Count)
        assertEquals(0, result.report.service1Count)
        assertEquals(0, result.report.service3Count)
        assertNull(summaries.get(draft.report.publicId).totalCount)
        assertEquals(12L, details.count())
    }

    @Test fun `invalid matrix overflow duplicate date and conversion data on detail mode leave stored rows unchanged`() {
        val initial = service.create(GuideDetailsCreateRequest(date, entries = guideYouthExample()), "editor")
        val overflow =
            guideYouthExample().map {
                if (it.roomCode == "MAIN_1F_A" &&
                    it.serviceNumber == 3
                ) {
                    it.copy(adultCount = Int.MAX_VALUE)
                } else {
                    it
                }
            }
        assertFailsWith<GuideDetailValidationException> {
            service.update(initial.report.publicId, GuideDetailsUpdateRequest(date, null, initial.report.version, overflow), "editor")
        }
        assertFailsWith<GuideException> { service.create(GuideDetailsCreateRequest(date), "editor") }
        summaries.create(WorshipCountRequest(date.plusWeeks(1), 1, 2, 3), "editor")
        assertFailsWith<GuideException> {
            service.update(
                initial.report.publicId,
                GuideDetailsUpdateRequest(date.plusWeeks(1), null, initial.report.version, guideZeroRows()),
                "editor",
            )
        }
        assertFailsWith<GuideDetailValidationException> {
            service.update(
                initial.report.publicId,
                GuideDetailsUpdateRequest(date, null, initial.report.version, guideZeroRows(), GuideSummaryConfirmation(0, 0, 70)),
                "editor",
            )
        }
        em.clear()
        assertEquals(initial, service.get(initial.report.publicId))
        assertEquals(12L, details.count())
        assertEquals(0L, conversions.count())
    }

    @Test fun `soft-deleted detail report is hidden in both readers and rejects further writes`() {
        val created = service.create(GuideDetailsCreateRequest(date, entries = guideYouthExample()), "editor")
        summaries.delete(created.report.publicId, created.report.version, "editor")
        em.clear()
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<GuideException> { service.get(created.report.publicId) }.status)
        assertFailsWith<GuideException> {
            service.update(
                created.report.publicId,
                GuideDetailsUpdateRequest(date, null, created.report.version, guideZeroRows()),
                "editor",
            )
        }
        assertEquals(0L, summaries.list(null, null, 0, 20, "serviceDate,desc").totalElements)
        assertEquals(12L, details.count())
    }
}
