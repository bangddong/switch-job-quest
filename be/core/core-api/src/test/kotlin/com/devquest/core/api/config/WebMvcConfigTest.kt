package com.devquest.core.api.config

import com.devquest.core.api.support.DailyEvaluateRateLimitBucketStore
import com.devquest.core.api.support.DailyEvaluateRateLimitInterceptor
import com.devquest.core.api.support.DailyExplainRateLimitBucketStore
import com.devquest.core.api.support.DailyExplainRateLimitInterceptor
import com.devquest.core.api.support.RateLimitBucketStore
import com.devquest.core.api.support.TechInterviewRateLimitInterceptor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.handler.MappedInterceptor
import tools.jackson.databind.ObjectMapper

/**
 * WebMvcConfig의 실제 경로 매핑(addPathPatterns)이 의도한 인터셉터에만 연결되는지 검증한다.
 * RateLimitBucketIsolationTest는 "서로 다른 버킷이면 서로 영향받지 않는다"만 증명하므로,
 * 실제 요청 경로 → 인터셉터 라우팅은 이 테스트가 담당한다.
 */
class WebMvcConfigTest {

    private val techInterviewRateLimitInterceptor = TechInterviewRateLimitInterceptor(
        RateLimitBucketStore(capacity = 2, refillDays = 1),
        ObjectMapper(),
    )
    private val dailyExplainRateLimitInterceptor = DailyExplainRateLimitInterceptor(
        DailyExplainRateLimitBucketStore(capacity = 5, refillDays = 1),
        ObjectMapper(),
    )
    private val dailyEvaluateRateLimitInterceptor = DailyEvaluateRateLimitInterceptor(
        DailyEvaluateRateLimitBucketStore(capacity = 1, refillDays = 1),
        ObjectMapper(),
    )

    private val webMvcConfig = WebMvcConfig(
        techInterviewRateLimitInterceptor,
        dailyExplainRateLimitInterceptor,
        dailyEvaluateRateLimitInterceptor,
    )

    /** WebMvcConfig.addInterceptors()를 실제 InterceptorRegistry에 적용한 뒤 등록된 MappedInterceptor 목록을 뽑아낸다. */
    private fun registerMappedInterceptors(): List<MappedInterceptor> {
        val registry = InterceptorRegistry()
        webMvcConfig.addInterceptors(registry)

        val getInterceptors = InterceptorRegistry::class.java.getDeclaredMethod("getInterceptors")
        getInterceptors.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val interceptors = getInterceptors.invoke(registry) as List<Any>
        return interceptors.filterIsInstance<MappedInterceptor>()
    }

    private fun patternsFor(mapped: List<MappedInterceptor>, interceptor: Any): List<String> =
        mapped.filter { it.interceptor === interceptor }
            .flatMap { it.includePathPatterns?.toList() ?: emptyList() }

    @Test
    fun `daily-question evaluate 경로는 daily-evaluate 인터셉터에만 매핑된다`() {
        val mapped = registerMappedInterceptors()

        assertThat(patternsFor(mapped, dailyEvaluateRateLimitInterceptor))
            .contains("/api/v1/daily-question/evaluate")
        assertThat(patternsFor(mapped, techInterviewRateLimitInterceptor))
            .doesNotContain("/api/v1/daily-question/evaluate")
        assertThat(patternsFor(mapped, dailyExplainRateLimitInterceptor))
            .doesNotContain("/api/v1/daily-question/evaluate")
    }

    @Test
    fun `tech-interview 경로는 tech-interview 인터셉터에만 매핑된다`() {
        val mapped = registerMappedInterceptors()

        assertThat(patternsFor(mapped, techInterviewRateLimitInterceptor))
            .contains("/api/v1/tech-interview/**")
        assertThat(patternsFor(mapped, dailyEvaluateRateLimitInterceptor))
            .doesNotContain("/api/v1/tech-interview/**")
        assertThat(patternsFor(mapped, dailyExplainRateLimitInterceptor))
            .doesNotContain("/api/v1/tech-interview/**")
    }

    @Test
    fun `daily-question explain 경로는 daily-explain 인터셉터에만 매핑된다`() {
        val mapped = registerMappedInterceptors()

        assertThat(patternsFor(mapped, dailyExplainRateLimitInterceptor))
            .contains("/api/v1/daily-question/explain")
        assertThat(patternsFor(mapped, techInterviewRateLimitInterceptor))
            .doesNotContain("/api/v1/daily-question/explain")
        assertThat(patternsFor(mapped, dailyEvaluateRateLimitInterceptor))
            .doesNotContain("/api/v1/daily-question/explain")
    }

    @Test
    fun `각 인터셉터에 등록된 경로 패턴은 정확히 하나씩이다 (중복 등록 회귀 방지)`() {
        val mapped = registerMappedInterceptors()

        // 누군가 tech-interview 등록에 daily-question 경로를 실수로 추가하면 이 assertion이 깨진다.
        assertThat(patternsFor(mapped, techInterviewRateLimitInterceptor))
            .containsExactly("/api/v1/tech-interview/**")
        assertThat(patternsFor(mapped, dailyExplainRateLimitInterceptor))
            .containsExactly("/api/v1/daily-question/explain")
        assertThat(patternsFor(mapped, dailyEvaluateRateLimitInterceptor))
            .containsExactly("/api/v1/daily-question/evaluate")
    }
}
