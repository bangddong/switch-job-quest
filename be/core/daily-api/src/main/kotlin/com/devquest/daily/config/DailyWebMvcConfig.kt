package com.devquest.daily.config

import com.devquest.daily.support.DailyEvaluateRateLimitInterceptor
import com.devquest.daily.support.DailyExplainRateLimitInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * core-api `WebMvcConfig`와 동일한 경로 배선. `GET /api/v1/daily-question`은 레이트리밋이 없다
 * (함정 ⑨ — AI 호출이 없는 뱅크 전용 조회이므로 무제한).
 */
@Configuration
class DailyWebMvcConfig(
    private val dailyExplainRateLimitInterceptor: DailyExplainRateLimitInterceptor,
    private val dailyEvaluateRateLimitInterceptor: DailyEvaluateRateLimitInterceptor,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(dailyExplainRateLimitInterceptor)
            .addPathPatterns("/api/v1/daily-question/explain")
        registry.addInterceptor(dailyEvaluateRateLimitInterceptor)
            .addPathPatterns("/api/v1/daily-question/evaluate")
    }
}
