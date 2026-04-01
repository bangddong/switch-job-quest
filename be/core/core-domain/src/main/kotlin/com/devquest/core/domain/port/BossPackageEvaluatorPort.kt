package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.BossPackageResult

interface BossPackageEvaluatorPort {
    fun evaluate(
        resumeContent: String,
        githubUrl: String,
        blogUrl: String,
        targetPosition: String
    ): BossPackageResult
}
