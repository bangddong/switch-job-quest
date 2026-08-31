package com.devquest.ai.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * ai-api `/health` — core-api `HealthController`(com.devquest.core.api.controller),
 * daily-api `HealthController`(com.devquest.daily.controller)와 같은 역할이되 응답 봉투가 없다.
 *
 * ai-api는 `ApiResponse`(core-api 소유 — `build.gradle.kts`에 core-api 의존이 없다)도
 * daily-api의 `DailyApiResponse`도 쓸 수 없다. 대신 이 모듈의 기존 컨트롤러 관례
 * (`AiApiTechInterviewController` 등 — 도메인 모델/원시 타입을 봉투 없이 직접 반환)를 그대로 따른다.
 * 새 봉투 클래스를 만들지 않는다 — ai-api 전체에 그런 개념이 없다.
 *
 * 🔴 이 엔드포인트는 **의도적으로 상수만 반환한다** — 외부 의존성 상태는 절대 반영하지 않는다
 * (원장 L-15, PR #374와 동일 계약). k8s의 startupProbe·livenessProbe가 이 경로를 찌르는데,
 * liveness가 실제 의존성 상태에 따라 실패하면 그 의존성이 죽을 때마다 컨테이너가 재시작되고
 * (재시작해도 원인이 안 고쳐지므로) 무한 재시작 루프에 빠져 장애를 증폭시킨다. 실제 의존성 검증은
 * `/actuator/health/readiness`가 담당한다 — 단, ai-api는 DB 등 검사할 외부 의존이 없어
 * readiness 그룹을 `ping`만으로 구성했다(근거는 `application.yml` 주석 참고). readiness 실패는
 * 재시작이 아니라 해당 파드를 Service 엔드포인트에서만 제외하므로 두 프로브의 실패 대응이 다르다.
 *
 * 반환 타입을 순수 `String`으로 두면 `StringHttpMessageConverter`가 먼저 선택되어 따옴표 없는
 * `text/plain` 바디가 나간다(이 모듈의 기존 계약 — `AiApiTechInterviewController`의
 * `TechInterviewWireFormatContractTest`로 실측 확인됨). 프로브는 상태 코드만 보므로 무해하지만,
 * 헤더가 실제 바디 형식과 다른 것을 말하지 않도록 `text/plain`을 명시한다.
 */
@RestController
class HealthController {

    @GetMapping("/health", produces = ["text/plain;charset=UTF-8"])
    fun health(): String = "ai-api is running"
}
