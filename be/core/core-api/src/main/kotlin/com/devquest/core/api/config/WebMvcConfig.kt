package com.devquest.core.api.config

import com.devquest.core.api.support.TechInterviewRateLimitInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val techInterviewRateLimitInterceptor: TechInterviewRateLimitInterceptor,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(techInterviewRateLimitInterceptor)
            .addPathPatterns("/api/v1/tech-interview/**", "/api/v1/daily-question/evaluate")
    }
}
