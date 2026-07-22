package com.devquest.ai.controller

import com.devquest.ai.controller.request.JdAnalysisAnalyzeRequest
import com.devquest.core.domain.model.evaluation.JdAnalysisResult
import com.devquest.core.domain.port.JdAnalysisEvaluatorPort
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ai/jd-analysis")
class JdAnalysisController(
    private val jdAnalysisEvaluatorPort: JdAnalysisEvaluatorPort,
) {

    @PostMapping("/analyze")
    fun analyze(@RequestBody request: JdAnalysisAnalyzeRequest): JdAnalysisResult =
        jdAnalysisEvaluatorPort.analyze(
            request.companyName,
            request.jobDescription,
            request.userSkills,
            request.userExperiences,
            // Kotlin default 파라미터 소실 대응 — 필드 생략 시 서버측 기본값("")으로 복원
            request.resumeContent ?: "",
        )
}
