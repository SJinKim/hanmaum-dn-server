package com.hanmaum.dn.app.common.pii

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

class PiiCryptoServiceTest {
    private val encryptionKey =
        SecretKeySpec(ByteArray(32) { index -> (index + 1).toByte() }, "AES")
    private val indexKey =
        SecretKeySpec(ByteArray(32) { index -> (index + 33).toByte() }, "HmacSHA256")
    private val service =
        PiiCryptoService(
            keyring =
                PiiKeyring(
                    activeKeyId = "v1",
                    encryptionKeys = mapOf("v1" to encryptionKey),
                    indexKey = indexKey,
                ),
            legacyPlaintextReadEnabled = true,
        )

    @Test
    fun `encrypt uses a fresh nonce and decrypts both values`() {
        val first = service.encrypt("kim@example.com", "members.email")
        val second = service.encrypt("kim@example.com", "members.email")

        assertNotEquals(first, second)
        assertEquals("kim@example.com", service.decrypt(first, "members.email"))
        assertEquals("kim@example.com", service.decrypt(second, "members.email"))
    }

    @Test
    fun `ciphertext cannot be moved to another field context`() {
        val encrypted = service.encrypt("Kim", "members.last_name")

        assertThrows(IllegalStateException::class.java) {
            service.decrypt(encrypted, "members.first_name")
        }
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val encrypted = service.encrypt("secret", "members.email")!!
        val parts = encrypted.split("$").toMutableList()
        val ciphertext = Base64.getUrlDecoder().decode(parts[3])
        ciphertext[ciphertext.lastIndex] = (ciphertext.last().toInt() xor 1).toByte()
        parts[3] = Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext)

        assertThrows(IllegalStateException::class.java) {
            service.decrypt(parts.joinToString("$"), "members.email")
        }
    }

    @Test
    fun `lookup hash normalizes case unicode and whitespace`() {
        val first = service.lookupHash("  TEST@Example.COM ")
        val second = service.lookupHash("test@example.com")

        assertEquals(first, second)
    }

    @Test
    fun `legacy plaintext can be read during migration`() {
        assertEquals("legacy", service.decrypt("legacy", "members.email"))
    }

    @Test
    fun `legacy plaintext is rejected after cutover`() {
        val strictService =
            PiiCryptoService(
                keyring = PiiKeyring("v1", mapOf("v1" to encryptionKey), indexKey),
                legacyPlaintextReadEnabled = false,
            )

        assertThrows(IllegalStateException::class.java) {
            strictService.decrypt("legacy", "members.email")
        }
    }

    @Test
    fun `strict keyring configuration fails closed without keys`() {
        assertThrows(IllegalStateException::class.java) {
            PiiKeyringLoader.load(
                PiiProperties(
                    strict = true,
                    keyringPath = "",
                    encryptionKey = "",
                    indexKey = "",
                ),
            )
        }
    }

    @Test
    fun `local plaintext mode writes readable values and still decrypts ciphertext`() {
        val plaintextService =
            PiiCryptoService(
                keyring = PiiKeyring("v1", mapOf("v1" to encryptionKey), indexKey),
                legacyPlaintextReadEnabled = false,
                plaintextWriteEnabled = true,
            )
        val existingCiphertext = service.encrypt("encrypted", "members.email")

        assertEquals("readable", plaintextService.encrypt("readable", "members.email"))
        assertEquals("readable", plaintextService.decrypt("readable", "members.email"))
        assertEquals("encrypted", plaintextService.decrypt(existingCiphertext, "members.email"))
        assertEquals(null, plaintextService.storageKeyId())
    }
}
