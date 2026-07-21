package com.devquest.core.api.config

import com.devquest.core.api.adapter.ai.http.BlogHttpEvaluator
import com.devquest.core.domain.port.BlogEvaluatorPort
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * AI 포트 전송 계층(in-process ↔ HTTP) 전환 스위치.
 *
 * `devquest.ai.transport` 프로퍼티로 AI 평가 포트의 구현체를 선택한다.
 *
 * - 기본값(`inprocess`, 미설정 포함): 이 설정 클래스는 어떤 빈도 만들지 않는다 → `client-ai`의
 *   `@Component` 평가자(예: `TechBlogEvaluator`)가 각 포트의 유일한 구현으로 주입된다.
 *   → 런타임 동작이 완전히 불변이다(Phase 0 무행동 원칙).
 * - `http`: 이 설정 클래스가 HTTP 어댑터 빈을 등록하고 `@Primary`를 부여해 client-ai 빈 대신
 *   주입되게 한다. client-ai 빈은 컨텍스트에 여전히 존재하지만(롤백을 위해) 사용되지는 않는다.
 *
 * `client-ai` 모듈과 소비 서비스(`AiCheckService` 등)는 이 클래스의 존재를 모른다 — 둘 다 포트
 * 인터페이스만 주입받으므로, 전송 계층 전환이 이 설정 파일 안에 국한된다(무수정 보장).
 *
 * 실제 ai-api HTTP 호출 배선은 Phase 1 Task 1.4. 지금은 대표 포트(`BlogEvaluatorPort`)로 전환
 * 메커니즘만 증명한다 — 나머지 16개 AI 포트의 HTTP 어댑터는 Phase 1에서 실제 배선과 함께 추가한다.
 */
@Configuration
class AiTransportConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun blogHttpEvaluator(): BlogEvaluatorPort = BlogHttpEvaluator()
}
