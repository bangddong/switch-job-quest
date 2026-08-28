package com.devquest.daily.controller

import com.devquest.daily.controller.response.DailyApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * daily-api `/health` — core-api `HealthController`(com.devquest.core.api.controller)와 같은 역할이되
 * `ApiResponse` 대신 daily-api 자체 봉투 [DailyApiResponse]를 쓴다(D-007, core-api 의존 금지).
 *
 * 🔴 이 엔드포인트는 **의도적으로 상수만 반환한다** — DB 등 실제 의존성 상태는 절대 반영하지 않는다.
 * k8s의 livenessProbe가 이 경로를 찌르는데, liveness가 DB 상태에 따라 실패하면 DB 장애 시 모든 파드가
 * 동시에 재시작되어 장애를 증폭시킨다(원장 L-15, PR #374). 실제 의존성 검증은
 * `/actuator/health/readiness`(readinessProbe 전용, `application.yml`의
 * `management.endpoint.health.group.readiness.include: db,ping` 설정으로 구성)가 담당한다 —
 * readiness 실패는 재시작이 아니라 해당 파드를 Service 엔드포인트에서만 제외한다.
 */
@RestController
class HealthController {

    @GetMapping("/health")
    fun health(): DailyApiResponse<String> {
        return DailyApiResponse.success("Daily API is running")
    }
}
