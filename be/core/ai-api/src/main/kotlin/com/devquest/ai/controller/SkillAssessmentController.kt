package com.devquest.ai.controller

import com.devquest.ai.controller.request.SkillAssessmentEvaluateRequest
import com.devquest.core.domain.model.evaluation.SkillAssessmentResult
import com.devquest.core.domain.port.SkillAssessmentPort
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ai/skill-assessment")
class SkillAssessmentController(
    private val skillAssessmentPort: SkillAssessmentPort,
) {

    @PostMapping("/evaluate")
    fun evaluate(@RequestBody request: SkillAssessmentEvaluateRequest): SkillAssessmentResult =
        skillAssessmentPort.evaluate(request.skills, request.targetRole)
}
