package com.hanmaum.dn.app.features.newcomers.service

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NewcomerImportReaderTest {
    private val reader = NewcomerImportReader()

    @Test
    fun `maps korean spreadsheet headers from a local csv`() {
        val file = Files.createTempFile("newcomer-import-", ".csv")
        file.writeText(
            """
            성,이름,성별,세례,신분,출석현황,순
            가,나,남,세례,직장인,정기출석,4
            """.trimIndent(),
        )

        val row = reader.read(file, NewcomerImportLayout.SPREADSHEET_A).single()

        assertEquals(2, row.rowNumber)
        assertEquals("가", row.fields["lastName"])
        assertEquals("나", row.fields["firstName"])
        assertEquals("4", row.fields["intakeRound"])
    }

    @Test
    fun `reads quoted multiline csv fields without shifting following columns`() {
        val file = Files.createTempFile("newcomer-import-", ".csv")
        file.writeText(
            "성,이름,담당자\n가,나,\"인도자,\n팀 A\"",
        )

        val row = reader.read(file, NewcomerImportLayout.SPREADSHEET_A).single()

        assertEquals("가", row.fields["lastName"])
        assertEquals("나", row.fields["firstName"])
        assertEquals("인도자,\n팀 A", row.fields["caregiver"])
    }

    @Test
    fun `reads an xlsx export without keeping it in the repository`() {
        val file = Files.createTempFile("newcomer-import-", ".xlsx")
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet()
            sheet.createRow(0).createCell(0).setCellValue("last name")
            sheet.getRow(0).createCell(1).setCellValue("first name")
            sheet.createRow(1).createCell(0).setCellValue("A")
            sheet.getRow(1).createCell(1).setCellValue("B")
            Files.newOutputStream(file).use(workbook::write)
        }

        val row = reader.read(file, NewcomerImportLayout.SPREADSHEET_B).single()

        assertEquals(mapOf("lastName" to "A", "firstName" to "B"), row.fields.filterKeys { it in setOf("lastName", "firstName") })
    }

    @Test
    fun `keeps sparse xlsx cells aligned with their headers`() {
        val file = Files.createTempFile("newcomer-import-", ".xlsx")
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet()
            sheet.createRow(0).createCell(0).setCellValue("last name")
            sheet.getRow(0).createCell(1).setCellValue("phone")
            sheet.getRow(0).createCell(2).setCellValue("first name")
            sheet.createRow(1).createCell(0).setCellValue("A")
            sheet.getRow(1).createCell(2).setCellValue("B")
            Files.newOutputStream(file).use(workbook::write)
        }

        val row = reader.read(file, NewcomerImportLayout.SPREADSHEET_B).single()

        assertEquals("A", row.fields["lastName"])
        assertEquals("", row.fields["phone"])
        assertEquals("B", row.fields["firstName"])
    }

    @Test
    fun `maps a google forms timestamp to registration date without replacing first visit date`() {
        val file = Files.createTempFile("newcomer-import-", ".csv")
        file.writeText("\uFEFFTimestamp,성,이름,첫방문일\n9/21/2026 6:30:00 PM,가,나,2026-09-20")

        val row = reader.read(file, NewcomerImportLayout.GOOGLE_FORMS).single()

        assertEquals("9/21/2026 6:30:00 PM", row.fields["registrationDate"])
        assertEquals("2026-09-20", row.fields["firstVisitDate"])
    }

    @Test
    fun `rejects unmapped source headers to prevent silent data loss`() {
        val file = Files.createTempFile("newcomer-import-", ".csv")
        file.writeText("성,이름,unknown source field\n가,나,value")

        assertFailsWith<IllegalArgumentException> {
            reader.read(file, NewcomerImportLayout.SPREADSHEET_A)
        }
    }
}
