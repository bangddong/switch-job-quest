package com.devquest.monitoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * `monitoring.yml` 의 계약 고정. 파일을 실제 Spring Boot ConfigData 로더로 읽는다(문자열 grep 아님).
 *
 * 🔴 import 우선순위 함정: 앱이 `spring.config.import` 로 가져온 이 파일의 값은 **앱 application.yml 값을 이긴다.**
 * 그래서 앱마다 달라야 하는 값(service-name, exposure)은 리터럴로 두면 안 되고, 앱이 정의한
 * `devquest.observability.service-name` 을 가리키는 간접 참조여야 한다. 이 테스트는 그 형태를 고정한다.
 * (앱 컨텍스트에서의 실증은 daily-api `ObservabilityWiringTest` 가 한다.)
 */
class MonitoringYmlTest {

    private val runner = ApplicationContextRunner()
        .withInitializer(ConfigDataApplicationContextInitializer())
        .withPropertyValues("spring.config.name=monitoring")

    @Test
    fun `application 공통 태그는 devquest observability service-name 을 따른다`() {
        runner.withPropertyValues("devquest.observability.service-name=devquest-daily-api").run { context ->
            assertThat(context.environment.getProperty("management.metrics.tags.application"))
                .isEqualTo("devquest-daily-api")
        }
    }

    @Test
    fun `service-name 이 정의되지 않으면 application 태그는 devquest-api - 기존 시계열 연속`() {
        runner.run { context ->
            assertThat(context.environment.getProperty("management.metrics.tags.application"))
                .isEqualTo("devquest-api")
        }
    }

    @Test
    fun `platform 공통 태그는 DEVQUEST_PLATFORM 을 따르고 없으면 unknown`() {
        runner.run { context ->
            assertThat(context.environment.getProperty("management.metrics.tags.platform")).isEqualTo("unknown")
        }
        runner.withPropertyValues("DEVQUEST_PLATFORM=eks").run { context ->
            assertThat(context.environment.getProperty("management.metrics.tags.platform")).isEqualTo("eks")
        }
    }

    @Test
    fun `앱마다 달라야 하는 exposure 를 monitoring yml 이 정의하지 않는다`() {
        runner.run { context ->
            assertThat(context.environment.getProperty("management.endpoints.web.exposure.include")).isNull()
        }
    }

    @Test
    fun `오토컨피그 OTLP 레지스트리는 공통으로 꺼져 있다 - 로컬과 테스트에서 localhost 4318 로 push 하지 않는다`() {
        runner.run { context ->
            assertThat(context.environment.getProperty("management.otlp.metrics.export.enabled")).isEqualTo("false")
        }
    }
}
