package com.hanmaum.dn.app.common.security

import org.junit.jupiter.api.assertThrows
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessTokenValidatorTest {
    private val issuer = "https://auth.example.test/realms/hanmaum-dn-prod"
    private val validator = accessTokenValidator(listOf(issuer), "hanmaum-dn-api")

    private fun token(
        iss: String = issuer,
        aud: List<String> = listOf("hanmaum-dn-api", "account"),
        typ: String? = "Bearer",
        expiresAt: Instant = Instant.now().plusSeconds(300),
    ): Jwt {
        val builder =
            Jwt
                .withTokenValue("token")
                .header("alg", "RS256")
                .issuer(iss)
                .audience(aud)
                .issuedAt(expiresAt.minusSeconds(600))
                .expiresAt(expiresAt)
        if (typ != null) builder.claim("typ", typ)
        return builder.build()
    }

    @Test
    fun `an access token for this API and environment passes`() {
        assertFalse(validator.validate(token()).hasErrors())
    }

    @Test
    fun `a token without a typ claim passes when the other claims match`() {
        assertFalse(validator.validate(token(typ = null)).hasErrors())
    }

    @Test
    fun `a token from the other environment's realm fails`() {
        val staging = token(iss = "https://auth.example.test/realms/hanmaum-dn-st")
        assertTrue(validator.validate(staging).hasErrors())
    }

    @Test
    fun `a token without the API audience fails`() {
        assertTrue(validator.validate(token(aud = listOf("account"))).hasErrors())
    }

    @Test
    fun `an ID token fails even with the API audience`() {
        assertTrue(validator.validate(token(typ = "ID")).hasErrors())
    }

    @Test
    fun `a refresh token fails`() {
        assertTrue(validator.validate(token(typ = "Refresh")).hasErrors())
    }

    @Test
    fun `an expired token fails`() {
        val expired = token(expiresAt = Instant.now().minusSeconds(3600))
        assertTrue(validator.validate(expired).hasErrors())
    }

    @Test
    fun `an empty issuer list stops the start`() {
        assertThrows<IllegalStateException> { accessTokenValidator(listOf(" "), "hanmaum-dn-api") }
    }

    @Test
    fun `a blank audience stops the start`() {
        assertThrows<IllegalStateException> { accessTokenValidator(listOf(issuer), " ") }
    }
}
