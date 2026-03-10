package com.devquest.client.ai.evaluator

import com.devquest.core.domain.model.evaluation.ResumeCheckResult
import com.devquest.core.domain.port.ResumeEvaluatorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class ResumeCheckEvaluator(
    private val chatClient: ChatClient
) : ResumeEvaluatorPort {

    override fun evaluate(targetCompany: String, targetJd: String, resumeContent: String): ResumeCheckResult {
        val prompt = """
            다음 이력서를 평가하고 개선 방안을 제시해주세요.

            ## 지원 회사: $targetCompany
            ## 채용공고 핵심 요구사항
            ${targetJd.take(500)}
            ## 이력서 내용
            $resumeContent

            ## 평가 기준
            1. STAR 기법 활용도: /40점
            2. 수치화 정도: /30점
            3. JD 키워드 매칭도: /30점

            반드시 다음 JSON 형식으로만 응답하세요:
            {
                "overallScore": 72, "starMethodScore": 28, "quantificationScore": 22, "keywordMatchScore": 22,
                "improvements": [{"section": "경력사항", "original": "...", "issue": "...", "suggestion": "..."}],
                "rewrittenExamples": [{"original": "...", "improved": "...", "explanation": "..."}]
            }
        """.trimIndent()

        return chatClient.prompt()
            .user(prompt)
            .call()
            .entity(ResumeCheckResult::class.java)
            ?: throw RuntimeException("이력서 평가 실패")
    }
}
