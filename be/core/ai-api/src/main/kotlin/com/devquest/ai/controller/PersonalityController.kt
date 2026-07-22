package com.devquest.ai.controller

import com.devquest.ai.controller.request.PersonalityEvaluateRequest
import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.port.PersonalityEvaluatorPort
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ai/personality")
class PersonalityController(
    private val personalityEvaluatorPort: PersonalityEvaluatorPort,
) {

    @PostMapping("/evaluate")
    fun evaluate(@RequestBody request: PersonalityEvaluateRequest): AiEvaluationResult =
        personalityEvaluatorPort.evaluate(request.question, request.answer)
}
