package com.devquest.daily.config

import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

/**
 * ⑥ B8-18 — daily-api 가 `support:monitoring` 을 가져온 뒤의 **실제 앱 컨텍스트** 배선을 고정한다.
 *
 * 🔴 import 우선순위 함정의 실증: `spring.config.import` 로 가져온 `monitoring.yml` 의 값은 앱 `application.yml` 값을
 * 이긴다. monitoring.yml 이 `management.endpoints.web.exposure.include: health,prometheus` 를 리터럴로 갖고 있으면
 * daily-api 가 `health` 만 노출하려고 정의한 값이 조용히 덮여 `/actuator/prometheus` 가 열린다. service-name 도 같다.
 * 그래서 앱 값이 반영되는지를 Environment 와 실제 HTTP 응답 둘 다로 본다.
 *
 * `DEVQUEST_PLATFORM=eks` 는 환경변수와 같은 이름의 프로퍼티다(Spring 이 이름으로 해석). 컨텍스트 캐시 키도 분리한다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = ["DEVQUEST_PLATFORM=eks"],
)
@AutoConfigureMockMvc
class ObservabilityWiringTest {

    @Autowired
    private lateinit var environment: Environment

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `service-name 은 앱이 정의한 devquest-daily-api 이고 monitoring yml 이 덮어쓰지 않는다`() {
        assertThat(environment.getProperty("devquest.observability.service-name")).isEqualTo("devquest-daily-api")
        assertThat(environment.getProperty("management.metrics.tags.application")).isEqualTo("devquest-daily-api")
    }

    @Test
    fun `exposure 는 앱이 정의한 health 뿐이고 monitoring yml 이 prometheus 를 추가하지 않는다`() {
        assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health")
    }

    @Test
    fun `actuator prometheus 는 daily-api 에서 열리지 않는다`() {
        // DailyApiControllerAdvice 의 포괄 핸들러가 미매핑 경로를 500 으로 바꾸므로 404 가 아니라 "200 이 아님"으로 단언한다.
        // 노출 설정 자체는 위 exposure 테스트가 고정한다.
        val response = mockMvc.perform(get("/actuator/prometheus")).andReturn().response

        assertThat(response.status).isNotEqualTo(200)
        assertThat(response.contentAsString).doesNotContain("jvm_memory")
    }

    @Test
    fun `모든 시계열에 application 과 platform 공통 태그가 붙는다`() {
        val id = meterRegistry.counter("devquest.observability.probe").id

        assertThat(id.getTag("application")).isEqualTo("devquest-daily-api")
        assertThat(id.getTag("platform")).isEqualTo("eks")
    }

    @Test
    fun `Prometheus 레지스트리는 만들어지지 않는다 - 읽는 곳 없이 메모리만 쓰지 않는다`() {
        val registryTypes = context.getBeansOfType(MeterRegistry::class.java).values.map { it.javaClass.simpleName }

        assertThat(registryTypes).noneMatch { it.contains("Prometheus") }
    }

    @Test
    fun `오토컨피그 OTLP 는 꺼져 있다 - 로컬과 테스트에서 localhost 4318 로 push 하지 않는다`() {
        // 현재 클래스패스(Boot 4.0.3, spring-boot-opentelemetry 없음)에서는 오토컨피그가 조건부로 비활성이라 레지스트리 부재만으로는
        // 이 가드를 검증하지 못한다(실측: enabled=true 로 바꿔도 빈이 생기지 않는다). 그래서 설정값 자체를 고정한다 -
        // 나중에 의존이 추가돼 오토컨피그가 켜져도 push 가 시작되지 않게 하는 방어선이다.
        assertThat(environment.getProperty("management.otlp.metrics.export.enabled")).isEqualTo("false")
        assertThat(context.getBeansOfType(MeterRegistry::class.java).values.map { it.javaClass.simpleName })
            .noneMatch { it.contains("Otlp") }
    }
}
