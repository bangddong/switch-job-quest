package com.devquest.core.api.config

import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment
import org.springframework.test.context.ActiveProfiles

/**
 * ⑥ B8-18 — core-api 의 관측 설정 배선을 고정한다. daily-api `ObservabilityWiringTest` 의 짝이다.
 *
 * core-api 는 기존 시계열을 잇기 위해 service-name 이 `devquest-api` 로 그대로여야 하고(Grafana job·Loki app 라벨 연속),
 * `/actuator/prometheus` 노출은 유지한다(운영 레시피 `fly ssh … wget localhost:8080/actuator/prometheus`). 노출 설정은
 * monitoring.yml 에서 core-api application.yml 로 옮겼다 - monitoring.yml 값이 앱 값을 이기는 import 우선순위 때문이다.
 * 실제 HTTP 노출은 ForwardHeadersStrategyTest(`/actuator/prometheus` 200)가 고정한다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        // 다른 풀 컨텍스트 테스트와 캐시 키를 분리한다.
        "spring.application.name=devquest-observability-wiring-test",
        "DEVQUEST_PLATFORM=fly",
    ],
)
@ActiveProfiles("test")
class ObservabilityWiringTest {

    @Autowired
    private lateinit var environment: Environment

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Test
    fun `service-name 은 기존 시계열 연속을 위해 devquest-api 이다`() {
        assertThat(environment.getProperty("devquest.observability.service-name")).isEqualTo("devquest-api")
        assertThat(environment.getProperty("management.metrics.tags.application")).isEqualTo("devquest-api")
    }

    @Test
    fun `exposure 는 health 와 prometheus 이다 - core-api application yml 이 정의한다`() {
        assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health,prometheus")
    }

    @Test
    fun `모든 시계열에 application 과 platform 공통 태그가 붙는다`() {
        val id = meterRegistry.counter("devquest.observability.probe").id

        assertThat(id.getTag("application")).isEqualTo("devquest-api")
        assertThat(id.getTag("platform")).isEqualTo("fly")
    }

    @Test
    fun `오토컨피그 OTLP 차단은 프로필과 무관하게 걸려 있다 - prod 프로필 블록에서 monitoring yml 로 옮겼다`() {
        // 수동 OtlpMetricsConfig 만 Grafana 로 보낸다. 오토컨피그가 같이 켜지면 429(이력). test 프로필에서도 false 여야
        // monitoring.yml 공통 경로가 살아 있다는 뜻이다(prod 전용 블록이었다면 여기서 null).
        assertThat(environment.getProperty("management.otlp.metrics.export.enabled")).isEqualTo("false")
    }
}
