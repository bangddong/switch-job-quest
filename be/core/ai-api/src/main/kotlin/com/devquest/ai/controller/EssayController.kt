package com.devquest.ai.controller

import com.devquest.ai.controller.request.EssayEvaluateRequest
import com.devquest.core.domain.model.evaluation.EssayCheckResult
import com.devquest.core.domain.port.EssayEvaluatorPort
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ai/essay")
class EssayController(
    private val essayEvaluatorPort: EssayEvaluatorPort,
) {

    @PostMapping("/evaluate")
    fun evaluate(@RequestBody request: EssayEvaluateRequest): EssayCheckResult =
        essayEvaluatorPort.evaluate(request.dissatisfactions, request.goals, request.fiveYearVision)
}
