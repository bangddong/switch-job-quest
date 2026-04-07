package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.port.PersonalityEvaluatorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class PersonalityInterviewEvaluator(
    private val chatClient: ChatClient,
    private val aiCallExecutor: AiCallExecutor
) : PersonalityEvaluatorPort {

    override fun evaluate(question: String, answer: String): AiEvaluationResult {
        val prompt = """
            다음 인성 면접 답변을 평가해주세요.

            ## 질문: $question
            ## 후보자 답변
            $answer

            ## 평가 기준 (총 100점)
            - 구체성 (30점) - 진정성 (25점) - 성장 마인드셋 (25점) - 커뮤니케이션 (20점)

            반드시 다음 JSON 형식으로만 응답하세요:
            {
                "score": 78, "passed": true, "grade": "B",
                "summary": "...", "strengths": ["..."], "improvements": ["..."],
                "detailedFeedback": "...", "xpMultiplier": 1.0, "retryAllowed": true
            }
        """.trimIndent()

        return aiCallExecutor.execute {
            chatClient.prompt().user(prompt).call().entity(AiEvaluationResult::class.java)
        }
    }
}
