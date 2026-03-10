package com.devquest.client.ai.evaluator

import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.port.BlogEvaluatorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class TechBlogEvaluator(
    private val chatClient: ChatClient
) : BlogEvaluatorPort {

    override fun evaluate(techTopic: String, title: String, content: String): AiEvaluationResult {
        val prompt = """
            다음 기술 블로그 포스트를 평가해주세요.

            ## 주제: $techTopic
            ## 제목: $title
            ## 본문
            $content

            ## 평가 기준 (총 100점)
            - 기술적 정확성 (40점): 내용이 기술적으로 올바른가?
            - 깊이와 인사이트 (25점): 표면적 설명 이상의 깊이가 있는가?
            - 코드 예제 품질 (20점): 코드 예제가 실용적이고 정확한가?
            - 가독성과 구조 (15점): 글의 흐름과 설명이 명확한가?

            반드시 다음 JSON 형식으로만 응답하세요:
            {
                "score": 82, "passed": true, "grade": "A",
                "summary": "...", "strengths": ["..."], "improvements": ["..."],
                "detailedFeedback": "...", "xpMultiplier": 1.2, "retryAllowed": true
            }
        """.trimIndent()

        return chatClient.prompt()
            .user(prompt)
            .call()
            .entity(AiEvaluationResult::class.java)
            ?: throw RuntimeException("AI 블로그 평가 실패")
    }
}
