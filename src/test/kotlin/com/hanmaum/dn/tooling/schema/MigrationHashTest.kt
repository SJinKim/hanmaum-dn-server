package com.hanmaum.dn.tooling.schema

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.moveTo
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class MigrationHashTest {
    @TempDir lateinit var project: Path

    private val sqlDir get() = project.resolve("src/main/resources/db/migration")
    private val kotlinDir get() = project.resolve("src/main/kotlin/db/migration")

    private fun hash() = MigrationHash.of(project, listOf(sqlDir, kotlinDir))

    private fun seed() {
        sqlDir.createDirectories()
        kotlinDir.createDirectories()
        sqlDir.resolve("V1__init.sql").writeText("CREATE TABLE a (id bigint);\n")
        sqlDir.resolve("V2__data.sql").writeText("UPDATE a SET id = 1;\n")
        kotlinDir.resolve("V3__Copy.kt").writeText("class V3__Copy\n")
    }

    @Test
    fun `is stable for unchanged migrations and independent of the directory order`() {
        seed()
        val first = hash()

        assertEquals(first, hash())
        assertEquals(first, MigrationHash.of(project, listOf(kotlinDir, sqlDir)))
        assertEquals(64, first.length)
    }

    @Test
    fun `changes when a data-only SQL migration changes`() {
        seed()
        val before = hash()

        sqlDir.resolve("V2__data.sql").writeText("UPDATE a SET id = 2;\n")

        assertNotEquals(before, hash())
    }

    @Test
    fun `changes when a Kotlin migration changes`() {
        seed()
        val before = hash()

        kotlinDir.resolve("V3__Copy.kt").writeText("class V3__Copy // changed\n")

        assertNotEquals(before, hash())
    }

    @Test
    fun `changes when a migration is renamed, added or deleted`() {
        seed()
        val original = hash()

        sqlDir.resolve("V2__data.sql").moveTo(sqlDir.resolve("V2__renamed.sql"))
        val renamed = hash()
        sqlDir.resolve("V4__more.sql").writeText("SELECT 1;\n")
        val added = hash()
        sqlDir.resolve("V1__init.sql").deleteExisting()
        val deleted = hash()

        assertEquals(4, setOf(original, renamed, added, deleted).size)
    }

    @Test
    fun `does not confuse path and content boundaries`() {
        sqlDir.createDirectories()
        kotlinDir.createDirectories()
        sqlDir.resolve("V1__a.sql").writeText("bV2__c.sql")
        val first = hash()

        Files.delete(sqlDir.resolve("V1__a.sql"))
        sqlDir.resolve("V1__a.sqlb").writeText("V2__c.sql")

        assertNotEquals(first, hash())
    }

    @Test
    fun `fails when a migration directory is missing`() {
        sqlDir.createDirectories()

        assertFailsWith<IllegalArgumentException> { hash() }
    }
}
