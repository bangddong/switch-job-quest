package com.devquest.ai.controller

import com.devquest.ai.controller.request.SystemDesignEvaluateRequest
import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.port.SystemDesignEvaluatorPort
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ai/system-design")
class SystemDesignController(
    private val systemDesignEvaluatorPort: SystemDesignEvaluatorPort,
) {

    @PostMapping("/evaluate")
    fun evaluate(@RequestBody request: SystemDesignEvaluateRequest): AiEvaluationResult =
        systemDesignEvaluatorPort.evaluate(
            request.problemStatement,
            request.architectureDescription,
            request.considerations,
        )
}
