package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.BossPackageResult
import com.devquest.core.domain.port.ai.AiEvaluatorPort

interface BossPackageEvaluatorPort : AiEvaluatorPort {
    fun evaluate(
        resumeContent: String,
        githubUrl: String,
        blogUrl: String,
        targetPosition: String
    ): BossPackageResult
}
