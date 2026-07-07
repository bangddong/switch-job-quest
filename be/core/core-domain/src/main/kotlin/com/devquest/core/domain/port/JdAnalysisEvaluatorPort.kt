package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.JdAnalysisResult

interface JdAnalysisEvaluatorPort {
    fun analyze(
        companyName: String,
        jobDescription: String,
        userSkills: List<String>,
        userExperiences: List<String>,
        resumeContent: String = "",
    ): JdAnalysisResult
}
