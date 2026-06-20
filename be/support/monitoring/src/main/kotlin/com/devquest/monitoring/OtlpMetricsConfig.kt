package com.devquest.monitoring

import io.micrometer.core.instrument.Clock
import io.micrometer.registry.otlp.OtlpConfig
import io.micrometer.registry.otlp.OtlpMeterRegistry
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

@Configuration
@ConditionalOnProperty("grafana.otlp.enabled", havingValue = "true")
class OtlpMetricsConfig(
    @Value("\${grafana.otlp.instance-id}") private val instanceId: String,
    @Value("\${GRAFANA_API_KEY}") private val apiKey: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    @Volatile
    private var registry: OtlpMeterRegistry? = null

    @Bean
    fun otlpMeterRegistry(clock: Clock): OtlpMeterRegistry {
        require(instanceId.isNotBlank()) { "grafana.otlp.instance-id must not be blank" }
        require(apiKey.isNotBlank()) { "GRAFANA_API_KEY must not be blank" }
        val encoded = Base64.getEncoder()
            .encodeToString("$instanceId:$apiKey".toByteArray())
        val config = object : OtlpConfig {
            override fun get(key: String): String? = when (key) {
                "otlp.url" -> "https://otlp-gateway-prod-ap-northeast-0.grafana.net/otlp/v1/metrics"
                "otlp.step" -> "PT60S"
                // baseTimeUnit 기본값은 MILLISECONDS → Mimir에 _milliseconds_bucket으로 저장됨.
                // 대시보드 쿼리가 _seconds_bucket을 사용하므로 seconds로 명시.
                "otlp.baseTimeUnit" -> "seconds"
                "otlp.resourceAttributes" -> "service.name=devquest-api"
                else -> null
            }
            override fun headers(): Map<String, String> =
                mapOf(
                    "Authorization" to "Basic $encoded",
                    // keep-alive stale connection 방지: Java HttpURLConnection이 idle connection을
                    // 재사용하다 "Unexpected end of file from server" 오류 발생 → Connection: close로 강제 종료
                    "Connection" to "close",
                )
        }
        val threadFactory = ThreadFactory { runnable ->
            Executors.defaultThreadFactory().newThread(runnable).apply {
                name = "otlp-metrics-exporter"
                isDaemon = true
            }
        }
        // HttpURLConnection keep-alive 비활성화: 60초 이상 유휴 상태인 connection을
        // Grafana Cloud 서버가 닫은 후 재사용 시 "Unexpected end of file from server" 오류 발생.
        // OTLP push는 60초 간격이므로 keep-alive 이점보다 stale connection 위험이 큼.
        System.setProperty("http.keepAlive", "false")

        // 3-arg 생성자 사용: 내부 private 4-arg 생성자가 threadFactory로 start() 를 1회 호출.
        // 기존: OtlpMeterRegistry(config, clock) → DEFAULT_THREAD_FACTORY로 start() 자동 호출
        //       + created.start(threadFactory) 명시 호출 → 총 2회 → "Publishing metrics" 2줄 로그.
        // publish() 오버라이드: 60초 push 성공 시 INFO 로그. 실패 시 Micrometer 내부 WARN 로그 유지.
        val created = object : OtlpMeterRegistry(config, clock, threadFactory) {
            override fun publish() {
                super.publish()
                log.info("OTLP metrics pushed to Grafana Cloud successfully")
            }
        }
        registry = created
        log.info("OtlpMeterRegistry started — pushing to Grafana Cloud every 60s")
        return created
    }

    @PreDestroy
    fun stopRegistry() {
        runCatching { registry?.stop() }
            .onSuccess { log.info("OtlpMeterRegistry stopped") }
            .onFailure { log.warn("OtlpMeterRegistry stop failed", it) }
    }
}
