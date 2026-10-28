package com.hanmaum.dn.tooling.schema

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SchemaNotesTest {
    @Test
    fun `assigns multi-line Korean notes to their table and ignores the intro`() {
        val notes =
            SchemaNotes.parse(
                """
                # Fachliche Notizen

                Einleitung, die zu keiner Tabelle gehört.

                ## public.members

                새가족 등록 후 생성됩니다.
                ### Details
                - 소속은 verschlüsselt.

                ## public.groups
                Kleingruppen
                """.trimIndent(),
            )

        assertEquals(
            mapOf(
                "public.members" to "새가족 등록 후 생성됩니다.\n### Details\n- 소속은 verschlüsselt.",
                "public.groups" to "Kleingruppen",
            ),
            notes.byTable,
        )
    }

    @Test
    fun `normalises CRLF line endings`() {
        val notes = SchemaNotes.parse("## public.members\r\nerste\r\nzweite\r\n")

        assertEquals("erste\nzweite", notes.byTable["public.members"])
    }

    @Test
    fun `rejects a heading that is not a schema-qualified table`() {
        val error = assertFailsWith<IllegalArgumentException> { SchemaNotes.parse("## members\ntext") }

        assertContains(error.message.orEmpty(), "Line 1")
    }

    @Test
    fun `rejects duplicate notes for a table`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                SchemaNotes.parse("## public.members\na\n## public.members\nb")
            }

        assertContains(error.message.orEmpty(), "duplicate notes for public.members")
    }

    @Test
    fun `reports notes whose table is gone, including empty sections`() {
        val notes = SchemaNotes.parse("## public.members\ntext\n## public.removed\n")

        val error = assertFailsWith<IllegalArgumentException> { notes.requireKnownTables(setOf("public.members")) }

        assertContains(error.message.orEmpty(), "public.removed")
    }
}
