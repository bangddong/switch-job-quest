package com.devquest.monitoring

import io.micrometer.core.instrument.Clock
import io.micrometer.registry.otlp.OtlpConfig
import io.micrometer.registry.otlp.OtlpMeterRegistry
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.core.type.AnnotatedTypeMetadata
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * `grafana.otlp.enabled=true`이더라도 실제 전송에 필요한 자격 증명
 * (`GRAFANA_API_KEY`, `grafana.otlp.instance-id`)이 비어 있으면 [OtlpMeterRegistry] 빈을
 * 등록하지 않는다. "켜라고 했는가"(enabled 플래그)만 보고 "쓸 수 있는가"(키가 실제로 있는가)는
 * 확인하지 않던 기존 가드의 결함을 보완한다.
 *
 * 2026-07-30 EKS Stage 3a 인시던트: `GRAFANA_API_KEY` 미설정 상태에서 `@Value("\${GRAFANA_API_KEY}")`
 * (기본값 없음)가 `PlaceholderResolutionException`을 던져 컨텍스트 초기화 자체가 실패 →
 * CrashLoopBackOff. 값이 없어도 부팅은 성공해야 하므로, 자격 증명 부재를 "빈 등록 여부"로만
 * 다루고 WARN 로그로 원인을 남긴다(값 자체는 로그에 남기지 않는다).
 */
class GrafanaOtlpCredentialsCondition : Condition {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        val environment = context.environment
        val apiKey = environment?.getProperty("GRAFANA_API_KEY")
        val instanceId = environment?.getProperty("grafana.otlp.instance-id")

        if (apiKey.isNullOrBlank() || instanceId.isNullOrBlank()) {
            log.warn(
                "GRAFANA_API_KEY 또는 grafana.otlp.instance-id가 설정되지 않아 " +
                    "OTLP 메트릭 전송(OtlpMeterRegistry)을 건너뜁니다.",
            )
            return false
        }
        return true
    }
}

@Configuration
@ConditionalOnProperty("grafana.otlp.enabled", havingValue = "true")
class OtlpMetricsConfig(
    @Value("\${grafana.otlp.instance-id:}") private val instanceId: String,
    @Value("\${GRAFANA_API_KEY:}") private val apiKey: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    @Volatile
    private var registry: OtlpMeterRegistry? = null

    @Bean
    @Conditional(GrafanaOtlpCredentialsCondition::class)
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
