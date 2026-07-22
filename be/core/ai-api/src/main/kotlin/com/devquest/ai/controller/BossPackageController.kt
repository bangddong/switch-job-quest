package com.devquest.ai.controller

import com.devquest.ai.controller.request.BossPackageEvaluateRequest
import com.devquest.core.domain.model.evaluation.BossPackageResult
import com.devquest.core.domain.port.BossPackageEvaluatorPort
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ai/boss-package")
class BossPackageController(
    private val bossPackageEvaluatorPort: BossPackageEvaluatorPort,
) {

    @PostMapping("/evaluate")
    fun evaluate(@RequestBody request: BossPackageEvaluateRequest): BossPackageResult =
        bossPackageEvaluatorPort.evaluate(
            request.resumeContent,
            request.githubUrl,
            request.blogUrl,
            request.targetPosition,
        )
}
