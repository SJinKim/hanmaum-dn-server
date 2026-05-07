package com.hanmaum.dn.app.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.HandlerTypePredicate
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

@Configuration
class ApiPrefixConfig(
    @Value("\${api.prefix:/api/v1}") private val apiPrefix: String,
) : WebMvcRegistrations {
    override fun getRequestMappingHandlerMapping(): RequestMappingHandlerMapping {
        val mapping = RequestMappingHandlerMapping()
        mapping.setPathPrefixes(
            mapOf(apiPrefix to HandlerTypePredicate.forBasePackage("com.hanmaum.dn.app.features")),
        )
        return mapping
    }
}
