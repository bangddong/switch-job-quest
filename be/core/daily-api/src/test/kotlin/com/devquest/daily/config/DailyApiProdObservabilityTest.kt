package com.devquest.daily.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource

/**
 * ⑥ B8-18 / QA F-1 회귀 가드 - daily-api `application-prod.yml` 의 송출 배선을 고정한다.
 *
 * `grafana.otlp.enabled` 와 `instance-id` 는 prod 프로필에만 있다. 기본(local) 프로필로 도는 [ObservabilityWiringTest] 는
 * `OtlpMetricsConfig` 를 로드하지 못하므로 이 블록이 지워져도 CI 가 초록이다. 그러면
 * `GrafanaOtlpCredentialsCondition` 이 false 가 돼 daily-api 지표 송출이 **조용히** 멈춘다.
 * prod 프로필 전체 기동은 실제 Postgres 접속 정보를 요구하므로, [DailyApiProdDdlAutoTest] 와 같이
 * [YamlPropertySourceLoader] 로 실제 yml 을 읽어 값을 단언한다.
 */
class DailyApiProdObservabilityTest {

    private fun property(resource: Resource, key: String): Any? =
        YamlPropertySourceLoader().load("prod", resource).firstNotNullOfOrNull { it.getProperty(key) }

    private val dailyProd: Resource = ClassPathResource("application-prod.yml")

    // core-api 는 daily-api 의 클래스패스에 없다(의존 금지). Gradle 테스트의 작업 디렉터리는 모듈 디렉터리(be/core/daily-api)다.
    private val coreApiProd: Resource = FileSystemResource("../core-api/src/main/resources/application-prod.yml")

    @Test
    fun `prod 프로필은 Grafana OTLP 송출을 켠다`() {
        assertThat(property(dailyProd, "grafana.otlp.enabled")?.toString()).isEqualTo("true")
    }

    @Test
    fun `instance-id 는 비어 있지 않고 core-api prod 와 같은 값이다 - 같은 Grafana 스택으로 보낸다`() {
        assertThat(coreApiProd.exists()).describedAs("core-api application-prod.yml 위치: ${coreApiProd.filename}").isTrue()

        val daily = property(dailyProd, "grafana.otlp.instance-id")?.toString()
        val core = property(coreApiProd, "grafana.otlp.instance-id")?.toString()

        assertThat(daily).isNotBlank()
        assertThat(core).isNotBlank()
        assertThat(daily).isEqualTo(core)
    }

    @Test
    fun `daily-api 는 기동 시 Flyway 마이그레이션을 켜지 않는다 - 클래스패스가 다른 앱이 migrate 하면 V8 사고가 재현된다`() {
        val flag = property(dailyProd, "devquest.flyway.migrate-on-startup")

        assertThat(flag?.toString()).isNotEqualTo("true")
        // 키 자체가 없어야 한다(기본값 false 에 기댄다). 명시적 false 도 허용하지 않는 건 의도가 바뀌었음을 눈에 띄게 하기 위해서다.
        assertThat(flag).isNull()
    }
}
