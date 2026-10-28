package com.hanmaum.dn.tooling.schema

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes

/**
 * SHA-256 over the relative paths and contents of every migration file. It goes into the snapshot
 * header so that any migration change, rename or deletion, including a data-only migration with no
 * visible schema effect, makes the checked-in snapshot stale.
 */
object MigrationHash {
    fun of(
        projectDir: Path,
        migrationDirs: List<Path>,
    ): String {
        val root = projectDir.toAbsolutePath().normalize()
        val files =
            migrationDirs
                .flatMap { dir ->
                    require(Files.isDirectory(dir)) { "Migration directory not found: $dir" }
                    Files.walk(dir).use { paths -> paths.filter { it.isRegularFile() }.toList() }
                }.map { root.relativize(it.toAbsolutePath().normalize()).invariantSeparatorsPathString to it }
                .sortedBy { it.first }

        val digest = MessageDigest.getInstance("SHA-256")
        files.forEach { (relativePath, file) ->
            val content = file.readBytes()
            // Length prefixes keep path/content boundaries unambiguous.
            val path = relativePath.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(path.size.toLong()).array())
            digest.update(path)
            digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(content.size.toLong()).array())
            digest.update(content)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
