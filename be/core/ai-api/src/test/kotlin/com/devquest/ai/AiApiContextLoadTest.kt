package com.devquest.ai

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * 서비스 분해 에픽 Phase 0 Task 0.3 — ai-api 모듈이 독립적으로 Spring 컨텍스트를
 * 로드할 수 있는지 검증한다. 컨트롤러가 없는 빈 스캐폴드 단계이므로 컨텍스트 로드
 * 성공 자체가 유일한 검증 대상이다.
 */
@SpringBootTest
class AiApiContextLoadTest {

    @Test
    fun `ai-api Spring 컨텍스트가 정상적으로 로드된다`() {
        // 컨텍스트 로드 중 예외가 없으면 성공 — 별도 assertion 불필요
    }
}
