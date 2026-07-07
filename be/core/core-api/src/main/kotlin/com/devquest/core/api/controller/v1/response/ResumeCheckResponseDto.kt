package com.devquest.core.api.controller.v1.response

import com.devquest.core.domain.model.evaluation.ResumeCheckResult
import com.devquest.core.domain.model.evaluation.ResumeImprovement
import com.devquest.core.domain.model.evaluation.ResumeRewrite
import java.time.LocalDateTime

data class ResumeCheckResponseDto(
    val overallScore: Int,
    val passed: Boolean,
    val starMethodScore: Int,
    val quantificationScore: Int,
    val keywordMatchScore: Int,
    val improvements: List<ResumeImprovement>,
    val rewrittenExamples: List<ResumeRewrite>,
    val checkedAt: LocalDateTime,
) {
    companion object {
        fun from(result: ResumeCheckResult, checkedAt: LocalDateTime): ResumeCheckResponseDto {
            return ResumeCheckResponseDto(
                overallScore = result.overallScore,
                passed = result.passed,
                starMethodScore = result.starMethodScore,
                quantificationScore = result.quantificationScore,
                keywordMatchScore = result.keywordMatchScore,
                improvements = result.improvements,
                rewrittenExamples = result.rewrittenExamples,
                checkedAt = checkedAt,
            )
        }
    }
}
