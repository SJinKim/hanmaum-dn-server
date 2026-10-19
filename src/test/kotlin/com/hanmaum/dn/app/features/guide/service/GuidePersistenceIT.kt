package com.hanmaum.dn.app.features.guide.service

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.guide.api.v1.dto.WorshipCountRequest
import com.hanmaum.dn.app.features.guide.domain.WorshipCountReport
import com.hanmaum.dn.app.features.guide.repository.GuideImportBatchRepository
import com.hanmaum.dn.app.features.guide.repository.GuideImportRecordRepository
import com.hanmaum.dn.app.features.guide.repository.WorshipCountRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.kotlin.any
import org.mockito.kotlin.doCallRealMethod
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DataJpaTest(properties = ["spring.jpa.show-sql=false"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
@Import(PiiCryptoConfiguration::class, WorshipCountService::class, GuideImportService::class, GuidePersistenceIT.MapperConfig::class)
class GuidePersistenceIT {
    @Autowired private lateinit var reports: WorshipCountRepository

    @Autowired private lateinit var batches: GuideImportBatchRepository

    @Autowired private lateinit var records: GuideImportRecordRepository

    @Autowired private lateinit var service: WorshipCountService

    @Autowired private lateinit var importer: GuideImportService

    @Autowired private lateinit var em: EntityManager

    @MockitoBean private lateinit var reader: GuideImportReader
    private val date = LocalDate.of(2026, 10, 18)
    private val path = Path.of("synthetic.csv")

    @TestConfiguration
    class MapperConfig {
        @Bean fun mapper(): ObjectMapper = JsonMapper.builder().build()
    }

    private fun source(): GuideImportSource {
        val rows =
            listOf(
                GuideImportRow(5, date.minusWeeks(1), listOf(171, 338, 144), "synthetic note\nline", mapOf("B" to "10/11"), null),
                GuideImportRow(6, date, listOf(186, 384, 146), null, mapOf("B" to "10/18"), 63),
            )
        return GuideImportSource("synthetic-source-hash", rows, emptyList()).also { whenever(reader.read(path)).thenReturn(it) }
    }

    @Test fun `actual persistence update version pagination soft delete and exact previous week`() {
        val previous = service.create(WorshipCountRequest(date.minusWeeks(1), 171, 338, 144), "test")
        val current = service.create(WorshipCountRequest(date, 186, 384, 146, "note\nline"), "test")
        em.clear()
        assertEquals(63L, service.get(current.publicId).previousWeekDelta)
        val changed = service.update(current.publicId, WorshipCountRequest(date, 0, null, 146, "new", current.version), "editor")
        assertEquals(current.version + 1, changed.version)
        assertNull(changed.totalCount)
        assertNull(changed.previousWeekDelta)
        assertFailsWith<GuideException> {
            service.update(current.publicId, WorshipCountRequest(date, 1, 2, 3, version = current.version), "editor")
        }
        assertEquals(1, service.list(null, null, 0, 1, "serviceDate,desc").content.size)
        assertEquals(2L, service.list(null, null, 0, 1, "serviceDate,desc").totalElements)
        service.delete(previous.publicId, previous.version, "editor")
        assertNull(reports.findByPublicIdAndDeletedAtIsNull(previous.publicId))
        assertEquals(1L, service.list(null, null, 0, 20, "serviceDate,desc").totalElements)
    }

    @Test fun `database independently rejects duplicate active dates`() {
        reports.saveAndFlush(WorshipCountReport(date, 1, 2, 3, null, "test"))
        assertFailsWith<DataIntegrityViolationException> { reports.saveAndFlush(WorshipCountReport(date, 4, 5, 6, null, "test")) }
    }

    @Test fun `dry run is write free commit retry and guarded batch rollback are idempotent`() {
        val source = source()
        val dry = importer.import(path)
        assertEquals(2, dry.newReports)
        assertEquals(0L, reports.count())
        assertEquals(0L, batches.count())
        val committed = importer.import(path, false, source.sha256)
        assertNotNull(committed.batchPublicId)
        assertEquals(2L, reports.count())
        assertEquals(2, importer.import(path, false, source.sha256).skipped)
        assertEquals(1L, batches.count())
        assertEquals(2L, records.count())
        assertEquals(2, importer.rollback(committed.batchPublicId, true))
        assertEquals(2L, service.list(null, null, 0, 20, "serviceDate,desc").totalElements)
        assertEquals(2, importer.rollback(committed.batchPublicId, false))
        assertEquals(0L, service.list(null, null, 0, 20, "serviceDate,desc").totalElements)
        assertEquals(0, importer.rollback(committed.batchPublicId, false))
    }

    @Test fun `edits block retry and rollback without overwriting user data`() {
        val source = source()
        val batch = importer.import(path, false, source.sha256).batchPublicId!!
        val report = reports.findByServiceDateAndDeletedAtIsNull(date)!!
        service.update(report.publicId, WorshipCountRequest(date, 200, 400, 150, version = report.version), "editor")
        assertEquals(listOf(6), importer.import(path).conflictRows)
        assertFailsWith<IllegalArgumentException> { importer.import(path, false, source.sha256) }
        assertFailsWith<IllegalArgumentException> { importer.rollback(batch, false) }
        assertEquals(750L, service.get(report.publicId).totalCount)
    }

    @Test fun `manual report collision aborts whole import and write requires reviewed hash`() {
        val source = source()
        service.create(WorshipCountRequest(date, 9, 9, 9), "editor")
        assertEquals(listOf(6), importer.import(path).conflictRows)
        assertFailsWith<IllegalArgumentException> { importer.import(path, false, "wrong-hash") }
        assertFailsWith<IllegalArgumentException> { importer.import(path, false, source.sha256) }
        assertEquals(1L, reports.count())
        assertEquals(0L, batches.count())
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GUIDE_IMPORT_REAL_SOURCE_DIR", matches = ".+")
    fun `private source import matches full approved manifest csv retry and rollback in disposable db`() {
        doCallRealMethod().whenever(reader).read(any())
        doCallRealMethod().whenever(reader).validateApproved(any())
        val paths = Files.list(Path.of(System.getenv("GUIDE_IMPORT_REAL_SOURCE_DIR"))).use { it.toList() }

        fun find(name: String) = paths.single { Normalizer.normalize(it.fileName.toString(), Normalizer.Form.NFC) == name }
        val xlsx = find("예배 인원 집계 2026.xlsx")
        val csv = find("예배 인원 집계.csv")
        val dry = importer.import(xlsx)
        assertEquals(254, dry.read)
        assertEquals(250, dry.recorded)
        assertEquals(116, dry.notes)
        assertEquals(listOf(41317L, 81757L, 36679L), dry.serviceSums)
        assertEquals(0L, reports.count())
        val batch = importer.import(xlsx, false, dry.sourceSha256).batchPublicId!!
        assertEquals(254L, reports.count())
        assertEquals(254, importer.import(csv, false, reader.read(csv).sha256).skipped)
        val latest = reports.findByServiceDateAndDeletedAtIsNull(date)!!
        assertEquals(716L, service.get(latest.publicId).totalCount)
        val wrongDelta = reports.findByServiceDateAndDeletedAtIsNull(LocalDate.of(2026, 10, 4))!!
        assertEquals(-93L, service.get(wrongDelta.publicId).previousWeekDelta)
        assertEquals(4, reports.findAll().count { it.service1Count == null && it.service2Count == null && it.service3Count == null })
        assertEquals(116, reports.findAll().count { !it.notes.isNullOrBlank() })
        assertTrue(records.findAll().all { !it.sourceData.contains("예약비") })
        assertEquals(254, importer.rollback(batch, false))
        assertEquals(0L, service.list(null, null, 0, 20, "serviceDate,desc").totalElements)
    }
}
