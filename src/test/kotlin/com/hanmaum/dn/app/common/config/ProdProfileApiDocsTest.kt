package com.hanmaum.dn.app.common.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource

/** ST and prod both run the `prod` profile; neither may serve the API docs (#237). */
class ProdProfileApiDocsTest {
    private val prod =
        YamlPropertiesFactoryBean()
            .apply { setResources(ClassPathResource("application-prod.yml")) }
            .getObject()!!

    @Test
    fun `the prod profile turns off api-docs and swagger-ui`() {
        assertEquals("false", prod.getProperty("springdoc.api-docs.enabled"))
        assertEquals("false", prod.getProperty("springdoc.swagger-ui.enabled"))
    }
}
