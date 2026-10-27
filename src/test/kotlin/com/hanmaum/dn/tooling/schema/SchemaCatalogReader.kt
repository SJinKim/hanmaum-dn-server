package com.hanmaum.dn.tooling.schema

import java.sql.Connection
import java.sql.ResultSet

/**
 * Reads table metadata from the PostgreSQL catalog of a migrated database. Rendering text comes
 * from the catalog functions (`format_type`, `pg_get_constraintdef`, `pg_get_indexdef`,
 * `pg_get_expr`), so the snapshot shows what PostgreSQL actually built rather than a parse of the
 * migration files.
 */
class SchemaCatalogReader(
    private val connection: Connection,
) {
    fun read(): SchemaCatalog {
        // Catalog functions qualify names outside the search path; pin it so the output does not
        // depend on the connecting role's settings.
        connection.createStatement().use { it.execute("SET search_path TO public") }

        val tables = query(TABLES_SQL) { rs -> rs.getLong("oid") to Pair(rs.getString("nspname"), rs.getString("relname")) }.toMap()
        val columns =
            query(COLUMNS_SQL) { rs ->
                rs.getLong("attrelid") to
                    ColumnInfo(
                        name = rs.getString("attname"),
                        type = rs.getString("type"),
                        notNull = rs.getBoolean("attnotnull"),
                        default = rs.getString("default_expr"),
                        identity = rs.getString("identity")?.firstOrNull(),
                        generated = rs.getString("generated_expr"),
                    )
            }.groupBy({ it.first }, { it.second })
        val constraints =
            query(CONSTRAINTS_SQL) { rs ->
                rs.getLong("conrelid") to ConstraintInfo(rs.getString("conname"), rs.getString("definition"))
            }.groupBy({ it.first }, { it.second })
        val indexes =
            query(INDEXES_SQL) { rs ->
                rs.getLong("indrelid") to
                    IndexInfo(
                        name = rs.getString("relname"),
                        definition = rs.getString("definition").let { it.substringAfter(" USING ", it) },
                        unique = rs.getBoolean("indisunique"),
                    )
            }.groupBy({ it.first }, { it.second })

        return SchemaCatalog(
            tables.map { (oid, name) ->
                TableInfo(
                    schema = name.first,
                    name = name.second,
                    columns = columns[oid].orEmpty(),
                    constraints = constraints[oid].orEmpty(),
                    indexes = indexes[oid].orEmpty(),
                )
            },
        )
    }

    private fun <T> query(
        sql: String,
        mapper: (ResultSet) -> T,
    ): List<T> =
        connection.prepareStatement(sql).use { statement ->
            statement.executeQuery().use { rs ->
                buildList { while (rs.next()) add(mapper(rs)) }
            }
        }

    companion object {
        /** Ordinary and partitioned tables outside PostgreSQL's own schemas, excluding extension-owned tables. */
        internal const val APPLICATION_TABLES_SQL = """
            SELECT c.oid, n.nspname, c.relname
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE c.relkind IN ('r', 'p')
              AND n.nspname NOT IN ('pg_catalog', 'information_schema')
              AND n.nspname NOT LIKE 'pg\_toast%'
              AND n.nspname NOT LIKE 'pg\_temp\_%'
              AND NOT EXISTS (
                  SELECT 1 FROM pg_depend d
                  WHERE d.classid = 'pg_class'::regclass AND d.objid = c.oid AND d.deptype = 'e'
              )
        """

        private const val TABLES_SQL = APPLICATION_TABLES_SQL

        private const val COLUMNS_SQL = """
            WITH t AS ($APPLICATION_TABLES_SQL)
            SELECT a.attrelid, a.attname,
                   format_type(a.atttypid, a.atttypmod) AS type,
                   a.attnotnull,
                   CASE WHEN a.attgenerated = '' THEN pg_get_expr(d.adbin, d.adrelid) END AS default_expr,
                   NULLIF(a.attidentity, '') AS identity,
                   CASE WHEN a.attgenerated <> '' THEN pg_get_expr(d.adbin, d.adrelid) END AS generated_expr
            FROM pg_attribute a
            JOIN t ON t.oid = a.attrelid
            LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
            WHERE a.attnum > 0 AND NOT a.attisdropped
            ORDER BY a.attrelid, a.attnum
        """

        private const val CONSTRAINTS_SQL = """
            WITH t AS ($APPLICATION_TABLES_SQL)
            SELECT con.conrelid, con.conname, pg_get_constraintdef(con.oid) AS definition
            FROM pg_constraint con
            JOIN t ON t.oid = con.conrelid
            WHERE con.contype IN ('p', 'u', 'c', 'f', 'x')
        """

        // An index that only backs a primary key, unique or exclusion constraint is already shown
        // with that constraint.
        private const val INDEXES_SQL = """
            WITH t AS ($APPLICATION_TABLES_SQL)
            SELECT i.indrelid, ic.relname, pg_get_indexdef(i.indexrelid) AS definition, i.indisunique
            FROM pg_index i
            JOIN t ON t.oid = i.indrelid
            JOIN pg_class ic ON ic.oid = i.indexrelid
            WHERE NOT EXISTS (
                SELECT 1 FROM pg_constraint con
                WHERE con.conindid = i.indexrelid AND con.conrelid = i.indrelid AND con.contype IN ('p', 'u', 'x')
            )
        """
    }
}
