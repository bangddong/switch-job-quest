package com.devquest.ai

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 서비스 분해 에픽 — Phase 0 Task 0.3에서 빈 스캐폴드로 시작, Phase 1 Task 1.1에서
 * `client-ai` 평가자(`com.devquest.client.ai.*`)를 붙였다.
 *
 * `scanBasePackages`를 `com.devquest`로 넓힌 이유: 기본 컴포넌트 스캔은 이 클래스가 속한
 * `com.devquest.ai` 패키지로 한정되는데, client-ai의 평가자·`AiCallExecutor`·
 * `CacheMetricsAdvisor` 등은 `com.devquest.client.ai` 패키지에 있어 그대로 두면 빈으로
 * 잡히지 않는다. core-api의 `DevQuestApplication`과 동일한 패턴.
 */
@SpringBootApplication(scanBasePackages = ["com.devquest"])
class AiApiApplication

fun main(args: Array<String>) {
    runApplication<AiApiApplication>(*args)
}
