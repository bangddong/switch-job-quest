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
                "otlp.resourceAttributes" -> "service.name=devquest-api"
                else -> null
            }
            override fun headers(): Map<String, String> =
                mapOf("Authorization" to "Basic $encoded")
        }
        val threadFactory = ThreadFactory { runnable ->
            Executors.defaultThreadFactory().newThread(runnable).apply {
                name = "otlp-metrics-exporter"
                isDaemon = true
            }
        }
        registry = OtlpMeterRegistry(config, clock).also {
            it.start(threadFactory)
            log.info("OtlpMeterRegistry started — pushing to Grafana Cloud every 60s")
        }
        return registry!!
    }

    @PreDestroy
    fun stopRegistry() {
        runCatching { registry?.stop() }
            .onSuccess { log.info("OtlpMeterRegistry stopped") }
            .onFailure { log.warn("OtlpMeterRegistry stop failed", it) }
    }
}
