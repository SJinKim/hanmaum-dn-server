package com.hanmaum.dn.app.common.config

import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlin.test.Test

/**
 * End-to-end check of the resource server against signed tokens (#236).
 *
 * Unlike the other controller tests, the JwtDecoder is not mocked: SecurityConfig builds the
 * real decoder, which fetches its keys from a local JWKS endpoint. The server is configured
 * like prod; tokens are shaped like Keycloak's.
 */
@WebMvcTest(TokenProbeController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class AccessTokenSecurityTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    // WebMvcConfig registers MemberStatusInterceptor, which the slice instantiates.
    @MockitoBean
    private lateinit var memberRepository: MemberRepository

    private fun bearer(token: String) = "Bearer $token"

    @Test
    fun `a dashboard access token with the admin role reaches an admin endpoint`() {
        mockMvc
            .perform(get("/token-probe/admin").header(HttpHeaders.AUTHORIZATION, bearer(token(roles = listOf("admin")))))
            .andExpect(status().isOk)
            .andExpect(content().string("admin"))
    }

    @Test
    fun `a mobile access token reaches an authenticated endpoint`() {
        val mobile = token(azp = "hanmaum-mobile", roles = listOf("user"))
        mockMvc
            .perform(get("/token-probe").header(HttpHeaders.AUTHORIZATION, bearer(mobile)))
            .andExpect(status().isOk)
    }

    @Test
    fun `a request without a token is rejected with 401`() {
        mockMvc
            .perform(get("/token-probe"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `an expired token is rejected with 401`() {
        val expired = token(expiresAt = Instant.now().minusSeconds(3600))
        mockMvc
            .perform(get("/token-probe").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `a staging token is rejected by the prod server with 401`() {
        // Same Keycloak instance, other realm: only the issuer tells them apart.
        val staging = token(issuer = STAGING_ISSUER)
        mockMvc
            .perform(get("/token-probe").header(HttpHeaders.AUTHORIZATION, bearer(staging)))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `a token signed with another realm's key is rejected with 401`() {
        val foreign = token(signingKey = otherRealmKey)
        mockMvc
            .perform(get("/token-probe").header(HttpHeaders.AUTHORIZATION, bearer(foreign)))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `a token of another realm client without the API audience is rejected with 401`() {
        // e.g. the backend's own service account: client credentials, no audience scope.
        val serviceAccount = token(azp = "dn-backend-admin", audience = listOf("account"), roles = listOf("admin"))
        mockMvc
            .perform(get("/token-probe/admin").header(HttpHeaders.AUTHORIZATION, bearer(serviceAccount)))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `an ID token is rejected with 401`() {
        val idToken = token(type = "ID", audience = listOf("hanmaum-dashboard"))
        mockMvc
            .perform(get("/token-probe").header(HttpHeaders.AUTHORIZATION, bearer(idToken)))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `a valid token without the required role is rejected with 403`() {
        mockMvc
            .perform(get("/token-probe/admin").header(HttpHeaders.AUTHORIZATION, bearer(token(roles = listOf("user")))))
            .andExpect(status().isForbidden)
    }

    companion object {
        private const val PROD_ISSUER = "https://auth.example.test/realms/hanmaum-dn-prod"
        private const val STAGING_ISSUER = "https://auth.example.test/realms/hanmaum-dn-st"

        private val realmKey: RSAKey = RSAKeyGenerator(2048).keyID("prod-key").generate()
        private val otherRealmKey: RSAKey = RSAKeyGenerator(2048).keyID("prod-key").generate()

        private val jwksServer: HttpServer =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                val body = JWKSet(realmKey.toPublicJWK()).toString().toByteArray()
                createContext("/certs") { exchange ->
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                start()
            }

        @JvmStatic
        @DynamicPropertySource
        fun securityProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") {
                "http://127.0.0.1:${jwksServer.address.port}/certs"
            }
            registry.add("app.security.allowed-issuers") { PROD_ISSUER }
        }

        @JvmStatic
        @AfterAll
        fun stopJwksServer() = jwksServer.stop(0)

        /** A token shaped like a Keycloak access token from the dashboard client. */
        private fun token(
            issuer: String = PROD_ISSUER,
            audience: List<String> = listOf("hanmaum-dn-api", "account"),
            azp: String = "hanmaum-dashboard",
            type: String = "Bearer",
            roles: List<String> = listOf("user"),
            expiresAt: Instant = Instant.now().plusSeconds(300),
            signingKey: RSAKey = realmKey,
        ): String {
            val claims =
                JWTClaimsSet
                    .Builder()
                    .issuer(issuer)
                    .subject(UUID.randomUUID().toString())
                    .audience(audience)
                    .issueTime(Date.from(expiresAt.minusSeconds(600)))
                    .expirationTime(Date.from(expiresAt))
                    .claim("typ", type)
                    .claim("azp", azp)
                    .claim("realm_access", mapOf("roles" to roles))
                    .claim("preferred_username", "tester")
                    .build()
            val header =
                JWSHeader
                    .Builder(JWSAlgorithm.RS256)
                    .keyID(signingKey.keyID)
                    .type(JOSEObjectType.JWT)
                    .build()
            return SignedJWT(header, claims).apply { sign(RSASSASigner(signingKey)) }.serialize()
        }
    }
}

@RestController
private class TokenProbeController {
    @GetMapping("/token-probe")
    fun probe(): String = "ok"

    @GetMapping("/token-probe/admin")
    @PreAuthorize("hasRole('ADMIN')")
    fun admin(): String = "admin"
}
