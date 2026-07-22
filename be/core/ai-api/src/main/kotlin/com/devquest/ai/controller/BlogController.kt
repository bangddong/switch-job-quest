package com.devquest.ai.controller

import com.devquest.ai.controller.request.BlogEvaluateRequest
import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.port.BlogEvaluatorPort
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 서비스 분해 에픽 Phase 1 Task 1.1 — BlogEvaluatorPort를 HTTP로 노출.
 * 컨트롤러는 요청 DTO → 포트 호출 → core-domain 반환 data class 그대로 직렬화만 한다.
 */
@RestController
@RequestMapping("/internal/ai/blog")
class BlogController(
    private val blogEvaluatorPort: BlogEvaluatorPort,
) {

    @PostMapping("/evaluate")
    fun evaluate(@RequestBody request: BlogEvaluateRequest): AiEvaluationResult =
        blogEvaluatorPort.evaluate(request.techTopic, request.title, request.content)
}
