package com.hanmaum.dn.app.common.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenApiSecurityTest {
    private val prefix = "/api/v1"

    private fun specWith(vararg paths: Pair<String, PathItem>): OpenAPI {
        val openApi = OpenAPI().paths(Paths().apply { paths.forEach { (path, item) -> addPathItem(path, item) } })
        OpenApiConfig.applyBearerSecurity(openApi, prefix)
        return openApi
    }

    @Test
    fun `declares an http bearer JWT scheme and requires it globally`() {
        val openApi = specWith()

        val scheme = openApi.components.securitySchemes[OpenApiConfig.BEARER_SCHEME]!!
        assertEquals(SecurityScheme.Type.HTTP, scheme.type)
        assertEquals("bearer", scheme.scheme)
        assertEquals("JWT", scheme.bearerFormat)
        assertNull(scheme.flows, "no OAuth flow, so Swagger UI never asks for a password or secret")
        assertEquals(
            listOf(OpenApiConfig.BEARER_SCHEME),
            openApi.security
                .single()
                .keys
                .toList(),
        )
    }

    @Test
    fun `public operations need no login, their neighbours inherit the bearer requirement`() {
        val openApi =
            specWith(
                "$prefix/newcomer-forms/{token}" to PathItem().get(Operation()),
                "$prefix/newcomer-forms/{token}/submissions" to PathItem().post(Operation()),
                "$prefix/newcomer-forms" to PathItem().get(Operation()).post(Operation()),
                "$prefix/members/register" to PathItem().post(Operation()),
                "$prefix/announcements" to PathItem().get(Operation()),
            )

        assertEquals(emptyList(), openApi.paths["$prefix/newcomer-forms/{token}"]!!.get.security)
        assertEquals(emptyList(), openApi.paths["$prefix/newcomer-forms/{token}/submissions"]!!.post.security)
        assertEquals(emptyList(), openApi.paths["$prefix/members/register"]!!.post.security)
        assertNull(openApi.paths["$prefix/newcomer-forms"]!!.get.security)
        assertNull(openApi.paths["$prefix/newcomer-forms"]!!.post.security)
        assertNull(openApi.paths["$prefix/announcements"]!!.get.security)
    }

    @Test
    fun `route matching respects method and segment count`() {
        assertTrue(PublicApiRoutes.matches(HttpMethod.GET, "$prefix/newcomer-forms/{token}", prefix))
        assertFalse(PublicApiRoutes.matches(HttpMethod.PUT, "$prefix/newcomer-forms/{token}", prefix))
        assertFalse(PublicApiRoutes.matches(HttpMethod.GET, "$prefix/newcomer-forms/{token}/submissions", prefix))
        assertFalse(PublicApiRoutes.matches(HttpMethod.GET, "$prefix/members/register", prefix))
        assertFalse(PublicApiRoutes.matches(HttpMethod.POST, "/api/v2/members/register", prefix))
    }
}
