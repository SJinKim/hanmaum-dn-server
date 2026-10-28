package com.hanmaum.dn.tooling.schema

/** Catalog metadata of the migrated database, as read by [SchemaCatalogReader]. */
data class SchemaCatalog(
    val tables: List<TableInfo>,
)

data class TableInfo(
    val schema: String,
    val name: String,
    /** In attribute order (`pg_attribute.attnum`). */
    val columns: List<ColumnInfo>,
    /** Primary key, unique, check, foreign key and exclusion constraints. */
    val constraints: List<ConstraintInfo>,
    /** Indexes that do not merely back a primary key, unique or exclusion constraint. */
    val indexes: List<IndexInfo>,
) {
    val qualifiedName: String get() = "$schema.$name"
}

data class ColumnInfo(
    val name: String,
    /** `format_type`, including length and precision. */
    val type: String,
    val notNull: Boolean,
    val default: String?,
    /** `a` = always, `d` = by default, `null` = no identity. */
    val identity: Char?,
    /** Expression of a stored generated column. */
    val generated: String?,
)

data class ConstraintInfo(
    val name: String,
    /** `pg_get_constraintdef`, e.g. `FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE`. */
    val definition: String,
)

data class IndexInfo(
    val name: String,
    /** `pg_get_indexdef` from the access method on, e.g. `btree (a, b DESC) WHERE (deleted_at IS NULL)`. */
    val definition: String,
    val unique: Boolean,
)
