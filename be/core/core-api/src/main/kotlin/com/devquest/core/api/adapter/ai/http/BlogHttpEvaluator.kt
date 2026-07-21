package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.port.BlogEvaluatorPort

/**
 * `BlogEvaluatorPort`의 HTTP 어댑터 뼈대.
 *
 * 서비스 분해 에픽 Phase 0 Task 0.4 — `devquest.ai.transport` 전환 스위치 메커니즘만 확립한다.
 * `devquest.ai.transport=http`일 때만 [com.devquest.core.api.config.AiTransportConfig]가 이 클래스를
 * 빈으로 등록한다(기본값 `inprocess`에서는 등록조차 되지 않음).
 *
 * 실제 ai-api RestClient 호출 배선은 Phase 1 Task 1.4에서 이뤄진다. 그 전까지는 호출될 일이 없다.
 */
class BlogHttpEvaluator : BlogEvaluatorPort {
    override fun evaluate(techTopic: String, title: String, content: String): AiEvaluationResult {
        TODO("Phase 1 Task 1.4에서 ai-api RestClient 호출로 구현 예정")
    }
}
