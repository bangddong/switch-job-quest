package com.devquest.ai.controller

import com.devquest.ai.controller.request.DeveloperClassEvaluateRequest
import com.devquest.core.domain.model.evaluation.DeveloperClassResult
import com.devquest.core.domain.port.DeveloperClassEvaluatorPort
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ai/developer-class")
class DeveloperClassController(
    private val developerClassEvaluatorPort: DeveloperClassEvaluatorPort,
) {

    @PostMapping("/evaluate")
    fun evaluate(@RequestBody request: DeveloperClassEvaluateRequest): DeveloperClassResult =
        developerClassEvaluatorPort.evaluate(request.skillAssessmentJson, request.careerEssayJson)
}
