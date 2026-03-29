package com.hanmaum.dn.app.common.config

import com.hanmaum.dn.app.common.interceptor.MemberStatusInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val memberStatusInterceptor: MemberStatusInterceptor,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(memberStatusInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/v1/members/me",
                "/api/v1/members/register",
            )
    }
}
