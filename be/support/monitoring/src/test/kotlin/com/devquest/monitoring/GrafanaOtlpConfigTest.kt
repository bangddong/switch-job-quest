package com.devquest.monitoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * OtlpConfig 를 **레지스트리를 만들지 않고** 단위 검증한다.
 *
 * 핵심은 `resourceAttributes()` 다. Micrometer 1.16.3 의 기본 구현은 문자열 `otlp.resourceAttributes` 를
 * `key=value,key=value` 로 파싱하는데, 빈 값(`key=`)을 그대로 통과시키고 `Collectors.toMap` 이라 키가 중복되면
 * 기동이 실패한다. 그래서 문자열 경로를 쓰지 않고 `resourceAttributes()` 를 직접 override 해 Map 을 돌려준다.
 */
class GrafanaOtlpConfigTest {

    private fun config(
        serviceName: String = "devquest-api",
        serviceInstanceId: String = "machine-1",
    ) = GrafanaOtlpConfig(
        instanceId = "1680166",
        apiKey = "dummy-key",
        serviceName = serviceName,
        serviceInstanceId = serviceInstanceId,
    )

    @Test
    fun `리소스 속성은 service name 과 service instance id 를 Map 으로 직접 돌려준다`() {
        val attributes = config(serviceName = "devquest-daily-api", serviceInstanceId = "daily-api-7c9d").resourceAttributes()

        assertThat(attributes).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "service.name" to "devquest-daily-api",
                "service.instance.id" to "daily-api-7c9d",
            ),
        )
    }

    @Test
    fun `리소스 속성을 문자열 프로퍼티 경로로 노출하지 않는다`() {
        // 문자열 경로(otlp.resourceAttributes)가 non-null 이면 Micrometer 가 그 파싱 경로를 탄다 - 빈 값 통과·키 중복 시 기동 실패.
        assertThat(config().get("otlp.resourceAttributes")).isNull()
    }

    @Test
    fun `service name 이 비어 있으면 기본값 devquest-api 로 대체한다`() {
        assertThat(config(serviceName = "").resourceAttributes()["service.name"]).isEqualTo("devquest-api")
        assertThat(config(serviceName = "   ").resourceAttributes()["service.name"]).isEqualTo("devquest-api")
    }

    @Test
    fun `service instance id 가 비어 있으면 unknown 으로 대체한다 - 빈 값 속성을 보내지 않는다`() {
        assertThat(config(serviceInstanceId = "").resourceAttributes()["service.instance.id"]).isEqualTo("unknown")
        assertThat(config(serviceInstanceId = " ").resourceAttributes()["service.instance.id"]).isEqualTo("unknown")
    }

    @Test
    fun `리소스 속성 값에는 빈 문자열이 하나도 없다`() {
        val attributes = config(serviceName = "", serviceInstanceId = "").resourceAttributes()

        assertThat(attributes.values).noneMatch { it.isBlank() }
    }

    @Test
    fun `instance id 는 FLY_MACHINE_ID 를 우선하고 없으면 HOSTNAME 그것도 없으면 unknown`() {
        assertThat(GrafanaOtlpConfig.resolveInstanceId(flyMachineId = "e148e", hostname = "pod-abc")).isEqualTo("e148e")
        assertThat(GrafanaOtlpConfig.resolveInstanceId(flyMachineId = "", hostname = "pod-abc")).isEqualTo("pod-abc")
        assertThat(GrafanaOtlpConfig.resolveInstanceId(flyMachineId = null, hostname = "pod-abc")).isEqualTo("pod-abc")
        assertThat(GrafanaOtlpConfig.resolveInstanceId(flyMachineId = " ", hostname = "")).isEqualTo("unknown")
        assertThat(GrafanaOtlpConfig.resolveInstanceId(flyMachineId = null, hostname = null)).isEqualTo("unknown")
    }

    @Test
    fun `전송 설정은 기존 계약을 유지한다 - url step baseTimeUnit`() {
        val config = config()

        assertThat(config.url()).isEqualTo("https://otlp-gateway-prod-ap-northeast-0.grafana.net/otlp/v1/metrics")
        assertThat(config.step()).isEqualTo(Duration.ofSeconds(60))
        // 대시보드 쿼리가 _seconds_bucket 을 쓴다 - 기본값(MILLISECONDS)으로 돌아가면 시계열 이름이 바뀐다.
        assertThat(config.baseTimeUnit()).isEqualTo(TimeUnit.SECONDS)
    }

    @Test
    fun `헤더는 Basic 인증과 Connection close 를 보낸다`() {
        val headers = config().headers()

        val expected = java.util.Base64.getEncoder().encodeToString("1680166:dummy-key".toByteArray())
        assertThat(headers["Authorization"]).isEqualTo("Basic $expected")
        assertThat(headers["Connection"]).isEqualTo("close")
    }
}

/** [OtlpMetricsConfig] 가 주입받은 값으로 설정을 어떻게 조립하는지 - 레지스트리는 만들지 않는다. */
class OtlpMetricsConfigAssemblyTest {

    private fun assemble(serviceName: String, flyMachineId: String, hostname: String) =
        OtlpMetricsConfig(
            instanceId = "1680166",
            apiKey = "dummy-key",
            serviceName = serviceName,
            flyMachineId = flyMachineId,
            hostname = hostname,
        ).grafanaOtlpConfig().resourceAttributes()

    @Test
    fun `Fly 에서는 FLY_MACHINE_ID 가 instance 가 되고 서비스 이름은 앱 값을 따른다`() {
        assertThat(assemble("devquest-api", flyMachineId = "e148e0", hostname = "ignored"))
            .containsEntry("service.name", "devquest-api")
            .containsEntry("service.instance.id", "e148e0")
    }

    @Test
    fun `EKS 에서는 FLY_MACHINE_ID 가 없으므로 파드 HOSTNAME 이 instance 가 된다`() {
        assertThat(assemble("devquest-daily-api", flyMachineId = "", hostname = "daily-api-6f7d9c-x2k4q"))
            .containsEntry("service.name", "devquest-daily-api")
            .containsEntry("service.instance.id", "daily-api-6f7d9c-x2k4q")
    }
}
