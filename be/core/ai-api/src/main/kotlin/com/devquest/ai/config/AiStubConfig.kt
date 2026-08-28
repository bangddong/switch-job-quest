package com.devquest.ai.config

import com.devquest.ai.stub.TechInterviewStubEvaluator
import com.devquest.core.domain.port.TechInterviewPort
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * D-008 — 학습 클러스터 전용 AI 스텁 전환 스위치.
 *
 * core-api의 `AiTransportConfig`(inprocess ↔ http 전송 계층 전환)와 **정확히 같은 형태**다:
 * `@ConditionalOnProperty` + `@Primary`로 `client-ai`의 `@Component` 구현(`TechInterviewEvaluator`)을
 * 조건부로 덮어쓴다.
 *
 * - 기본값(프로퍼티 미설정, off): 이 클래스는 어떤 빈도 만들지 않는다 → `client-ai`의
 *   `TechInterviewEvaluator`(ai-api가 `implementation(project(":clients:client-ai"))`로 이미 의존)가
 *   `TechInterviewPort`의 유일한 구현으로 주입된다. **prod에 스텁이 조용히 켜지는 일이 없다** —
 *   "잊으면 터진다"가 아니라 "잊으면 안 돈다" 형태(열린 결정 ①과 동일 원칙).
 * - `devquest.ai.stub.tech-interview.enabled=true`: 이 빈이 `@Primary`로 등록돼
 *   `TechInterviewStubEvaluator`가 주입된다.
 *
 * 🔴 B-2b 함정 재확인: `@ConditionalOnProperty` 게이트가 닫혔을 때 폴백이 없으면 기동 실패로
 * 이어진다(daily-api가 실제로 겪은 문제). 여기서는 ai-api가 `client-ai`를 항상 의존하므로
 * `client-ai`의 `@Component` 구현이 게이트가 닫혀 있어도 항상 폴백으로 존재해 안전하다.
 *
 * `TechInterviewPort` 하나만 스텁한다 — 나머지 17개 `AiEvaluatorPort` + `Judge0Port`는 스텁 대상이
 * 아니며 여전히 실제 `ANTHROPIC_API_KEY`가 필요하다.
 */
@Configuration
class AiStubConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai.stub.tech-interview", name = ["enabled"], havingValue = "true")
    fun techInterviewStubEvaluator(): TechInterviewPort = TechInterviewStubEvaluator()
}
