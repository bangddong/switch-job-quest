package com.devquest.client.ai.evaluator

import com.devquest.core.domain.support.AiEvaluationException
import com.devquest.core.domain.model.evaluation.BossPackageResult
import com.devquest.core.domain.port.BossPackageEvaluatorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class BossPackageEvaluator(
    private val chatClient: ChatClient
) : BossPackageEvaluatorPort {

    override fun evaluate(
        resumeContent: String,
        githubUrl: String,
        blogUrl: String,
        targetPosition: String
    ): BossPackageResult {
        val prompt = """
            다음 지원 패키지를 종합 평가해주세요.

            ## 목표 포지션: $targetPosition
            ## GitHub URL: $githubUrl
            ## 블로그 URL: ${blogUrl.ifBlank { "미제공" }}
            ## 이력서 내용
            $resumeContent

            ## 평가 기준
            1. 이력서 임팩트 (STAR 기법 활용, 수치화, 키워드 적합성): /20점
            2. GitHub 일관성 (커밋 활동, README 품질, 프로젝트 구성): /20점
            3. 기술 전문성 (블로그/GitHub 기반 기술 깊이): /20점
            4. 포지션 핏 (목표 포지션과의 정합성): /20점
            5. 차별화 포인트 (경쟁자 대비 강점): /20점

            반드시 다음 JSON 형식으로만 응답하세요:
            {
                "overallScore": 75,
                "resumeImpactScore": 16,
                "githubConsistencyScore": 15,
                "technicalDepthScore": 14,
                "positionFitScore": 15,
                "differentiationScore": 15,
                "strengths": ["강점1", "강점2", "강점3"],
                "improvements": ["개선사항1", "개선사항2"],
                "overallFeedback": "종합 피드백..."
            }
        """.trimIndent()

        return chatClient.prompt()
            .user(prompt)
            .call()
            .entity(BossPackageResult::class.java)
            ?: throw AiEvaluationException("지원 패키지 평가 실패")
    }
}
