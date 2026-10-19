package com.hanmaum.dn.app.features.guide.service

import org.apache.commons.csv.CSVFormat
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.xssf.eventusermodel.XSSFReader
import org.apache.poi.xssf.model.SharedStrings
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

const val GUIDE_MAPPING_VERSION = "summary-main-v1"

fun guideSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

fun guideCanonical(
    date: LocalDate,
    counts: List<Int?>,
    notes: String?,
): String =
    guideSha256(
        (listOf(date.toString()) + counts.map { it?.toString() } + notes)
            .joinToString("") {
                it?.let { s -> "${s.length}:$s;" }
                    ?: "null;"
            }.toByteArray(Charsets.UTF_8),
    )

data class GuideImportRow(
    val rowNumber: Int,
    val date: LocalDate,
    val counts: List<Int?>,
    val notes: String?,
    val source: Map<String, String>,
    val sourceDelta: Long?,
) {
    val total: Long? get() = if (counts.any { it == null }) null else counts.sumOf { it!!.toLong() }
    val fingerprint: String get() = guideCanonical(date, counts, notes)
}

data class GuideImportSource(
    val sha256: String,
    val rows: List<GuideImportRow>,
    val warnings: List<String>,
)

@Component
class GuideImportReader {
    /** Reads only B:G/I at the approved fixed rows. Formula caches, never detail-sheet recomputation. */
    fun read(path: Path): GuideImportSource {
        require(Files.isRegularFile(path)) { "Import source must be a regular file." }
        val bytes = Files.readAllBytes(path)
        val raw =
            when (
                path.fileName
                    .toString()
                    .substringAfterLast('.')
                    .lowercase()
            ) {
                "xlsx" -> xlsx(bytes)
                "csv" -> csv(bytes)
                else -> error("Only XLSX or semicolon CSV is supported.")
            }
        val warnings = mutableListOf<String>()
        val rows =
            blocks.flatMap { (year, header, range) ->
                if (header == 116) {
                    if (raw[header]?.get("B")?.trim() != year.toString()) warnings += "HEADER_YEAR_OVERRIDDEN:116"
                } else {
                    require(raw[header]?.get("B")?.trim() == year.toString()) { "Unexpected year header at row $header." }
                }
                range.map { rowNumber ->
                    val cells = requireNotNull(raw[rowNumber]) { "Missing source row $rowNumber." }
                    val rawDate = cells.getValue("B").trim()
                    val monthDay =
                        if (rawDate.contains('/')) {
                            rawDate.split('/').take(2).map(String::toInt)
                        } else {
                            LocalDate.parse(rawDate).let { listOf(it.monthValue, it.dayOfMonth) }
                        }
                    val date = LocalDate.of(year, monthDay[0], monthDay[1])
                    if (!rawDate.contains('/') && LocalDate.parse(rawDate).year != year) warnings += "DATE_NORMALIZED:$rowNumber"
                    val counts =
                        if (rowNumber in 24..27) {
                            listOf(null, null, null)
                        } else {
                            listOf("C", "D", "E").map { column ->
                                number(cells[column].orEmpty(), rowNumber)
                                    ?.also {
                                        require(it in 0..Int.MAX_VALUE.toLong()) { "Invalid count at row $rowNumber." }
                                    }?.toInt()
                            }
                        }
                    val row =
                        GuideImportRow(
                            rowNumber,
                            date,
                            counts,
                            cells["I"]?.takeIf(String::isNotBlank),
                            cells,
                            number(cells["G"].orEmpty(), rowNumber),
                        )
                    if (row.total !=
                        null
                    ) {
                        require(number(cells["F"].orEmpty(), rowNumber) == row.total) { "Main total mismatch at row $rowNumber." }
                    }
                    row
                }
            }
        require(rows.map { it.date }.distinct().size == rows.size) { "Duplicate normalized source dates." }
        val byDate = rows.associateBy { it.date }
        rows.forEach { row ->
            val before = byDate[row.date.minusWeeks(1)]?.total
            val expected = if (before != null && row.total != null) row.total!! - before else null
            if (row.sourceDelta != expected) warnings += "DELTA_RECOMPUTED:${row.rowNumber}"
        }
        return GuideImportSource(guideSha256(bytes), rows, warnings)
    }

