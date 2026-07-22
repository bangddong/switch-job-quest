package com.devquest.ai.controller

import com.devquest.ai.controller.request.ResumeEvaluateRequest
import com.devquest.core.domain.model.evaluation.ResumeCheckResult
import com.devquest.core.domain.port.ResumeEvaluatorPort
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ai/resume")
class ResumeController(
    private val resumeEvaluatorPort: ResumeEvaluatorPort,
) {

    @PostMapping("/evaluate")
    fun evaluate(@RequestBody request: ResumeEvaluateRequest): ResumeCheckResult =
        resumeEvaluatorPort.evaluate(request.targetCompany, request.targetJd, request.resumeContent)
}
