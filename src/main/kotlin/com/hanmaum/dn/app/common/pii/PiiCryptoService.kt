package com.hanmaum.dn.app.common.pii

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec

class PiiCryptoService(
    private val keyring: PiiKeyring,
    private val legacyPlaintextReadEnabled: Boolean,
    private val plaintextWriteEnabled: Boolean = false,
) {
    private val secureRandom = SecureRandom()

    fun encrypt(
        plaintext: String?,
        context: String,
    ): String? {
        if (plaintext == null) {
            return null
        }
        if (plaintextWriteEnabled) {
            return plaintext
        }

        val nonce = ByteArray(NONCE_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            keyring.encryptionKeys.getValue(keyring.activeKeyId),
            GCMParameterSpec(TAG_BITS, nonce),
        )
        cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return listOf(
            PREFIX,
            keyring.activeKeyId,
            encoder.encodeToString(nonce),
            encoder.encodeToString(ciphertext),
        ).joinToString(SEPARATOR)
    }

    fun decrypt(
        storedValue: String?,
        context: String,
    ): String? {
        if (storedValue == null) {
            return null
        }
        if (!storedValue.startsWith("$PREFIX$SEPARATOR")) {
            check(legacyPlaintextReadEnabled || plaintextWriteEnabled) {
                "Plaintext PII encountered after legacy reads were disabled."
            }
            return storedValue
        }

        val parts = storedValue.split(SEPARATOR)
        check(parts.size == ENVELOPE_PARTS) { "Invalid encrypted PII envelope." }
        val key =
            keyring.encryptionKeys[parts[1]]
                ?: error("Encrypted PII references unavailable key '${parts[1]}'.")

        return try {
            val nonce = decoder.decode(parts[2])
            val ciphertext = decoder.decode(parts[3])
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } catch (exception: AEADBadTagException) {
            throw IllegalStateException("Encrypted PII failed authentication.", exception)
        } catch (exception: GeneralSecurityException) {
            throw IllegalStateException("Encrypted PII could not be decrypted.", exception)
        } catch (exception: IllegalArgumentException) {
            throw IllegalStateException("Encrypted PII envelope is malformed.", exception)
        }
    }

    fun lookupHash(value: String?): String? {
        if (value.isNullOrBlank()) {
            return null
        }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(keyring.indexKey)
        return encoder.encodeToString(mac.doFinal(normalize(value).toByteArray(Charsets.UTF_8)))
    }

    fun isEncrypted(value: String?): Boolean = value?.startsWith("$PREFIX$SEPARATOR") == true

    fun activeKeyId(): String = keyring.activeKeyId

    fun plaintextWriteEnabled(): Boolean = plaintextWriteEnabled

    fun storageKeyId(): String? = keyring.activeKeyId.takeUnless { plaintextWriteEnabled }

    fun normalize(value: String): String =
        Normalizer
            .normalize(value, Normalizer.Form.NFKC)
            .trim()
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE, " ")

    private companion object {
        const val PREFIX = "ENC1"
        const val SEPARATOR = "$"
        const val ENVELOPE_PARTS = 4
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
        val WHITESPACE = Regex("\\s+")
        val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        val decoder: Base64.Decoder = Base64.getUrlDecoder()
    }
}

object PiiCryptoContext {
    private val service = AtomicReference<PiiCryptoService?>()

    fun initialize(cryptoService: PiiCryptoService) {
        service.set(cryptoService)
    }

    fun encrypt(
        plaintext: String?,
        context: String,
    ): String? = requiredService().encrypt(plaintext, context)

    fun decrypt(
        storedValue: String?,
        context: String,
    ): String? = requiredService().decrypt(storedValue, context)

    fun lookupHash(value: String?): String? = requiredService().lookupHash(value)

    fun isEncrypted(value: String?): Boolean = requiredService().isEncrypted(value)

    fun activeKeyId(): String = requiredService().activeKeyId()

    fun plaintextWriteEnabled(): Boolean = requiredService().plaintextWriteEnabled()

    fun storageKeyId(): String? = requiredService().storageKeyId()

    fun normalize(value: String): String = requiredService().normalize(value)

    internal fun resetForTest() {
        service.set(null)
    }

    private fun requiredService(): PiiCryptoService =
        checkNotNull(service.get()) {
            "PII crypto has not been initialized. Configure PiiCryptoConfiguration before using encrypted entities."
        }
}
