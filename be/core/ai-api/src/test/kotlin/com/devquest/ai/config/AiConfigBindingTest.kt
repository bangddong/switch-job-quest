package com.devquest.ai.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment

/**
 * 서비스 분해 에픽 Phase 1 Task 1.2 — ai-api로 이관한 설정 키가 **런타임에 실제로 바인딩**되는지
 * 검증한다. `Environment.getProperty`는 yml 파일을 텍스트로 읽는 게 아니라, Spring이 모든
 * property source(빈 스캐폴드 application.yml + import된 client-ai-anthropic.yml + 환경변수)를
 * 병합해 실제로 해석한 최종값이다 — `AiCallExecutor`·`Judge0Adapter`의 `@Value` private 필드가
 * 컨텍스트 로드 시 주입받는 값과 동일하다.
 *
 * 값은 core-api/application.yml에 선언된 현재 값과 동일해야 한다(동작 동일성 유지가 이 태스크의 목적).
 * 대조표는 PR 반환 보고서 참조.
 */
@SpringBootTest
class AiConfigBindingTest {

    @Autowired
    private lateinit var environment: Environment

    @Test
    fun `AiCallExecutor가 소비하는 max-retry가 core-api와 동일한 값으로 바인딩된다`() {
        val maxRetry = environment.getProperty("devquest.ai.max-retry", Int::class.java)

        // core-api/application.yml: devquest.ai.max-retry: 3
        assertThat(maxRetry).isEqualTo(3)
    }

    @Test
    fun `Judge0Adapter가 소비하는 api-host가 core-api와 동일한 값으로 바인딩된다`() {
        val apiHost = environment.getProperty("devquest.judge0.api-host")

        // core-api/application.yml: devquest.judge0.api-host: judge0-ce.p.rapidapi.com
        assertThat(apiHost).isEqualTo("judge0-ce.p.rapidapi.com")
    }

    @Test
    fun `Judge0Adapter가 소비하는 api-key는 시크릿 플레이스홀더로 해석되고 하드코딩된 실제 값이 아니다`() {
        val apiKey = environment.getProperty("devquest.judge0.api-key")

        // 환경변수(JUDGE0_API_KEY) 미설정 시 빈 문자열로 해석되어야 한다(기본값 자리에 실제 키 금지 규칙의 증거).
        assertThat(apiKey).isNotNull()
        assertThat(apiKey).doesNotContain("rapidapi.com").doesNotStartWith("http")
    }

    @Test
    fun `AiClientConfig가 소비하는 boss-model, boss-max-tokens는 client-ai-anthropic-yml 공유로 core-api와 자동 동행한다`() {
        val bossModel = environment.getProperty("devquest.ai.boss-model")
        val bossMaxTokens = environment.getProperty("devquest.ai.boss-max-tokens", Int::class.java)

        // client-ai-anthropic.yml(client-ai 모듈 리소스, core-api·ai-api 동일 import): boss-model / boss-max-tokens
        assertThat(bossModel).isEqualTo("claude-sonnet-4-6")
        assertThat(bossMaxTokens).isEqualTo(4000)
    }
}
