package com.devquest.client.ai.evaluator

import com.devquest.core.domain.model.evaluation.CoachAnswerHistory
import com.devquest.core.domain.model.evaluation.CoachAnswerResult
import com.devquest.core.domain.model.evaluation.CoachReportResult
import com.devquest.core.domain.model.evaluation.CoachSessionResult
import com.devquest.core.domain.model.evaluation.CoachQuestion
import com.devquest.core.domain.support.AiEvaluationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class InterviewCoachEvaluatorTest {

    private val chatClient: org.springframework.ai.chat.client.ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val evaluator = InterviewCoachEvaluator(chatClient)

    // ── startSession ──────────────────────────────────────────────────────────

    @Test
    fun `startSession - AI가 null을 반환하면 AiEvaluationException 발생`() {
        whenever(
            chatClient.prompt().user(any<String>()).call().entity(CoachSessionResult::class.java)
        ).thenReturn(null)

        assertThatThrownBy {
            evaluator.startSession(jdText = "백엔드 개발자 모집합니다.", targetRole = "시니어 백엔드 개발자")
        }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("면접 세션 시작 실패")
    }

    @Test
    fun `startSession - AI가 정상 응답을 반환하면 결과를 그대로 반환`() {
        val expected = CoachSessionResult(
            jdSummary = "Spring Boot 기반 백엔드 개발자 모집",
            keyCompetencies = listOf("Spring Boot", "JPA", "MSA"),
            questions = listOf(
                CoachQuestion(0, "JPA N+1 문제를 어떻게 해결하셨나요?", "JPA"),
                CoachQuestion(1, "MSA 환경에서 트랜잭션 처리 경험을 공유해주세요.", "MSA"),
            )
        )
        whenever(
            chatClient.prompt().user(any<String>()).call().entity(CoachSessionResult::class.java)
        ).thenReturn(expected)

        val result = evaluator.startSession(
            jdText = "Spring Boot 기반 백엔드 개발자를 모집합니다. JPA, MSA 경험 우대.",
            targetRole = "시니어 백엔드 개발자"
        )

        assertThat(result.jdSummary).isEqualTo("Spring Boot 기반 백엔드 개발자 모집")
        assertThat(result.keyCompetencies).hasSize(3)
        assertThat(result.questions).hasSize(2)
    }

    @Test
    fun `startSession - 프롬프트에 JD 내용과 목표 포지션이 포함된다`() {
        val promptCaptor = ArgumentCaptor.forClass(String::class.java)
        whenever(
            chatClient.prompt().user(capture(promptCaptor)).call().entity(CoachSessionResult::class.java)
        ).thenReturn(CoachSessionResult())

        evaluator.startSession(
            jdText = "Kotlin과 Spring Boot를 사용하는 팀입니다.",
            targetRole = "백엔드 개발자"
        )

        val prompt = promptCaptor.value
        assertThat(prompt).contains("Kotlin과 Spring Boot를 사용하는 팀입니다.")
        assertThat(prompt).contains("백엔드 개발자")
        assertThat(prompt).contains("핵심 역량")
    }

    // ── evaluateAnswer ────────────────────────────────────────────────────────

    @Test
    fun `evaluateAnswer - AI가 null을 반환하면 AiEvaluationException 발생`() {
        whenever(
            chatClient.prompt().user(any<String>()).call().entity(CoachAnswerResult::class.java)
        ).thenReturn(null)

        assertThatThrownBy {
            evaluator.evaluateAnswer(
                question = "JPA N+1 문제를 어떻게 해결하셨나요?",
                answer = "fetch join을 사용했습니다.",
                questionIndex = 0,
                totalQuestions = 5,
            )
        }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("답변 평가 실패")
    }

    @Test
    fun `evaluateAnswer - AI가 정상 응답을 반환하면 결과를 그대로 반환`() {
        val expected = CoachAnswerResult(
            feedback = "STAR 기법을 잘 활용한 답변입니다.",
            score = 82,
            improvements = listOf("결과 수치를 더 구체적으로 제시하면 좋겠습니다."),
            encouragement = "좋은 경험을 잘 전달하셨어요. 계속 화이팅!"
        )
        whenever(
            chatClient.prompt().user(any<String>()).call().entity(CoachAnswerResult::class.java)
        ).thenReturn(expected)

        val result = evaluator.evaluateAnswer(
            question = "JPA N+1 문제를 어떻게 해결하셨나요?",
            answer = "fetch join과 @EntityGraph를 상황에 맞게 활용했습니다.",
            questionIndex = 1,
            totalQuestions = 5,
        )

        assertThat(result.score).isEqualTo(82)
        assertThat(result.feedback).contains("STAR")
        assertThat(result.encouragement).isNotBlank()
    }

    @Test
    fun `evaluateAnswer - 프롬프트에 질문 번호와 전체 문제 수가 포함된다`() {
        val promptCaptor = ArgumentCaptor.forClass(String::class.java)
        whenever(
            chatClient.prompt().user(capture(promptCaptor)).call().entity(CoachAnswerResult::class.java)
        ).thenReturn(CoachAnswerResult())

        evaluator.evaluateAnswer(
            question = "테스트 질문",
            answer = "테스트 답변",
            questionIndex = 2,
            totalQuestions = 5,
        )

        val prompt = promptCaptor.value
        assertThat(prompt).contains("3번")
        assertThat(prompt).contains("전체 5개")
    }

    // ── generateReport ────────────────────────────────────────────────────────

    @Test
    fun `generateReport - AI가 null을 반환하면 AiEvaluationException 발생`() {
        whenever(
            chatClient.prompt().user(any<String>()).call().entity(CoachReportResult::class.java)
        ).thenReturn(null)

        assertThatThrownBy {
            evaluator.generateReport(
                targetRole = "시니어 백엔드 개발자",
                jdSummary = "Spring Boot 기반 백엔드 개발자 모집",
                answers = listOf(CoachAnswerHistory("질문", "답변", "피드백"))
            )
        }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("종합 리포트 생성 실패")
    }

    @Test
    fun `generateReport - AI가 정상 응답을 반환하면 결과를 그대로 반환`() {
        val expected = CoachReportResult(
            overallScore = 78,
            passLikelihood = 65,
            strengths = listOf("기술적 깊이", "문제 해결 능력"),
            weaknesses = listOf("결과 수치화 부족"),
            finalAdvice = "STAR 기법 연습을 꾸준히 하면 합격 가능성이 높아집니다."
        )
        whenever(
            chatClient.prompt().user(any<String>()).call().entity(CoachReportResult::class.java)
        ).thenReturn(expected)

        val result = evaluator.generateReport(
            targetRole = "시니어 백엔드 개발자",
            jdSummary = "Spring Boot 기반 백엔드 개발자 모집",
            answers = listOf(
                CoachAnswerHistory("JPA N+1 문제를 어떻게 해결하셨나요?", "fetch join을 사용했습니다.", "구체적인 결과 수치가 부족합니다.")
            )
        )

        assertThat(result.overallScore).isEqualTo(78)
        assertThat(result.passLikelihood).isEqualTo(65)
        assertThat(result.strengths).hasSize(2)
        assertThat(result.finalAdvice).isNotBlank()
    }

    @Test
    fun `generateReport - 프롬프트에 목표 포지션과 JD 요약이 포함된다`() {
        val promptCaptor = ArgumentCaptor.forClass(String::class.java)
        whenever(
            chatClient.prompt().user(capture(promptCaptor)).call().entity(CoachReportResult::class.java)
        ).thenReturn(CoachReportResult())

        evaluator.generateReport(
            targetRole = "시니어 백엔드 개발자",
            jdSummary = "Kotlin 기반 서비스 개발",
            answers = listOf(CoachAnswerHistory("질문", "답변", "피드백"))
        )

        val prompt = promptCaptor.value
        assertThat(prompt).contains("시니어 백엔드 개발자")
        assertThat(prompt).contains("Kotlin 기반 서비스 개발")
        assertThat(prompt).contains("합격 가능성")
    }
}
