package com.hanmaum.dn.tooling.schema

import com.hanmaum.dn.app.common.pii.PiiCryptoContext
import com.hanmaum.dn.app.common.pii.PiiCryptoService
import com.hanmaum.dn.app.common.pii.PiiKeyring
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.CoreMigrationType
import org.flywaydb.core.api.MigrationState
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.sql.DriverManager
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess

/**
 * Migrates an empty, dedicated PostgreSQL database with the production Flyway migrations and
 * renders its schema as `docs/db/schema.md`. Run by `./gradlew schemaSnapshot` in CI; it never
 * starts the application and never touches a shared database.
 */
class SchemaSnapshotGenerator(
    private val database: Database,
    private val projectDir: Path,
    private val locations: List<String> = listOf(MIGRATION_LOCATION),
) {
    data class Database(
        val url: String,
        val user: String,
        val password: String,
    ) {
        override fun toString() = "Database(url=$url)"
    }

    private val log = LoggerFactory.getLogger(SchemaSnapshotGenerator::class.java)

    fun generate(): String {
        val sqlDir = projectDir.resolve("src/main/resources/db/migration")
        val kotlinDir = projectDir.resolve("src/main/kotlin/db/migration")
        val migrationHash = MigrationHash.of(projectDir, listOf(sqlDir, kotlinDir))
        val notesFile = projectDir.resolve(NOTES_PATH)
        val notes = if (notesFile.exists()) SchemaNotes.parse(notesFile.readText()) else SchemaNotes.EMPTY

        requireEmptyDatabase()
        migrate(expectedKotlinMigrations = kotlinDir.listDirectoryEntries("*.kt").size)

        val catalog = connect().use { SchemaCatalogReader(it).read() }
        connect().use { CatalogCompleteness.verify(it, catalog) }
        log.info(
            "Schema snapshot read tables={} columns={}",
            catalog.tables.size,
            catalog.tables.sumOf { it.columns.size },
        )
        return SchemaMarkdownRenderer.render(catalog, notes, migrationHash)
    }

    private fun requireEmptyDatabase() {
        val existing =
            connect().use { connection ->
                connection.prepareStatement("SELECT count(*) FROM (${SchemaCatalogReader.APPLICATION_TABLES_SQL}) t").use { st ->
                    st.executeQuery().use { rs ->
                        rs.next()
                        rs.getInt(1)
                    }
                }
            }
        check(existing == 0) {
            "Schema snapshot needs an empty, dedicated database but ${database.url} already has $existing tables."
        }
    }

    private fun migrate(expectedKotlinMigrations: Int) {
        // The Kotlin migrations encrypt PII as soon as matching rows exist. A throwaway keyring
        // keeps them runnable without any real key; the database is discarded afterwards.
        PiiCryptoContext.initialize(PiiCryptoService(throwawayKeyring(), legacyPlaintextReadEnabled = false))

        val flyway =
            Flyway
                .configure()
                .dataSource(database.url, database.user, database.password)
                .locations(*locations.toTypedArray())
                .failOnMissingLocations(true)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load()
        flyway.migrate()

        val migrations = flyway.info().all().filterNot { it.type.isSynthetic }
        val unsuccessful = migrations.filter { it.state != MigrationState.SUCCESS }
        check(unsuccessful.isEmpty()) {
            "Migrations not applied successfully: " + unsuccessful.joinToString { "${it.version ?: it.description} (${it.state})" }
        }
        // A Kotlin migration missing from the classpath would be skipped silently, not fail.
        val kotlinMigrations = migrations.filter { it.type == CoreMigrationType.JDBC }
        check(kotlinMigrations.size == expectedKotlinMigrations) {
            "Expected $expectedKotlinMigrations Kotlin migrations on the classpath, Flyway applied ${kotlinMigrations.size}."
        }
        log.info(
            "Schema snapshot migrated sql={} kotlin={} kotlinVersions={}",
            migrations.count { it.type == CoreMigrationType.SQL },
            kotlinMigrations.size,
            kotlinMigrations.map { it.version.toString() },
        )
    }

    private fun connect() = DriverManager.getConnection(database.url, database.user, database.password)

    private fun throwawayKeyring(): PiiKeyring {
        val random = SecureRandom()

        fun key() = ByteArray(32).also(random::nextBytes)
        return PiiKeyring(
            activeKeyId = "schema-snapshot",
            encryptionKeys = mapOf("schema-snapshot" to SecretKeySpec(key(), "AES")),
            indexKey = SecretKeySpec(key(), "HmacSHA256"),
        )
    }

    companion object {
        const val MIGRATION_LOCATION = "classpath:db/migration"
        const val NOTES_PATH = "docs/db/schema-notes.md"
    }
}

/** Writes the snapshot only after a complete, successful generation, so no partial candidate is left behind. */
fun writeSnapshot(
    output: Path,
    generate: () -> String,
) {
    Files.deleteIfExists(output)
    val content = generate()
    Files.createDirectories(output.toAbsolutePath().parent)
    val temp = Files.createTempFile(output.toAbsolutePath().parent, "schema", ".md")
    temp.writeText(content, Charsets.UTF_8)
    Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}

/**
 * `generate <output>`: needs SCHEMA_SNAPSHOT_DB_URL, SCHEMA_SNAPSHOT_DB_USER and
 * SCHEMA_SNAPSHOT_DB_PASSWORD for an empty database; runs in the project directory.
 * `check <checked-in> <candidate>`: exits non-zero when the checked-in snapshot is stale.
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "generate" -> {
            require(args.size == 2) { "Usage: generate <output>" }
            val database =
                SchemaSnapshotGenerator.Database(
                    url = requiredEnv("SCHEMA_SNAPSHOT_DB_URL"),
                    user = requiredEnv("SCHEMA_SNAPSHOT_DB_USER"),
                    password = requiredEnv("SCHEMA_SNAPSHOT_DB_PASSWORD"),
                )
            val output = Path(args[1])
            writeSnapshot(output) { SchemaSnapshotGenerator(database, Path("").toAbsolutePath()).generate() }
            println("Schema snapshot written to $output (${Files.size(output)} bytes)")
        }
        "check" -> {
            require(args.size == 3) { "Usage: check <checked-in> <candidate>" }
            val checkedIn = Path(args[1]).takeIf { it.exists() }?.readText()
            val problem = SnapshotComparison.problem(checkedIn, Path(args[2]).readText())
            if (problem != null) {
                System.err.println(problem)
                System.err.println()
                System.err.println(updateInstructions())
                exitProcess(1)
            }
            println("docs/db/schema.md is up to date.")
        }
        else -> throw IllegalArgumentException("Usage: generate <output> | check <checked-in> <candidate>")
    }
}

private fun requiredEnv(name: String): String =
    System.getenv(name)?.takeIf(String::isNotBlank) ?: error("Environment variable $name is not set.")

private fun updateInstructions(): String {
    val runId = System.getenv("GITHUB_RUN_ID")
    val repo = System.getenv("GITHUB_REPOSITORY")
    val download =
        if (runId != null && repo != null) {
            "gh run download $runId -R $repo -n schema-snapshot -D /tmp/schema-snapshot"
        } else {
            "gh run download <run-id> -n schema-snapshot -D /tmp/schema-snapshot"
        }
    return """
        To update the snapshot (no local Docker needed):
          1. Open this workflow run, job "Schema snapshot", and download the artifact "schema-snapshot", or run:
             $download
          2. Copy schema.md to docs/db/schema.md, review the diff, commit and push.
          The next CI run verifies the file. Always take the artifact from the run of your latest commit.
        Details: docs/db/README.md
        """.trimIndent()
}
