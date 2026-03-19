package com.devquest.client.ai.evaluator

import com.devquest.core.domain.support.AiEvaluationException
import com.devquest.core.domain.model.evaluation.JdAnalysisResult
import com.devquest.core.domain.port.JdAnalysisEvaluatorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class JdAnalysisEvaluator(
    private val chatClient: ChatClient
) : JdAnalysisEvaluatorPort {

    override fun analyze(companyName: String, jobDescription: String, userSkills: List<String>, userExperiences: List<String>): JdAnalysisResult {
        val prompt = """
            다음 채용공고(JD)를 분석하고, 후보자와의 기술 갭을 평가해주세요.

            ## 회사: $companyName
            ## 채용공고 내용
            $jobDescription
            ## 후보자 보유 기술
            ${userSkills.joinToString(", ")}
            ## 후보자 경력 요약
            ${userExperiences.joinToString("\n")}

            반드시 다음 JSON 형식으로만 응답하세요:
            {
                "companyName": "$companyName",
                "requiredSkills": [{"skill": "Spring Boot", "required": true, "userLevel": "상", "importance": "HIGH"}],
                "hiddenRequirements": ["..."], "overallMatchScore": 78,
                "keyDifferentiators": ["..."], "applicationStrategy": "..."
            }
        """.trimIndent()

        return chatClient.prompt()
            .user(prompt)
            .call()
            .entity(JdAnalysisResult::class.java)
            ?: throw AiEvaluationException("JD 분석 실패")
    }
}
