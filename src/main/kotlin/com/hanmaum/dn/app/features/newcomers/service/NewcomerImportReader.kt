package com.hanmaum.dn.app.features.newcomers.service

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

data class NewcomerImportRow(
    val rowNumber: Int,
    val fields: Map<String, String>,
)

@Component
class NewcomerImportReader {
    fun read(
        path: Path,
        layout: NewcomerImportLayout,
    ): List<NewcomerImportRow> {
        require(Files.isRegularFile(path)) { "Import file does not exist." }
        val source =
            if (path.fileName
                    .toString()
                    .lowercase()
                    .endsWith(".xlsx")
            ) {
                readXlsx(path)
            } else {
                readCsv(path)
            }
        val headers = source.headers.map(::normalizeHeader)
        val mapping = headerMapping(layout)
        val supportedHeaders = mapping.values.flatten().toSet()
        val unmappedHeaders = headers.filter(String::isNotBlank).filterNot { it in supportedHeaders }.distinct()
        require(unmappedHeaders.isEmpty()) { "Import file contains unmapped headers: ${unmappedHeaders.joinToString(", ")}." }
        return source.rows.map { sourceRow ->
            NewcomerImportRow(
                sourceRow.rowNumber,
                mapping
                    .mapNotNull { (field, aliases) ->
                        headers.indexOfFirst { it in aliases }.takeIf { it >= 0 }?.let { column ->
                            field to
                                sourceRow.values.getOrElse(column) { "" }.trim()
                        }
                    }.toMap(),
            )
        }
    }

    private fun readXlsx(path: Path): SourceRows =
        Files.newInputStream(path).use { input ->
            WorkbookFactory.create(input).use { workbook ->
                val formatter = DataFormatter()
                val sheet = workbook.getSheetAt(0)
                val header = requireNotNull(sheet.getRow(0)) { "Import file has no header row." }
                val columnCount = header.lastCellNum.toInt()
                require(columnCount > 0) { "Import file has no header row." }
                SourceRows(
                    headers = cells(header, columnCount, formatter),
                    rows =
                        (1..sheet.lastRowNum).mapNotNull { index ->
                            sheet.getRow(index)?.let { row ->
                                SourceRow(index + 1, cells(row, columnCount, formatter))
                            }
                        },
                )
            }
        }

    private fun readCsv(path: Path): SourceRows =
        Files.newBufferedReader(path).use { reader ->
            CSVParser(
                reader,
                CSVFormat.RFC4180
                    .builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .build(),
            ).use { parser ->
                val headers = parser.headerNames
                require(headers.isNotEmpty()) { "Import file has no header row." }
                SourceRows(
                    headers = headers,
                    rows = parser.map { record -> SourceRow(record.recordNumber.toInt() + 1, headers.map(record::get)) },
                )
            }
        }

    private fun cells(
        row: Row,
        columnCount: Int,
        formatter: DataFormatter,
    ): List<String> =
        (0 until columnCount).map { column ->
            row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)?.let(formatter::formatCellValue).orEmpty()
        }

    private fun headerMapping(layout: NewcomerImportLayout): Map<String, Set<String>> =
        commonHeaders +
            when (layout) {
                NewcomerImportLayout.SPREADSHEET_A -> emptyMap()
                NewcomerImportLayout.SPREADSHEET_B -> mapOf("firstVisitDate" to setOf("firstvisit", "firstvisitdate", "첫방문일"))
                NewcomerImportLayout.GOOGLE_FORMS -> mapOf("registrationDate" to setOf("timestamp", "제출시간"))
            }

    private fun normalizeHeader(value: String): String =
        value
            .removePrefix("\uFEFF")
            .lowercase()
            .replace(Regex("[\\s_()/-]"), "")

    private companion object {
        val commonHeaders =
            mapOf(
                "lastName" to setOf("lastname", "surname", "성", "성명성"),
                "firstName" to setOf("firstname", "givenname", "이름", "성명이름"),
                "email" to setOf("email", "이메일", "emailaddress"),
                "phone" to setOf("phone", "phonenumber", "전화번호", "연락처"),
                "birthDate" to setOf("birthdate", "birthday", "생년월일"),
                "registrationDate" to setOf("registrationdate", "등록일"),
                "gender" to setOf("gender", "성별"),
                "baptism" to setOf("baptism", "세례", "세례여부"),
                "identityStatus" to setOf("identitystatus", "신분"),
                "attendance" to setOf("attendance", "출석현황"),
                "caregiver" to setOf("caregiver", "담당자"),
                "intakeRound" to setOf("intakeround", "순", "기수"),
                "firstVisitDate" to setOf("firstvisitdate", "첫방문일"),
            )
    }

    private data class SourceRows(
        val headers: List<String>,
        val rows: List<SourceRow>,
    )

    private data class SourceRow(
        val rowNumber: Int,
        val values: List<String>,
    )
}
