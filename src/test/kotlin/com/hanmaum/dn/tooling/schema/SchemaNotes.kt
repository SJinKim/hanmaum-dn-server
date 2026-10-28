package com.hanmaum.dn.tooling.schema

/**
 * Hand-written notes per table, kept in `docs/db/schema-notes.md` so regenerating the snapshot
 * never loses them. Each `## schema.table` heading starts a table's note; everything up to the next
 * such heading belongs to it verbatim. Text before the first heading is the file's own intro.
 */
class SchemaNotes private constructor(
    val byTable: Map<String, String>,
) {
    /** Fails on notes for tables that no longer exist, so a rename or drop does not lose them silently. */
    fun requireKnownTables(qualifiedTableNames: Set<String>) {
        val orphaned = byTable.keys - qualifiedTableNames
        require(orphaned.isEmpty()) {
            "Schema notes reference unknown tables: ${orphaned.sorted().joinToString()}. " +
                "Rename or remove their sections in docs/db/schema-notes.md."
        }
    }

    companion object {
        val EMPTY = SchemaNotes(emptyMap())

        private val TABLE_HEADING = Regex("""^## +(\S+)\s*$""")
        private val QUALIFIED_NAME = Regex("""^[^.\s]+\.[^.\s]+$""")

        fun parse(text: String): SchemaNotes {
            val notes = linkedMapOf<String, MutableList<String>>()
            var current: MutableList<String>? = null
            text.replace("\r\n", "\n").split('\n').forEachIndexed { index, line ->
                if (line.startsWith("## ")) {
                    val table =
                        TABLE_HEADING
                            .find(line)
                            ?.groupValues
                            ?.get(1)
                            ?.takeIf(QUALIFIED_NAME::matches)
                            ?: throw IllegalArgumentException(
                                "Line ${index + 1}: note headings must be '## <schema>.<table>', got '$line'.",
                            )
                    require(table !in notes) { "Line ${index + 1}: duplicate notes for $table." }
                    current = mutableListOf<String>().also { notes[table] = it }
                } else {
                    current?.add(line)
                }
            }
            return SchemaNotes(
                notes.mapValues { (_, lines) -> lines.dropWhile(String::isBlank).dropLastWhile(String::isBlank).joinToString("\n") },
            )
        }
    }
}
