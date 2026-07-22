package com.devquest.ai.controller.request

/**
 * `resumeContent`는 JdAnalysisEvaluatorPort.analyze의 Kotlin 기본값(`= ""`)이 HTTP JSON에서는
 * 소실되므로 nullable로 받는다 — 컨트롤러가 `?: ""`로 서버측 기본값을 복원한다.
 */
data class JdAnalysisAnalyzeRequest(
    val companyName: String,
    val jobDescription: String,
    val userSkills: List<String>,
    val userExperiences: List<String>,
    val resumeContent: String? = null,
)
