package com.devquest.core.domain.port.ai

/**
 * AI(LLM) 컴퓨트 포트 마커 인터페이스.
 *
 * 서비스 분해 에픽(3서비스: core/ai-service/daily) Phase 0 Task 0.1 — 향후 ai-service로
 * 분리될 포트를 타입으로 식별하기 위한 무행동(no-op) 마커. 메서드를 선언하지 않으며,
 * 순수 Kotlin 인터페이스로 Spring 등 프레임워크 의존이 없다.
 *
 * `com.devquest.core.domain.port` 패키지의 AI 평가/생성 포트(`*EvaluatorPort`, 그 외 LLM 호출
 * 포트)가 이 인터페이스를 상속한다. DB 영속성 포트와 `Judge0Port`(비-LLM 외부 코드채점 어댑터)는
 * 상속하지 않는다.
 */
interface AiEvaluatorPort
