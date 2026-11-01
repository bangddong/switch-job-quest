package com.devquest.monitoring

import io.micrometer.registry.otlp.OtlpConfig
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Grafana Cloud OTLP(Mimir) 전송용 [OtlpConfig]. [OtlpMetricsConfig] 에서 분리해 레지스트리를 만들지 않고도
 * 단위 테스트할 수 있게 했다.
 *
 * 리소스 속성(`service.name`, `service.instance.id`)은 문자열 프로퍼티 `otlp.resourceAttributes` 로 주지 않고
 * [resourceAttributes] 를 직접 override 해 Map 으로 돌려준다. Micrometer 1.16.3 의 기본 파싱 경로는 빈 값(`key=`)을
 * 그대로 통과시키고, `Collectors.toMap` 이라 키가 중복되면 기동이 실패한다.
 *
 * Grafana Cloud OTLP 는 `service.name`→`job`, `service.instance.id`→`instance` 만 라벨로 올린다. 그 외 리소스 속성
 * (플랫폼 등)은 라벨이 되지 않으므로 플랫폼 구분은 Micrometer 공통 태그 `platform` 으로 한다(`monitoring.yml`).
 */
class GrafanaOtlpConfig(
    instanceId: String,
    apiKey: String,
    serviceName: String,
    serviceInstanceId: String,
) : OtlpConfig {

    private val resolvedServiceName = serviceName.trim().ifEmpty { DEFAULT_SERVICE_NAME }
    private val resolvedServiceInstanceId = serviceInstanceId.trim().ifEmpty { UNKNOWN_INSTANCE_ID }
    private val authorization = "Basic " + Base64.getEncoder().encodeToString("$instanceId:$apiKey".toByteArray())

    override fun get(key: String): String? = when (key) {
        "otlp.url" -> "https://otlp-gateway-prod-ap-northeast-0.grafana.net/otlp/v1/metrics"
        "otlp.step" -> "PT60S"
        // baseTimeUnit 기본값은 MILLISECONDS → Mimir에 _milliseconds_bucket으로 저장됨.
        // 대시보드 쿼리가 _seconds_bucket을 사용하므로 seconds로 명시.
        "otlp.baseTimeUnit" -> TimeUnit.SECONDS.name.lowercase()
        // "otlp.resourceAttributes" 는 일부러 null — resourceAttributes() 를 직접 override 한다.
        else -> null
    }

    override fun resourceAttributes(): Map<String, String> = mapOf(
        "service.name" to resolvedServiceName,
        "service.instance.id" to resolvedServiceInstanceId,
    )

    override fun headers(): Map<String, String> =
        mapOf(
            "Authorization" to authorization,
            // keep-alive stale connection 방지: Java HttpURLConnection이 idle connection을
            // 재사용하다 "Unexpected end of file from server" 오류 발생 → Connection: close로 강제 종료
            "Connection" to "close",
        )

    companion object {
        const val DEFAULT_SERVICE_NAME = "devquest-api"
        const val UNKNOWN_INSTANCE_ID = "unknown"

        /**
         * 인스턴스 식별자: Fly 머신 ID → 파드 호스트명(K8s `HOSTNAME`) → `unknown`.
         * 환경변수가 "설정됐지만 빈 값"인 경우도 없는 것으로 본다(빈 속성 값을 Grafana 로 보내지 않는다).
         */
        fun resolveInstanceId(flyMachineId: String?, hostname: String?): String =
            flyMachineId?.trim()?.takeIf { it.isNotEmpty() }
                ?: hostname?.trim()?.takeIf { it.isNotEmpty() }
                ?: UNKNOWN_INSTANCE_ID
    }
}
