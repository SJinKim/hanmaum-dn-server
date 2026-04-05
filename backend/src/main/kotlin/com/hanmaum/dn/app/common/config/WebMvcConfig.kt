package com.hanmaum.dn.app.common.config

import com.hanmaum.dn.app.common.interceptor.MemberStatusInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val memberStatusInterceptor: MemberStatusInterceptor,
    @Value("\${api.prefix}") private val apiPrefix: String,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(memberStatusInterceptor)
            .addPathPatterns("$apiPrefix/**")
            .excludePathPatterns(
                "$apiPrefix/members/me",
                "$apiPrefix/members/register",
            )
    }
}
