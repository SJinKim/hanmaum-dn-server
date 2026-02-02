package com.hanmaum.dn.app.common.config

import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class KeycloakConfig(
    @Value($$"${app.keycloak.server-url_docker}")
    private val serverUrl: String,

    @Value($$"${app.keycloak.realm}")
    private val realm: String,

    @Value($$"${app.keycloak.admin-client-id}")
    private val clientId: String,

    @Value($$"${app.keycloak.admin-client-secret}")
    private val clientSecret: String
) {

    @Bean
    fun keycloak(): Keycloak {
        return KeycloakBuilder.builder()
            .serverUrl(serverUrl)
            .realm(realm)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .build()
    }
}