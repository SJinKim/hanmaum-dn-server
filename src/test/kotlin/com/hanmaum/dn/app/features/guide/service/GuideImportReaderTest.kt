package com.hanmaum.dn.app.features.guide.service

import org.apache.commons.csv.CSVFormat
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuideImportReaderTest {
    @TempDir lateinit var dir: Path
    private val reader = GuideImportReader()
    private val blocks =
        listOf(
            Triple(2021, 4, 5..8),
            Triple(2022, 9, 10..61),
            Triple(2023, 62, 63..114),
            Triple(2024, 116, 117..168),
            Triple(
                2025,
                169,
                170..221,
            ),
            Triple(2026, 222, 223..264),
        )

    private fun fixture(xlsx: Boolean = false): Path {
        val rows = MutableList(280) { MutableList(30) { "ignored data" } }
        blocks.forEach { (year, header, range) ->
            rows[header - 1][1] = if (header == 116) "2023" else year.toString()
            range.forEachIndexed { i, row ->
                rows[row - 1][1] = "1/${i + 1}"
                val d = LocalDate.of(year, 1, 1).plusDays(i.toLong())
                rows[row - 1][1] = "${d.monthValue}/${d.dayOfMonth}"
                for (column in 2..4) rows[row - 1][column] = if (row in 24..27) "0" else "1"
                rows[row - 1][5] = if (row in 24..27) "0" else "3"
                rows[row - 1][6] = "0"
                rows[row - 1][8] = if (row == 223) "메모, note\nsecond line" else ""
            }
        }
        val path = dir.resolve(if (xlsx) "source.xlsx" else "source.csv")
        if (!xlsx) {
            Files.newBufferedWriter(path).use { out ->
                CSVFormat.RFC4180
                    .builder()
                    .setDelimiter(';')
                    .build()
                    .print(out)
                    .use { p -> rows.forEach(p::printRecord) }
            }
        } else {
            XSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("Summary")
                blocks.forEach { (year, header, range) ->
                    sheet.createRow(header - 1).createCell(1).setCellValue(year.toDouble())
                    range.forEachIndexed { i, row ->
                        val r = sheet.createRow(row - 1)
                        val sourceYear = if (row == 170) 2024 else year
                        r.createCell(1).setCellValue(LocalDate.of(sourceYear, 1, 1).plusDays(i.toLong()).atStartOfDay())
                        for (c in 2..6) r.createCell(c).setCellValue(rows[row - 1][c].toDouble())
                        r.createCell(8).setCellValue(rows[row - 1][8])
                        r.createCell(7).setCellValue("must not parse 예약비")
                        if (row == 223) {
                            r.getCell(2).cellFormula = "'missing-sheet'!A1"
                            r.getCell(2).setCellValue(1.0)
                        }
                    }
                }
                Files.newOutputStream(path).use(workbook::write)
            }
        }
        return path
    }

    @Test fun `fixed main blocks ignore excluded columns preserve notes and distinguish missing from real zero`() {
        val source = reader.read(fixture())
        assertEquals(254, source.rows.size)
        assertEquals(250, source.rows.count { it.total != null })
        assertTrue(source.rows.filter { it.rowNumber in 24..27 }.all { it.counts == listOf(null, null, null) && it.total == null })
        assertEquals("메모, note\nsecond line", source.rows.single { it.rowNumber == 223 }.notes)
        assertTrue(source.rows.all { it.source.keys.all { c -> c in setOf("B", "C", "D", "E", "F", "G", "I") } })
        assertFailsWith<IllegalArgumentException> { reader.validateApproved(source) }
    }

    @Test fun `xlsx uses cached summary formulas without reading detail sheets and normalizes approved year`() {
        val source = reader.read(fixture(true))
        assertEquals(LocalDate.of(2025, 1, 1), source.rows.single { it.rowNumber == 170 }.date)
        assertEquals(1, source.rows.single { it.rowNumber == 223 }.counts[0])
        assertEquals("'missing-sheet'!A1", source.rows.single { it.rowNumber == 223 }.source["CFormula"])
        assertTrue("DATE_NORMALIZED:170" in source.warnings)
    }

    @Test fun `changed main total fails rather than silently importing it`() {
        val path = fixture()
        val text = Files.readString(path).replaceFirst(";1;1;1;3;", ";1;1;1;4;")
        Files.writeString(path, text)
        assertFailsWith<IllegalArgumentException> { reader.read(path) }
    }

    @Test fun `csv accounting dash is zero and approved header override preserves 2024`() {
        val path = fixture()
        Files.writeString(path, Files.readString(path).replaceFirst(";1;1;1;3;", ";1;1; - ;2;"))
        val source = reader.read(path)
        assertEquals(0, source.rows.first().counts[2])
        assertEquals(2L, source.rows.first().total)
        assertEquals(
            2024,
            source.rows
                .single { it.rowNumber == 117 }
                .date.year,
        )
        assertTrue("HEADER_YEAR_OVERRIDDEN:116" in source.warnings)
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GUIDE_IMPORT_REAL_SOURCE_DIR", matches = ".+")
    fun `approved private xlsx and csv have identical canonical reports and manifest`() {
        val paths = Files.list(Path.of(System.getenv("GUIDE_IMPORT_REAL_SOURCE_DIR"))).use { it.toList() }

        fun find(name: String) = paths.single { Normalizer.normalize(it.fileName.toString(), Normalizer.Form.NFC) == name }
        val xlsx = reader.read(find("예배 인원 집계 2026.xlsx"))
        val csv = reader.read(find("예배 인원 집계.csv"))
        reader.validateApproved(xlsx)
        reader.validateApproved(csv)
        assertEquals(xlsx.rows.map { it.fingerprint }, csv.rows.map { it.fingerprint })
        assertEquals("9163e7f85c3196bac09c11aac4273f76966665eb8f5378d07cd3edaf90a292e7", xlsx.sha256)
        assertTrue("DELTA_RECOMPUTED:262" in xlsx.warnings)
        assertNull(xlsx.rows.single { it.rowNumber == 24 }.total)
        assertNotNull(xlsx.rows.single { it.rowNumber == 264 }.total)
    }
}
