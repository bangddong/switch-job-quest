package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.JdAnalysisResult
import com.devquest.core.domain.port.JdAnalysisEvaluatorPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient

/**
 * `JdAnalysisEvaluatorPort`의 HTTP 어댑터 — ai-api `POST /internal/ai/jd-analysis/analyze` 호출.
 *
 * `resumeContent`는 포트 인터페이스가 Kotlin 기본값(`= ""`)을 선언하지만, 오버라이드 메서드는
 * Kotlin 규칙상 기본값을 다시 선언할 수 없다 — 호출부가 인터페이스 타입으로 컴파일되므로 값 생략 시
 * 컴파일 시점에 `""`가 이미 채워져 이 함수에는 항상 구체적인 `String`이 들어온다. 따라서 요청 DTO는
 * non-null로 그대로 보낸다(ai-api 쪽 nullable 복원 로직은 HTTP 경계 일반 대응이며, core는 항상 값을
 * 채워 보내므로 굳이 null을 보낼 이유가 없다).
 */
class JdAnalysisHttpEvaluator(
    restClient: RestClient,
    objectMapper: ObjectMapper,
) : BaseAiHttpAdapter(restClient, objectMapper), JdAnalysisEvaluatorPort {

    override fun analyze(
        companyName: String,
        jobDescription: String,
        userSkills: List<String>,
        userExperiences: List<String>,
        resumeContent: String,
    ): JdAnalysisResult =
        postJson(
            "/internal/ai/jd-analysis/analyze",
            JdAnalysisAnalyzeHttpRequest(companyName, jobDescription, userSkills, userExperiences, resumeContent),
        )
}

private data class JdAnalysisAnalyzeHttpRequest(
    val companyName: String,
    val jobDescription: String,
    val userSkills: List<String>,
    val userExperiences: List<String>,
    val resumeContent: String,
)
