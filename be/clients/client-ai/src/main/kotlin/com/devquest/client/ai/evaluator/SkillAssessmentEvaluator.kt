package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.core.domain.model.evaluation.SkillAssessmentResult
import com.devquest.core.domain.port.SkillAssessmentPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class SkillAssessmentEvaluator(
    private val chatClient: ChatClient,
    private val aiCallExecutor: AiCallExecutor
) : SkillAssessmentPort {

    override fun evaluate(skills: List<String>, targetRole: String): SkillAssessmentResult {
        val skillsText = skills.joinToString("\n") { "- $it" }

        val prompt = """
            당신은 5년차 백엔드 개발자의 이직 준비를 돕는 커리어 코치입니다.
            아래 개발자의 기술 스택을 분석하고 ${targetRole} 포지션을 목표로 한 진단 결과를 제공하세요.

            ## 보유 기술 스택 (형식: 기술명:경력기간)
            $skillsText
            (예: Java:5년은 5년간 실무 사용, Kubernetes:6개월은 6개월 경험을 의미)

            ## 목표 포지션
            ${targetRole}

            ## 진단 기준 (총 100점)
            - 기술 다양성 및 깊이 (30점): 경력기간을 고려한 기술의 폭과 전문성
            - 시장 적합성 (30점): 목표 포지션 JD에서 자주 요구되는 기술 보유율
            - 기술 스택 일관성 (20점): 백엔드/풀스택/클라우드 등 방향성 일치
            - 성장 가능성 (20점): 현재 스택에서 다음 단계로의 확장성

            ## 응답 규칙
            - developerType: 이 개발자의 현재 유형 (예: "안정형 백엔드 스페셜리스트", "클라우드 전환 준비형")
            - strengths: 강점 기술 2~3가지 (구체적으로, 시장 관점에서)
            - improvements: 갭 기술 2가지 + 학습 우선순위 1가지 (총 3가지, 간결하게)
            - feedback: 종합 진단 2~3문장 (목표 포지션 대비 현재 위치와 방향 제시)

            반드시 다음 JSON 형식으로만 응답하세요 (다른 텍스트 금지):
            {
                "score": 75,
                "passed": true,
                "grade": "B",
                "developerType": "안정형 백엔드 스페셜리스트",
                "strengths": ["강점1", "강점2"],
                "improvements": ["갭기술1", "갭기술2", "학습우선순위"],
                "feedback": "종합 진단 내용"
            }
        """.trimIndent()

        return aiCallExecutor.execute {
            chatClient.prompt().user(prompt).call().entity(SkillAssessmentResult::class.java)
        }
    }
}