    /** Frozen approved inventory; protects a live write from a shifted/changed workbook. */
    fun validateApproved(source: GuideImportSource) {
        require(source.rows.size == 254 && source.rows.count { it.total != null } == 250) { "Unexpected report coverage." }
        require(source.rows.count { !it.notes.isNullOrBlank() } == 116) { "Unexpected notes coverage." }
        val sums = (0..2).map { i -> source.rows.sumOf { it.counts[i]?.toLong() ?: 0L } }
        require(sums == listOf(41317L, 81757L, 36679L)) { "Service control sums differ from approved source." }
        val annual = source.rows.groupBy { it.date.year }.mapValues { (_, rows) -> rows.sumOf { it.total ?: 0L } }
        require(annual == mapOf(2021 to 1657L, 2022 to 22774L, 2023 to 26342L, 2024 to 37205L, 2025 to 40340L, 2026 to 31435L)) {
            "Annual control sums differ from approved source."
        }
    }

    private fun number(
        raw: String,
        row: Int,
    ): Long? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        // Excel's accounting format exports an actual numeric zero as a dash in CSV.
        if (value == "-") return 0L
        require(Regex("-?(?:[0-9]+|[0-9]{1,3}(?:\\.[0-9]{3})+)").matches(value)) { "Invalid integer at row $row." }
        return value.replace(".", "").toLong()
    }

    private fun csv(bytes: ByteArray): Map<Int, Map<String, String>> =
        bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").reader().use { reader ->
            CSVFormat.RFC4180.builder().setDelimiter(';').build().parse(reader).use { parser ->
                parser
                    .mapIndexedNotNull { index, record ->
                        val row = index + 1
                        if (row !in
                            allowedRows
                        ) {
                            null
                        } else {
                            row to
                                columns.mapValues { (_, index) -> if (record.size() > index) record.get(index) else "" }
                        }
                    }.toMap()
            }
        }

    private fun xlsx(bytes: ByteArray): Map<Int, Map<String, String>> {
        OPCPackage.open(ByteArrayInputStream(bytes)).use { pkg ->
            val workbook = XSSFReader(pkg)
            workbook.setUseReadOnlySharedStringsTable(true)
            val sheets = workbook.sheetsData as XSSFReader.SheetIterator
            while (sheets.hasNext()) {
                sheets.next().use { input ->
                    if (sheets.sheetName == "Summary") return summaryXml(input, workbook.sharedStringsTable)
                }
            }
        }
        error("Missing Summary sheet.")
    }

    private fun summaryXml(
        input: InputStream,
        strings: SharedStrings?,
    ): Map<Int, Map<String, String>> {
        val factory = XMLInputFactory.newFactory()
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        val xml = factory.createXMLStreamReader(input)
        val rows = allowedRows.associateWith { columns.mapValues { "" }.toMutableMap() }
        var row = 0
        var column: String? = null
        var type: String? = null
        try {
            while (xml.hasNext()) {
                if (xml.next() != XMLStreamConstants.START_ELEMENT) continue
                when (xml.localName) {
                    "row" -> {
                        row = xml.getAttributeValue(null, "r").toInt()
                        if (row > 264) break
                    }
                    "c" -> {
                        val address = xml.getAttributeValue(null, "r")
                        column = address.takeWhile(Char::isLetter).takeIf { row in allowedRows && it in columns }
                        type = xml.getAttributeValue(null, "t")
                    }
                    "f" -> if (column != null) rows.getValue(row)[column + "Formula"] = xml.elementText
                    "v", "t" ->
                        if (column != null) {
                            val raw = xml.elementText
                            val value =
                                when (type) {
                                    "s" -> requireNotNull(strings).getItemAt(raw.toInt()).string
                                    "str", "inlineStr" -> raw
                                    null, "n" ->
                                        if (column == "B" &&
                                            row !in headers
                                        ) {
                                            DateUtil.getLocalDateTime(raw.toDouble()).toLocalDate().toString()
                                        } else {
                                            raw.toBigDecimal().stripTrailingZeros().toPlainString()
                                        }
                                    else -> error("Unsupported value in allowed source cell $column$row.")
                                }
                            val cells = rows.getValue(row)
                            cells[column!!] = if (type == "inlineStr") cells.getValue(column!!) + value else value
                        }
                }
            }
        } finally {
            xml.close()
        }
        return rows
    }

    private companion object {
        val blocks =
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
        val columns = linkedMapOf("B" to 1, "C" to 2, "D" to 3, "E" to 4, "F" to 5, "G" to 6, "I" to 8)
        val headers = blocks.map { it.second }.toSet()
        val allowedRows = headers + blocks.flatMap { it.third }
    }
}
