package com.hanmaum.dn.app.common.config

import com.hanmaum.dn.app.common.security.Roles
import com.hanmaum.dn.app.common.security.accessTokenValidator
import com.hanmaum.dn.app.common.security.securityProblemDetail
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.ObjectMapper

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
    // The `aud` value Keycloak puts into access tokens for this API (#235, #236).
    // No default in code: application.yml sets it, and a blank value stops the start.
    @Value("\${app.security.audience}")
    private val apiAudience: String,
    @Value("\${app.cors.allowed-origins:http://localhost:4200,http://localhost}")
    private val allowedOrigins: List<String>,
) {
    @Bean
    fun filterChain(
        http: HttpSecurity,
        problemAuthenticationEntryPoint: AuthenticationEntryPoint,
        problemAccessDeniedHandler: AccessDeniedHandler,
    ): SecurityFilterChain {
        http
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests { auth ->
                auth
                    // Prometheus reaches this only through the private observability network.
                    // Caddy blocks every public /actuator/* path except health.
                    .requestMatchers("/actuator/health", "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "$apiPrefix/newcomer-forms/*")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "$apiPrefix/newcomer-forms/*/submissions")
                    .permitAll()
                    // Self-registration. permitAll instead of web.ignoring(), so CORS and the
                    // security headers still apply (#237). Rate-limited in MemberService.
                    .requestMatchers(HttpMethod.POST, "$apiPrefix/members/register")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.authenticationEntryPoint(problemAuthenticationEntryPoint)
                oauth2.accessDeniedHandler(problemAccessDeniedHandler)
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }.exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint(problemAuthenticationEntryPoint)
                exceptions.accessDeniedHandler(problemAccessDeniedHandler)
            }

        return http.build()
    }

    @Bean
    fun problemAuthenticationEntryPoint(objectMapper: ObjectMapper): AuthenticationEntryPoint =
        AuthenticationEntryPoint { _, response, _ ->
            writeSecurityProblem(response, HttpStatus.UNAUTHORIZED, objectMapper)
        }

    @Bean
    fun problemAccessDeniedHandler(objectMapper: ObjectMapper): AccessDeniedHandler =
        AccessDeniedHandler { _, response, _ ->
            writeSecurityProblem(response, HttpStatus.FORBIDDEN, objectMapper)
        }

    private fun writeSecurityProblem(
        response: HttpServletResponse,
        status: HttpStatus,
        objectMapper: ObjectMapper,
    ) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        objectMapper.writeValue(response.outputStream, securityProblemDetail(status))
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
            val granted =
                roles.map { role ->
                    SimpleGrantedAuthority("ROLE_${role.uppercase()}")
                }
            // Expand the hierarchy here too, so programmatic checks such as
            // `authorities.any { it.authority == "ROLE_ADMIN" }` also pass for a pastor.
            val authorities = roleHierarchy().getReachableGrantedAuthorities(granted)

            // Rückgabe: Ein Token-Objekt, das Spring versteht (User + Rollen)
            JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("preferred_username"))
        }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        // JWK URI is resolved from spring.security.oauth2.resourceserver.jwt.jwk-set-uri:
        //   - dev profile:  application-dev.yml  → http://localhost:8091/realms/...
        //   - Docker:       env var               → http://hanmaumApp-keycloak:8090/realms/...
        val jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()

        // Issuers and audience are declared in the yml files — never hardcoded.
        // [AI-GUARD] Do not add inline issuer URLs here; edit the yml files instead.
        jwtDecoder.setJwtValidator(accessTokenValidator(configuredIssuers, apiAudience))

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

    companion object {
        /**
         * PASTOR > ADMIN (#240). Static, so method security picks it up before
         * this configuration is instantiated.
         */
        @Bean
        @JvmStatic
        fun roleHierarchy(): RoleHierarchy = RoleHierarchyImpl.fromHierarchy(Roles.HIERARCHY)
    }
}
