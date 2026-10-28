package com.hanmaum.dn.tooling.schema

import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Runs the real migration chain against fresh PostgreSQL databases. Needs the CI service from
 * SCHEMA_SNAPSHOT_DB_URL/_USER/_PASSWORD with a role that may create databases; every test works in
 * its own scratch database, so the database the snapshot job uses afterwards stays empty.
 */
@Tag("integration")
class SchemaSnapshotGeneratorIT {
    private val admin =
        SchemaSnapshotGenerator.Database(
            url = requiredEnv("SCHEMA_SNAPSHOT_DB_URL"),
            user = requiredEnv("SCHEMA_SNAPSHOT_DB_USER"),
            password = requiredEnv("SCHEMA_SNAPSHOT_DB_PASSWORD"),
        )
    private val projectDir = Path("").toAbsolutePath()
    private val scratchDatabases = mutableListOf<String>()

    @TempDir lateinit var tempDir: Path

    @AfterEach
    fun dropScratchDatabases() {
        DriverManager.getConnection(admin.url, admin.user, admin.password).use { connection ->
            scratchDatabases.forEach { name ->
                connection.createStatement().use { it.execute("DROP DATABASE IF EXISTS \"$name\" WITH (FORCE)") }
            }
        }
    }

    @Test
    fun `migrates the full SQL and Kotlin chain and renders every application table`() {
        val database = scratchDatabase()

        val markdown = SchemaSnapshotGenerator(database, projectDir).generate()

        DriverManager.getConnection(database.url, database.user, database.password).use { connection ->
            val kotlinMigration =
                connection
                    .prepareStatement(
                        "SELECT success FROM flyway_schema_history WHERE type = 'JDBC' AND version = '20261021100000'",
                    ).use { st -> st.executeQuery().use { rs -> if (rs.next()) rs.getBoolean(1) else null } }
            assertEquals(true, kotlinMigration, "V20261021100000 (Kotlin) must be in the applied history")

            val tables =
                connection
                    .prepareStatement(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                    ).use { st -> st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } } }
            assertTrue(tables.size > 10, "expected the application tables, got $tables")
            tables.forEach { assertContains(markdown, "\n## public.$it") }
        }
        assertContains(markdown, "## public.flyway_schema_history (technisch: Flyway-Verlauf)")
        assertContains(markdown, "\n- `keycloak_id` ")
        assertFalse(markdown.contains("nextval('public."), "defaults must not depend on the search path")
    }

    @Test
    fun `generates byte-identical snapshots from two fresh databases`() {
        val first = SchemaSnapshotGenerator(scratchDatabase(), projectDir).generate()
        val second = SchemaSnapshotGenerator(scratchDatabase(), projectDir).generate()

        assertEquals(first, second)
    }

    @Test
    fun `refuses a database that already has tables`() {
        val database = scratchDatabase()
        DriverManager.getConnection(database.url, database.user, database.password).use { connection ->
            connection.createStatement().use { it.execute("CREATE TABLE leftover (id bigint)") }
        }

        val error = assertFailsWith<IllegalStateException> { SchemaSnapshotGenerator(database, projectDir).generate() }

        assertContains(error.message.orEmpty(), "already has 1 tables")
    }

    @Test
    fun `leaves no candidate behind when a migration fails`() {
        val broken = tempDir.resolve("broken").createDirectories()
        broken.resolve("V99999999999999__broken.sql").writeText("ALTER TABLE does_not_exist ADD COLUMN x int;\n")
        val output = tempDir.resolve("schema/schema.md")
        output.parent.createDirectories()
        output.writeText("stale candidate from an earlier run")
        val generator =
            SchemaSnapshotGenerator(
                scratchDatabase(),
                projectDir,
                locations = listOf(SchemaSnapshotGenerator.MIGRATION_LOCATION, "filesystem:$broken"),
            )

        assertFailsWith<FlywayException> { writeSnapshot(output) { generator.generate() } }

        assertFalse(output.exists())
    }

    @Test
    fun `writes the candidate after a successful generation`() {
        val output = tempDir.resolve("schema/schema.md")
        val generator = SchemaSnapshotGenerator(scratchDatabase(), projectDir)

        writeSnapshot(output) { generator.generate() }

        assertTrue(output.readText().startsWith("<!-- Erzeugt von ./gradlew schemaSnapshot"))
    }

    private fun scratchDatabase(): SchemaSnapshotGenerator.Database {
        val name = "schema_snapshot_it_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(admin.url, admin.user, admin.password).use { connection ->
            connection.createStatement().use { it.execute("CREATE DATABASE \"$name\"") }
        }
        scratchDatabases += name
        return admin.copy(url = admin.url.substringBeforeLast('/') + "/" + name)
    }

    private fun requiredEnv(name: String): String = requireNotNull(System.getenv(name)) { "$name must point at the CI PostgreSQL service" }
}
