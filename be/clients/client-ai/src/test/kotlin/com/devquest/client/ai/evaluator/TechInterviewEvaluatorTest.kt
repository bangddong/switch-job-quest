package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.AiMetricsRecorder
import com.devquest.client.ai.support.ConferenceReferenceLoader
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import com.devquest.core.domain.support.AiEvaluationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.client.ChatClient

@ExtendWith(MockitoExtension::class)
class TechInterviewEvaluatorTest {

    private val chatClient: ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val metricsRecorder = AiMetricsRecorder(SimpleMeterRegistry())
    private val aiCallExecutor = AiCallExecutor(maxRetry = 1, metricsRecorder = metricsRecorder)
    private val conferenceReferenceLoader = ConferenceReferenceLoader()
    private val evaluator = TechInterviewEvaluator(chatClient, aiCallExecutor, conferenceReferenceLoader)

    @Test
    fun `generateDailyQuestion — AI가 null을 반환하면 AiEvaluationException 발생`() {
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call().content()
        ).thenReturn(null)

        assertThatThrownBy { evaluator.generateDailyQuestion("Java,Spring Boot,JPA") }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("최종 실패")
    }

    @Test
    fun `generateDailyQuestion — AI가 정상 응답을 반환하면 질문 텍스트를 그대로 반환`() {
        val expected = "JPA에서 N+1 문제란 무엇이며 어떻게 해결하나요?"
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call().content()
        ).thenReturn(expected)

        val result = evaluator.generateDailyQuestion("Java,Spring Boot,JPA")

        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `generateQuestions — AI가 null을 반환하면 AiEvaluationException 발생`() {
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call().content()
        ).thenReturn(null)

        assertThatThrownBy { evaluator.generateQuestions("Java,Spring Boot") }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("최종 실패")
    }

    @Test
    fun `generateQuestions — AI가 정상 응답을 반환하면 결과를 그대로 반환`() {
        val json = """{"questions":["Q1","Q2"],"overallScore":0,"feedback":"","passed":false,"modelAnswer":""}"""
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call().content()
        ).thenReturn(json)

        val result = evaluator.generateQuestions("Java,Spring Boot")

        assertThat(result.questions).hasSize(2)
    }

    @Test
    fun `evaluate — Kafka 관련 질문 시 컨퍼런스 참고자료가 시스템 프롬프트에 주입됨`() {
        val json = """{"questions":["Kafka 컨슈머 그룹이란?"],"overallScore":80,"feedback":"좋음","passed":true,"modelAnswer":"답변"}"""
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call().content()
        ).thenReturn(json)

        val result = evaluator.evaluate(
            techStack = "Kafka",
            questions = listOf("Kafka 컨슈머 그룹이란?"),
            answers = listOf("컨슈머 그룹은...")
        )

        assertThat(result.overallScore).isEqualTo(80)
        assertThat(result.passed).isTrue()
    }

    @Test
    fun `evaluate — AI가 null을 반환하면 AiEvaluationException 발생`() {
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call().content()
        ).thenReturn(null)

        assertThatThrownBy {
            evaluator.evaluate(
                techStack = "Java",
                questions = listOf("JVM이란?"),
                answers = listOf("JVM은...")
            )
        }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("최종 실패")
    }

    @Test
    fun `explainFollowup — AI가 null을 반환하면 AiEvaluationException 발생`() {
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call().content()
        ).thenReturn(null)

        assertThatThrownBy {
            evaluator.explainFollowup(
                question = "OSIV란 무엇인가요?",
                answer = "OSIV는 영속성 컨텍스트를 뷰까지 열어두는 전략입니다.",
                feedback = "핵심은 맞지만 트랜잭션 범위 설명이 부족합니다.",
                userQuestion = "트랜잭션 범위가 정확히 뭔가요?",
            )
        }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("최종 실패")
    }

    @Test
    fun `explainFollowup — AI가 정상 응답을 반환하면 설명 텍스트를 그대로 반환`() {
        val expected = "트랜잭션 범위는 @Transactional이 적용된 메서드가 시작되고 끝나는 구간을 의미합니다."
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call().content()
        ).thenReturn(expected)

        val result = evaluator.explainFollowup(
            question = "OSIV란 무엇인가요?",
            answer = "OSIV는 영속성 컨텍스트를 뷰까지 열어두는 전략입니다.",
            feedback = "핵심은 맞지만 트랜잭션 범위 설명이 부족합니다.",
            userQuestion = "트랜잭션 범위가 정확히 뭔가요?",
            modelAnswer = "OSIV는...(모범 답안)",
        )

        assertThat(result).isEqualTo(expected)
    }
}
