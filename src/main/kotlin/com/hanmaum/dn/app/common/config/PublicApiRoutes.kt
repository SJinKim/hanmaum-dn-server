package com.hanmaum.dn.app.common.config

import org.springframework.http.HttpMethod

/**
 * The API operations that answer without a token (#237). [SecurityConfig] permits exactly these,
 * and [OpenApiConfig] marks exactly these as needing no login in the spec (#239), so the two
 * cannot drift apart. `PUBLIC_ROUTES.md` explains each one.
 *
 * Paths are relative to `api.prefix`. `*` stands for one path segment.
 */
object PublicApiRoutes {
    val operations: List<Pair<HttpMethod, String>> =
        listOf(
            // The token in the link is the capability.
            HttpMethod.GET to "/newcomer-forms/*",
            HttpMethod.POST to "/newcomer-forms/*/submissions",
            // Self-registration, rate-limited in MemberService.
            HttpMethod.POST to "/members/register",
        )

    /** True if [path], a spec path such as `/api/v1/newcomer-forms/{token}`, is public for [method]. */
    fun matches(
        method: HttpMethod,
        path: String,
        apiPrefix: String,
    ): Boolean {
        val segments = path.split('/')
        return operations.any { (publicMethod, pattern) ->
            val patternSegments = "$apiPrefix$pattern".split('/')
            publicMethod == method &&
                patternSegments.size == segments.size &&
                patternSegments.zip(segments).all { (expected, actual) -> expected == "*" || expected == actual }
        }
    }
}
