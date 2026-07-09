package com.hanmaum.dn.app.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    @Value("\${api.prefix:/api/v1}") private val apiPrefix: String,
    // JWK URI is resolved per-profile (application-dev.yml / application-prod.yml).
    // Never hardcode a realm name here — use \${KEYCLOAK_REALM} in the yml files.
    // [AI-GUARD] Do not change this default or inline a realm name.
    @Value(
        "\${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:http://hanmaumApp-keycloak:8090/realms/\${KEYCLOAK_REALM:hanmaum}/protocol/openid-connect/certs}",
    )
    private val jwkSetUri: String,
    // Allowed JWT issuers are declared in application-dev.yml / application-prod.yml.
    // Each environment defines its own list via \${KEYCLOAK_REALM} or env vars.
    // [AI-GUARD] Never add hardcoded realm names or issuer URLs to this list in code.
    @Value("\${app.security.allowed-issuers}")
    private val configuredIssuers: List<String>,
    @Value("\${app.cors.allowed-origins:http://localhost:4200,http://localhost}")
    private val allowedOrigins: List<String>,
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/health", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "$apiPrefix/announcements")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "$apiPrefix/albums")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }

        return http.build()
    }

    @Bean
    fun webSecurityCustomizer(): WebSecurityCustomizer =
        WebSecurityCustomizer { web ->
            web
                .ignoring()
                .requestMatchers(HttpMethod.POST, "$apiPrefix/members/register")
        }

    /**
     * WICHTIG: Mappt Keycloak-Rollen auf Spring Authorities.
     * Keycloak speichert Rollen in "realm_access" -> "roles".
     * Spring braucht aber "ROLE_ADMIN". Das machen wir hier.
     */
    @Bean
    fun jwtAuthenticationConverter(): Converter<Jwt, AbstractAuthenticationToken> =
        Converter { jwt ->
            // hole den 'realm_access' Teil aus dem Token JSON
            val realmAccess = jwt.claims["realm_access"] as? Map<String, Any>
            val roles = realmAccess?.get("roles") as? List<String> ?: emptyList()

            // wandle jede Rolle (z.B. "admin") in "ROLE_ADMIN" um
            val authorities =
                roles.map { role ->
                    SimpleGrantedAuthority("ROLE_${role.uppercase()}")
                }

            // Rückgabe: Ein Token-Objekt, das Spring versteht (User + Rollen)
            JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("preferred_username"))
        }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        // JWK URI is resolved from spring.security.oauth2.resourceserver.jwt.jwk-set-uri:
        //   - dev profile:  application-dev.yml  → http://localhost:8091/realms/...
        //   - Docker:       env var               → http://hanmaumApp-keycloak:8090/realms/...
        val jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()

        // Issuers are declared in application-dev.yml / application-prod.yml — never hardcoded.
        // [AI-GUARD] Do not add inline issuer URLs here; edit the yml files instead.
        val issuerValidator =
            OAuth2TokenValidator<Jwt> { jwt ->
                val issuerClaim = jwt.getClaimAsString("iss")
                if (configuredIssuers.contains(issuerClaim)) {
                    OAuth2TokenValidatorResult.success()
                } else {
                    OAuth2TokenValidatorResult.failure(
                        OAuth2Error(
                            "invalid_issuer",
                            "Dieser Issuer wird nicht akzeptiert: $issuerClaim",
                            null,
                        ),
                    )
                }
            }

        // 4. Standard-Validator (Zeitstempel) + Unser Issuer Validator kombinieren
        val timestampValidator = JwtTimestampValidator()
        val combinedValidator = DelegatingOAuth2TokenValidator(timestampValidator, issuerValidator)

        jwtDecoder.setJwtValidator(combinedValidator)

        return jwtDecoder
    }

    /**
     * CORS Konfiguration:
     * Erlaubt dem Angular Dashboard (localhost:4200), mit uns zu reden.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()

        // Wer darf anfragen? Configured via app.cors.allowed-origins per profile
        configuration.allowedOrigins = allowedOrigins

        // Was dürfen sie tun? (Alles)
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")

        // Welche Infos dürfen mitgeschickt werden? (Authorization Header ist wichtig)
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true // erlaubt Cookies/Auth-Header

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
