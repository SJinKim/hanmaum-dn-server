package com.hanmaum.dn.tooling.schema

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SnapshotComparisonTest {
    private fun snapshot(
        hash: String,
        vararg columns: String,
    ) = "# DB-Schema\n\n${SchemaMarkdownRenderer.HASH_LABEL}`$hash`\n\n## public.members\n\n" +
        columns.joinToString("") { "- `$it` text\n" }

    @Test
    fun `accepts an identical snapshot`() {
        val current = snapshot("h1", "id", "email")

        assertNull(SnapshotComparison.problem(current, current))
    }

    @Test
    fun `reports a missing snapshot`() {
        assertEquals("docs/db/schema.md is missing.", SnapshotComparison.problem(null, snapshot("h1", "id")))
    }

    @Test
    fun `reports changed migrations and shows the differing lines`() {
        val problem = assertNotNull(SnapshotComparison.problem(snapshot("old", "id"), snapshot("new", "id", "email")))

        assertContains(problem, "The migrations changed since the snapshot was generated (`old` -> `new`).")
        assertContains(problem, "line 3: - ${SchemaMarkdownRenderer.HASH_LABEL}`old`")
        assertContains(problem, "line 3: + ${SchemaMarkdownRenderer.HASH_LABEL}`new`")
        assertContains(problem, "line 8: + - `email` text")
    }

    @Test
    fun `reports a hand-edited snapshot even when the migration hash matches`() {
        val tampered = snapshot("h1", "id", "email").replace("`email` text", "`email` varchar")

        val problem = assertNotNull(SnapshotComparison.problem(tampered, snapshot("h1", "id", "email")))

        assertFalse(problem.contains("migrations changed"))
        assertContains(problem, "line 8: - - `email` varchar")
        assertContains(problem, "line 8: + - `email` text")
    }

    @Test
    fun `reports a difference in trailing whitespace`() {
        val current = snapshot("h1", "id")

        assertNotNull(SnapshotComparison.problem(current.trimEnd(), current))
    }

    @Test
    fun `limits the diff output`() {
        val diff = SnapshotComparison.diff(List(200) { "old $it" }, List(200) { "new $it" })

        assertEquals(81, diff.lines().size)
        assertContains(diff, "... 320 more changed lines")
    }
}
