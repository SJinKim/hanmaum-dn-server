package com.hanmaum.dn.tooling.schema

import java.sql.Connection

/**
 * Cross-checks the catalog read by [SchemaCatalogReader] against independent queries on
 * `information_schema` and `pg_indexes`, so a filter mistake in the reader cannot silently drop
 * tables, columns, keys or indexes from the snapshot.
 */
object CatalogCompleteness {
    private val IMPLICIT_NOT_NULL = Regex("""^\d+_\d+_\d+_not_null$""")
    private val CONSTRAINT_INDEX = Regex("""^(PRIMARY KEY|UNIQUE|EXCLUDE)\b""")

    fun verify(
        connection: Connection,
        catalog: SchemaCatalog,
    ) {
        val tables = catalog.tables
        val mismatches =
            listOfNotNull(
                mismatch(
                    "tables",
                    tables.map { it.qualifiedName }.toSet(),
                    names(
                        connection,
                        """
                        SELECT table_schema || '.' || table_name FROM information_schema.tables
                        WHERE table_type = 'BASE TABLE' AND table_schema NOT IN ('pg_catalog', 'information_schema')
                        """,
                    ),
                ),
                mismatch(
                    "columns",
                    tables.flatMap { t -> t.columns.map { "${t.qualifiedName}.${it.name}" } }.toSet(),
                    names(
                        connection,
                        """
                        SELECT table_schema || '.' || table_name || '.' || column_name FROM information_schema.columns
                        WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
                          AND (table_schema, table_name) IN (
                              SELECT table_schema, table_name FROM information_schema.tables WHERE table_type = 'BASE TABLE'
                          )
                        """,
                    ),
                ),
                mismatch(
                    "constraints",
                    tables.flatMap { t -> t.constraints.map { "${t.qualifiedName}.${it.name}" } }.toSet(),
                    names(
                        connection,
                        """
                        SELECT table_schema || '.' || table_name || '.' || constraint_name FROM information_schema.table_constraints
                        WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
                          AND constraint_type IN ('PRIMARY KEY', 'UNIQUE', 'FOREIGN KEY', 'CHECK')
                        """,
                    ).filterNot { IMPLICIT_NOT_NULL.matches(it.substringAfterLast('.')) }.toSet(),
                    // information_schema has no exclusion constraints.
                    ignoreInCatalog =
                        tables
                            .flatMap { t ->
                                t.constraints.filter { it.definition.startsWith("EXCLUDE") }.map { "${t.qualifiedName}.${it.name}" }
                            }.toSet(),
                ),
                mismatch(
                    "indexes",
                    tables
                        .flatMap { t ->
                            t.indexes.map { "${t.qualifiedName}.${it.name}" } +
                                t.constraints
                                    .filter {
                                        CONSTRAINT_INDEX.containsMatchIn(
                                            it.definition,
                                        )
                                    }.map { "${t.qualifiedName}.${it.name}" }
                        }.toSet(),
                    names(
                        connection,
                        """
                        SELECT schemaname || '.' || tablename || '.' || indexname FROM pg_indexes
                        WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
                        """,
                    ),
                ),
            )
        check(mismatches.isEmpty()) { "Schema snapshot is incomplete:\n" + mismatches.joinToString("\n") }
    }

    private fun mismatch(
        kind: String,
        inCatalog: Set<String>,
        expected: Set<String>,
        ignoreInCatalog: Set<String> = emptySet(),
    ): String? {
        val missing = expected - inCatalog
        val unexpected = inCatalog - expected - ignoreInCatalog
        if (missing.isEmpty() && unexpected.isEmpty()) return null
        return "$kind missing=${missing.sorted()} unexpected=${unexpected.sorted()}"
    }

    private fun names(
        connection: Connection,
        sql: String,
    ): Set<String> =
        connection.prepareStatement(sql.trimIndent()).use { statement ->
            statement.executeQuery().use { rs -> buildSet { while (rs.next()) add(rs.getString(1)) } }
        }
}
