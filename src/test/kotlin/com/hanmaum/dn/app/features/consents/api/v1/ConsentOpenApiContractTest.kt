package com.hanmaum.dn.app.features.consents.api.v1

import com.hanmaum.dn.app.common.config.OpenApiConfig
import com.hanmaum.dn.app.common.domainvalue.ConsentPurpose
import com.hanmaum.dn.app.features.consents.api.v1.dto.ConsentState
import com.hanmaum.dn.app.features.consents.api.v1.dto.MemberConsentStatus
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.util.Json31
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import org.springdoc.core.customizers.GlobalOpenApiCustomizer
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsentOpenApiContractTest {
    private val mapper = JsonMapper.builder().findAndAddModules().build()

    private fun customizedSpec(): OpenAPI {
        val spec =
            OpenAPI().components(
                Components().schemas(ModelConverters.getInstance(true).readAll(MemberConsentStatus::class.java)),
            )
        AnnotationConfigApplicationContext(OpenApiConfig::class.java).use { context ->
            context.getBeansOfType(GlobalOpenApiCustomizer::class.java).values.forEach { it.customise(spec) }
        }
        return spec
    }

    @Test
    fun `missing consent JSON is allowed by the generated schema`() {
        val missing = MemberConsentStatus(ConsentPurpose.entries.first(), ConsentState.MISSING, true, "v1", null, null)
        val wire = mapper.readTree(mapper.writeValueAsString(missing))
        val properties = customizedSpec().components.schemas["MemberConsentStatus"]!!.properties

        for (field in listOf("grantedVersion", "grantedAt")) {
            assertTrue(wire[field].isNull, "$field is actually serialized as null")
            assertTrue("null" in properties[field]!!.types.orEmpty(), "$field must permit null")
        }
        assertEquals(setOf("string", "null"), properties["grantedVersion"]!!.types)
        assertEquals("date-time", properties["grantedAt"]!!.format)
        assertEquals(setOf("string"), properties["currentVersion"]!!.types)
        assertTrue(Json31.mapper().writeValueAsString(properties["grantedAt"]).contains("null"))
    }
}
