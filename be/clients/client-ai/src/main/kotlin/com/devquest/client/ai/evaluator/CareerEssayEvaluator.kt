package com.devquest.client.ai.evaluator

import com.devquest.core.domain.support.AiEvaluationException
import com.devquest.core.domain.model.evaluation.EssayCheckResult
import com.devquest.core.domain.port.EssayEvaluatorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class CareerEssayEvaluator(
    private val chatClient: ChatClient
) : EssayEvaluatorPort {

    override fun evaluate(dissatisfactions: List<String>, goals: List<String>, fiveYearVision: String): EssayCheckResult {
        val prompt = """
            다음 5년차 백엔드 개발자의 이직 동기를 평가해주세요.

            ## 현재 직장 불만족 사항
            ${dissatisfactions.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")}

            ## 이직 후 목표
            ${goals.mapIndexed { i, g -> "${i + 1}. $g" }.joinToString("\n")}

            ## 5년 후 비전
            $fiveYearVision

            ## 평가 기준 (총 100점)
            - 명확성 (30점): 불만족 사유와 목표가 구체적인가
            - 논리성 (30점): 현재 상황 → 이직 → 미래 비전의 흐름이 자연스러운가
            - 동기 진정성 (20점): 성장 욕구가 진정성 있게 느껴지는가
            - 성장 방향 (20점): 5년 후 비전이 현실적이고 구체적인가

            반드시 다음 JSON 형식으로만 응답하세요 (다른 텍스트 금지):
            {
                "score": 75, "passed": true, "grade": "B",
                "clarityScore": 22, "logicScore": 23, "motivationScore": 15, "growthScore": 15,
                "feedback": "전반적으로 이직 동기가 명확하나...",
                "developerType": "아키텍트 지향형",
                "suggestedFocus": ["기술 중심 스타트업", "플랫폼 팀이 있는 기업"]
            }
        """.trimIndent()

        return chatClient.prompt()
            .user(prompt)
            .call()
            .entity(EssayCheckResult::class.java)
            ?: throw AiEvaluationException("AI 평가 응답 파싱 실패")
    }
}
