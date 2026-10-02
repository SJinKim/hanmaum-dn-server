package com.hanmaum.dn.app.common.security

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtTimestampValidator

/**
 * Accepts only Keycloak access tokens issued for this API in this environment (#236).
 *
 * - `exp`/`nbf`: the standard timestamp check.
 * - `iss`: must be one of [allowedIssuers]. ST and prod share one Keycloak instance and
 *   differ only by realm, so the issuer is what keeps a staging token out of prod.
 * - `aud`: must contain [audience]. Only the user clients carry the audience scope, so
 *   tokens of other realm clients (e.g. the backend's own service account) fail here.
 * - `typ`: Keycloak marks ID tokens `ID` and refresh tokens `Refresh`. Anything other than
 *   `Bearer` is not an API access token, even if the other claims happen to match.
 *
 * The signature is checked by the decoder before any of this runs.
 */
fun accessTokenValidator(
    allowedIssuers: List<String>,
    audience: String,
): OAuth2TokenValidator<Jwt> {
    val issuers = allowedIssuers.map { it.trim() }.filter { it.isNotEmpty() }
    check(issuers.isNotEmpty()) { "app.security.allowed-issuers is empty; refusing to start" }
    check(audience.isNotBlank()) { "app.security.audience is empty; refusing to start" }

    val issuerValidator =
        OAuth2TokenValidator<Jwt> { jwt ->
            val issuer = jwt.getClaimAsString("iss")
            if (issuer in issuers) {
                OAuth2TokenValidatorResult.success()
            } else {
                invalid("Dieser Issuer wird nicht akzeptiert: $issuer")
            }
        }

    val audienceValidator =
        OAuth2TokenValidator<Jwt> { jwt ->
            if (jwt.audience.orEmpty().contains(audience)) {
                OAuth2TokenValidatorResult.success()
            } else {
                invalid("Das Token ist nicht für diese API ausgestellt")
            }
        }

    val tokenTypeValidator =
        OAuth2TokenValidator<Jwt> { jwt ->
            val type = jwt.getClaimAsString("typ")
            if (type == null || type == ACCESS_TOKEN_TYPE) {
                OAuth2TokenValidatorResult.success()
            } else {
                invalid("Nur Access Tokens werden akzeptiert")
            }
        }

    return DelegatingOAuth2TokenValidator(
        JwtTimestampValidator(),
        issuerValidator,
        audienceValidator,
        tokenTypeValidator,
    )
}

private const val ACCESS_TOKEN_TYPE = "Bearer"

private fun invalid(description: String) =
    OAuth2TokenValidatorResult.failure(OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null))
