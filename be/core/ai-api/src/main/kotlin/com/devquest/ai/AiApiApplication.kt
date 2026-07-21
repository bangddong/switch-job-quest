package com.devquest.ai

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 서비스 분해 에픽 Phase 0 Task 0.3 — 향후 ai-service로 분리될 빈 스캐폴드 앱.
 *
 * 이 시점엔 컨트롤러가 없어 아무도 호출하지 않는다. 독립 기동(컨텍스트 로드)만
 * 가능하면 되며, 기존 core-api 앱과 배포 산출물에는 영향을 주지 않는다.
 */
@SpringBootApplication
class AiApiApplication

fun main(args: Array<String>) {
    runApplication<AiApiApplication>(*args)
}
