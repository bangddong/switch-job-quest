package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.ResumeCheckResult
import com.devquest.core.domain.port.ai.AiEvaluatorPort

interface ResumeEvaluatorPort : AiEvaluatorPort {
    fun evaluate(targetCompany: String, targetJd: String, resumeContent: String): ResumeCheckResult
}
