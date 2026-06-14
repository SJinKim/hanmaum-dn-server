package com.hanmaum.dn.app.common.pii

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import java.util.Properties
import javax.crypto.spec.SecretKeySpec

data class PiiKeyring(
    val activeKeyId: String,
    val encryptionKeys: Map<String, SecretKeySpec>,
    val indexKey: SecretKeySpec,
)

internal object PiiKeyringLoader {
    private const val MINIMUM_KEY_BYTES = 32

    fun load(properties: PiiProperties): PiiKeyring {
        val fromFile =
            properties.keyringPath
                .takeIf(String::isNotBlank)
                ?.let(::loadFile)
        if (fromFile != null) {
            return fromFile
        }

        if (properties.encryptionKey.isNotBlank() && properties.indexKey.isNotBlank()) {
            return PiiKeyring(
                activeKeyId = properties.activeKeyId,
                encryptionKeys =
                    mapOf(
                        properties.activeKeyId to decodeAesKey(properties.encryptionKey),
                    ),
                indexKey = decodeHmacKey(properties.indexKey),
            )
        }

        check(!properties.strict) {
            "PII protection is strict, but no keyring file or inline keys were configured."
        }
        return developmentKeyring()
    }

    private fun loadFile(pathValue: String): PiiKeyring {
        val path = Path.of(pathValue)
        check(Files.isRegularFile(path)) { "PII keyring file does not exist: $path" }

        val values =
            Properties().apply {
                Files.newInputStream(path).use(::load)
            }
        val activeKeyId = values.getProperty("active-key-id")?.trim().orEmpty()
        check(activeKeyId.isNotBlank()) { "PII keyring is missing active-key-id." }
        check(activeKeyId.matches(Regex("[A-Za-z0-9._-]+"))) {
            "PII active-key-id may only contain letters, digits, dot, underscore, and dash."
        }

        val encryptionKeys =
            values
                .stringPropertyNames()
                .filter { it.startsWith("key.") }
                .associate { name ->
                    name.removePrefix("key.") to decodeAesKey(values.getProperty(name))
                }
        check(encryptionKeys.containsKey(activeKeyId)) {
            "PII keyring does not contain active encryption key '$activeKeyId'."
        }

        val indexKey = values.getProperty("index-key")?.let(::decodeHmacKey)
        checkNotNull(indexKey) { "PII keyring is missing index-key." }

        return PiiKeyring(activeKeyId, encryptionKeys, indexKey)
    }

    private fun decodeAesKey(value: String): SecretKeySpec {
        val decoded = decode(value)
        check(decoded.size == MINIMUM_KEY_BYTES) {
            "PII AES key must be exactly 32 bytes (AES-256)."
        }
        return SecretKeySpec(decoded, "AES")
    }

    private fun decodeHmacKey(value: String): SecretKeySpec {
        val decoded = decode(value)
        check(decoded.size >= MINIMUM_KEY_BYTES) {
            "PII index key must contain at least 32 bytes."
        }
        return SecretKeySpec(decoded, "HmacSHA256")
    }

    private fun decode(value: String): ByteArray =
        try {
            Base64.getDecoder().decode(value.trim())
        } catch (exception: IllegalArgumentException) {
            throw IllegalStateException("PII keyring contains invalid Base64.", exception)
        }

    private fun developmentKeyring(): PiiKeyring {
        val encryptionKey = sha256("hanmaum-development-encryption-key")
        val indexKey = sha256("hanmaum-development-index-key")
        return PiiKeyring(
            activeKeyId = "development-v1",
            encryptionKeys = mapOf("development-v1" to SecretKeySpec(encryptionKey, "AES")),
            indexKey = SecretKeySpec(indexKey, "HmacSHA256"),
        )
    }

    private fun sha256(value: String): ByteArray =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
}
