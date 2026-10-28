package com.hanmaum.dn.tooling.schema

/** Byte comparison of the checked-in snapshot with a freshly generated candidate. */
object SnapshotComparison {
    private const val MAX_DIFF_LINES = 80

    /** Returns `null` when the checked-in snapshot is current, otherwise why it is not. */
    fun problem(
        checkedIn: String?,
        candidate: String,
    ): String? {
        if (checkedIn == null) return "docs/db/schema.md is missing."
        if (checkedIn == candidate) return null

        val reasons = mutableListOf<String>()
        val checkedInHash = hashLine(checkedIn)
        val candidateHash = hashLine(candidate)
        if (checkedInHash != candidateHash) {
            reasons += "The migrations changed since the snapshot was generated (${checkedInHash ?: "no hash"} -> $candidateHash)."
        }
        reasons += "docs/db/schema.md differs from the schema the migrations produce (- checked in, + generated):"
        reasons += diff(checkedIn.split('\n'), candidate.split('\n'))
        return reasons.joinToString("\n")
    }

    private fun hashLine(snapshot: String): String? =
        snapshot
            .lineSequence()
            .firstOrNull { it.startsWith(SchemaMarkdownRenderer.HASH_LABEL) }
            ?.removePrefix(SchemaMarkdownRenderer.HASH_LABEL)

    /** Minimal line diff (longest common subsequence after trimming the common prefix and suffix). */
    internal fun diff(
        old: List<String>,
        new: List<String>,
    ): String {
        val prefix = old.zip(new).takeWhile { (a, b) -> a == b }.size
        val suffix =
            old
                .drop(prefix)
                .asReversed()
                .zip(new.drop(prefix).asReversed())
                .takeWhile { (a, b) -> a == b }
                .size
        val a = old.subList(prefix, old.size - suffix)
        val b = new.subList(prefix, new.size - suffix)

        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) {
            for (j in b.indices.reversed()) {
                lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }

        val lines = mutableListOf<String>()
        var i = 0
        var j = 0
        while (i < a.size || j < b.size) {
            when {
                i < a.size && j < b.size && a[i] == b[j] -> {
                    i++
                    j++
                }
                j < b.size && (i == a.size || lcs[i][j + 1] >= lcs[i + 1][j]) -> lines += "line ${prefix + j + 1}: + ${b[j++]}"
                else -> lines += "line ${prefix + i + 1}: - ${a[i++]}"
            }
        }
        val shown = lines.take(MAX_DIFF_LINES)
        val omitted = lines.size - shown.size
        return (shown + listOfNotNull(if (omitted > 0) "... $omitted more changed lines" else null)).joinToString("\n")
    }
}
