package com.devquest.client.ai.evaluator

import com.devquest.core.domain.model.evaluation.CoachAnswerHistory
import com.devquest.core.domain.model.evaluation.CoachAnswerResult
import com.devquest.core.domain.model.evaluation.CoachReportResult
import com.devquest.core.domain.model.evaluation.CoachSessionResult
import com.devquest.core.domain.port.InterviewCoachPort
import com.devquest.core.domain.support.AiEvaluationException
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class InterviewCoachEvaluator(
    private val chatClient: ChatClient
) : InterviewCoachPort {

    override fun startSession(jdText: String, targetRole: String): CoachSessionResult {
        val prompt = """
            당신은 10년 이상의 경험을 가진 시니어 개발자 면접관 겸 커리어 코치입니다.
            아래 JD(채용공고)를 분석하고, 해당 포지션의 기술 면접을 위한 맞춤형 질문 5개를 생성하세요.

            ## 목표 포지션
            ${targetRole}

            ## 채용공고 (JD)
            ${jdText}

            ## 작업
            1. JD를 분석하여 핵심 역량 3~5가지를 추출하세요.
            2. 각 핵심 역량을 검증할 수 있는 기술 면접 질문을 총 5개 생성하세요.
            3. 질문은 실무 경험을 드러낼 수 있는 구체적인 질문으로 작성하세요.
            4. 질문 index는 0부터 시작합니다.

            반드시 다음 JSON 형식으로만 응답하세요 (다른 텍스트 금지):
            {
                "jdSummary": "JD 핵심 내용 2~3문장 요약",
                "keyCompetencies": ["핵심역량1", "핵심역량2", "핵심역량3"],
                "questions": [
                    {"index": 0, "question": "질문 내용", "competency": "관련 핵심역량"},
                    {"index": 1, "question": "질문 내용", "competency": "관련 핵심역량"},
                    {"index": 2, "question": "질문 내용", "competency": "관련 핵심역량"},
                    {"index": 3, "question": "질문 내용", "competency": "관련 핵심역량"},
                    {"index": 4, "question": "질문 내용", "competency": "관련 핵심역량"}
                ]
            }
        """.trimIndent()

        return chatClient.prompt()
            .user(prompt)
            .call()
            .entity(CoachSessionResult::class.java)
            ?: throw AiEvaluationException("면접 세션 시작 실패")
    }

    override fun evaluateAnswer(question: String, answer: String, questionIndex: Int, totalQuestions: Int): CoachAnswerResult {
        val prompt = """
            당신은 10년 이상의 경험을 가진 시니어 개발자 면접관 겸 커리어 코치입니다.
            지원자의 면접 답변을 STAR 기법(Situation, Task, Action, Result) 기준으로 평가하고, 격려와 개선점을 제공하세요.

            ## 면접 질문 (${questionIndex + 1}번 / 전체 ${totalQuestions}개)
            ${question}

            ## 지원자 답변
            ${answer}

            ## 평가 기준 (총 100점)
            - Situation/Task (25점): 상황과 과제를 명확히 설명했는가
            - Action (40점): 본인이 취한 구체적인 행동을 기술했는가
            - Result (25점): 측정 가능한 결과나 배운 점을 제시했는가
            - 전달력/논리성 (10점): 답변이 명확하고 논리적인가

            ## 응답 규칙
            - feedback: 답변의 강점과 아쉬운 점을 2~3문장으로 구체적으로 서술
            - score: 0~100 사이의 정수 점수
            - improvements: 개선할 수 있는 구체적인 방법 2~3가지 (짧고 실천 가능하게)
            - encouragement: 지원자를 격려하는 한 문장 (따뜻하고 진정성 있게)

            반드시 다음 JSON 형식으로만 응답하세요 (다른 텍스트 금지):
            {
                "feedback": "답변 피드백 내용",
                "score": 75,
                "improvements": ["개선점1", "개선점2", "개선점3"],
                "encouragement": "격려 메시지"
            }
        """.trimIndent()

        return chatClient.prompt()
            .user(prompt)
            .call()
            .entity(CoachAnswerResult::class.java)
            ?: throw AiEvaluationException("답변 평가 실패")
    }

    override fun generateReport(targetRole: String, jdSummary: String, answers: List<CoachAnswerHistory>): CoachReportResult {
        val answersText = answers.mapIndexed { idx, it ->
            """
            [질문 ${idx + 1}]
            질문: ${it.question}
            답변: ${it.answer}
            피드백: ${it.feedback}
            """.trimIndent()
        }.joinToString("\n\n")

        val prompt = """
            당신은 10년 이상의 경험을 가진 시니어 개발자 면접관 겸 커리어 코치입니다.
            지원자의 전체 면접 세션을 종합 분석하여 최종 리포트를 작성하세요.

            ## 목표 포지션
            ${targetRole}

            ## JD 요약
            ${jdSummary}

            ## 전체 질문/답변/피드백 내역
            ${answersText}

            ## 작업
            1. 전체 답변을 종합하여 전반적인 면접 성과를 평가하세요.
            2. 지원자의 강점과 약점을 구체적으로 분석하세요.
            3. 합격 가능성을 현실적으로 판단하세요.
            4. 실질적인 최종 조언을 제공하세요.

            ## 응답 규칙
            - overallScore: 전체 세션 종합 점수 (0~100 정수)
            - passLikelihood: 이 포지션 합격 가능성 % (0~100 정수, 현실적으로 평가)
            - strengths: 면접에서 드러난 강점 2~3가지 (구체적으로)
            - weaknesses: 보완이 필요한 약점 2~3가지 (건설적으로)
            - finalAdvice: 이직 준비를 위한 최종 조언 3~4문장 (실천 가능한 방향으로)

            반드시 다음 JSON 형식으로만 응답하세요 (다른 텍스트 금지):
            {
                "overallScore": 78,
                "passLikelihood": 65,
                "strengths": ["강점1", "강점2", "강점3"],
                "weaknesses": ["약점1", "약점2"],
                "finalAdvice": "최종 조언 내용"
            }
        """.trimIndent()

        return chatClient.prompt()
            .user(prompt)
            .call()
            .entity(CoachReportResult::class.java)
            ?: throw AiEvaluationException("종합 리포트 생성 실패")
    }
}
