package com.hanmaum.dn.app.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health", "/actuator/info").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }

        return http.build()
    }

    /**
     * WICHTIG: Mappt Keycloak-Rollen auf Spring Authorities.
     * Keycloak speichert Rollen in "realm_access" -> "roles".
     * Spring braucht aber "ROLE_ADMIN". Das machen wir hier.
     */
    @Bean
    fun jwtAuthenticationConverter(): Converter<Jwt, AbstractAuthenticationToken> {
        return Converter { jwt ->
            // hole den 'realm_access' Teil aus dem Token JSON
            val realmAccess = jwt.claims["realm_access"] as? Map<String, Any>
            val roles = realmAccess?.get("roles") as? List<String> ?: emptyList()

            // wandle jede Rolle (z.B. "admin") in "ROLE_ADMIN" um
            val authorities = roles.map { role ->
                SimpleGrantedAuthority("ROLE_${role.uppercase()}")
            }

            // Rückgabe: Ein Token-Objekt, das Spring versteht (User + Rollen)
            JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("preferred_username"))
        }
    }

    /**
     * CORS Konfiguration:
     * Erlaubt dem Angular Dashboard (localhost:4200), mit uns zu reden.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()

        // Wer darf anfragen? (Angular + Mobile Localhost)
        configuration.allowedOrigins = listOf("http://localhost:4200", "http://localhost")

        // Was dürfen sie tun? (Alles)
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")

        // Welche Infos dürfen mitgeschickt werden? (Authorization Header ist wichtig)
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true  // erlaubt Cookies/Auth-Header

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}