package com.hanmaum.dn.app.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {
    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix("/api/v1") { beanClass ->
            beanClass.isAnnotationPresent(RestController::class.java) &&
                beanClass.packageName.contains(".v1")
        }

        configurer.addPathPrefix("/api/v2") { beanClass ->
            beanClass.isAnnotationPresent(RestController::class.java) &&
                beanClass.packageName.contains(".v2")
        }
    }
}
