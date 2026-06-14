package com.hanmaum.dn.app.common.pii

import org.junit.jupiter.api.Assertions.assertThrows
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PiiLocalPlaintextPolicyTest {
    @Test
    fun `disabled mode is accepted without environment restrictions`() {
        val enabled =
            PiiLocalPlaintextPolicy.validateAndResolve(
                properties = PiiProperties(localPlaintextEnabled = false, strict = true),
                activeProfiles = setOf("prod"),
                datasourceUrl = "jdbc:postgresql://production-db:5432/hanmaum",
            )

        assertFalse(enabled)
    }

    @Test
    fun `dev profile accepts localhost database`() {
        val enabled =
            PiiLocalPlaintextPolicy.validateAndResolve(
                properties = PiiProperties(localPlaintextEnabled = true),
                activeProfiles = setOf("dev"),
                datasourceUrl = "jdbc:postgresql://localhost:5433/hanmaum",
            )

        assertTrue(enabled)
    }

    @Test
    fun `dev profile accepts local docker database`() {
        val enabled =
            PiiLocalPlaintextPolicy.validateAndResolve(
                properties = PiiProperties(localPlaintextEnabled = true),
                activeProfiles = setOf("dev"),
                datasourceUrl = "jdbc:postgresql://hanmaumApp-db:5432/hanmaum",
            )

        assertTrue(enabled)
    }

    @Test
    fun `production profile rejects plaintext mode`() {
        assertThrows(IllegalStateException::class.java) {
            PiiLocalPlaintextPolicy.validateAndResolve(
                properties = PiiProperties(localPlaintextEnabled = true),
                activeProfiles = setOf("prod"),
                datasourceUrl = "jdbc:postgresql://localhost:5432/hanmaum",
            )
        }
    }

    @Test
    fun `remote database rejects plaintext mode`() {
        assertThrows(IllegalStateException::class.java) {
            PiiLocalPlaintextPolicy.validateAndResolve(
                properties = PiiProperties(localPlaintextEnabled = true),
                activeProfiles = setOf("dev"),
                datasourceUrl = "jdbc:postgresql://database.example.com:5432/hanmaum",
            )
        }
    }

    @Test
    fun `strict mode rejects plaintext mode`() {
        assertThrows(IllegalStateException::class.java) {
            PiiLocalPlaintextPolicy.validateAndResolve(
                properties = PiiProperties(localPlaintextEnabled = true, strict = true),
                activeProfiles = setOf("dev"),
                datasourceUrl = "jdbc:postgresql://localhost:5432/hanmaum",
            )
        }
    }
}
