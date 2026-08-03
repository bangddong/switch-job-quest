package com.devquest.monitoring

import io.micrometer.core.instrument.Clock
import io.micrometer.registry.otlp.OtlpMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 2026-07-30 EKS Stage 3a 배포 인시던트 재현/회귀 테스트.
 *
 * [ApplicationContextRunner]는 실제 컨텍스트 refresh 경로를 그대로 태워서
 * `@ConditionalOnProperty` 평가 → `@Configuration` 클래스 인스턴스화(생성자 `@Value` 주입) →
 * `@Bean` 팩토리 메서드 호출까지 우회 없이 검증한다. `grafana.otlp.enabled=true`인데
 * `GRAFANA_API_KEY`가 없으면 생성자 주입에서 `PlaceholderResolutionException`이 발생해
 * 컨텍스트 refresh 자체가 실패한다 — 이 테스트는 그 실제 실패 경로를 재현한다.
 */
class OtlpMetricsConfigTest {

    @Configuration
    class ClockConfig {
        @Bean
        fun clock(): Clock = Clock.SYSTEM
    }

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration::class.java))
        .withUserConfiguration(ClockConfig::class.java, OtlpMetricsConfig::class.java)

    @Test
    fun `enabled=true이고 GRAFANA_API_KEY가 없으면 컨텍스트는 정상 기동되고 OtlpMeterRegistry 빈은 생성되지 않는다`() {
        contextRunner
            .withPropertyValues(
                "grafana.otlp.enabled=true",
                "grafana.otlp.instance-id=1680166",
            )
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(OtlpMeterRegistry::class.java)
            }
    }

    @Test
    fun `enabled=true이고 GRAFANA_API_KEY가 빈 문자열이면 OtlpMeterRegistry 빈은 생성되지 않는다`() {
        contextRunner
            .withPropertyValues(
                "grafana.otlp.enabled=true",
                "grafana.otlp.instance-id=1680166",
                "GRAFANA_API_KEY=",
            )
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(OtlpMeterRegistry::class.java)
            }
    }

    @Test
    fun `enabled=true이고 GRAFANA_API_KEY가 있으면 OtlpMeterRegistry 빈이 정상적으로 생성된다`() {
        contextRunner
            .withPropertyValues(
                "grafana.otlp.enabled=true",
                "grafana.otlp.instance-id=1680166",
                "GRAFANA_API_KEY=dummy-key",
            )
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(OtlpMeterRegistry::class.java)
            }
    }

    @Test
    fun `grafana otlp enabled가 없으면 OtlpMetricsConfig 자체가 등록되지 않는다`() {
        contextRunner
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(OtlpMetricsConfig::class.java)
                assertThat(context).doesNotHaveBean(OtlpMeterRegistry::class.java)
            }
    }
}
