package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.ResumeCheckResult

interface ResumeEvaluatorPort {
    fun evaluate(targetCompany: String, targetJd: String, resumeContent: String): ResumeCheckResult
}
