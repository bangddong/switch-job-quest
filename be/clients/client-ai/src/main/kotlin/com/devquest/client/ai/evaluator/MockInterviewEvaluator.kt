package com.devquest.client.ai.evaluator

import com.devquest.core.domain.support.AiEvaluationException
import com.devquest.core.domain.model.evaluation.InterviewEvaluationResult
import com.devquest.core.domain.port.InterviewEvaluatorPort
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class MockInterviewEvaluator(
    private val chatClient: ChatClient
) : InterviewEvaluatorPort {

    private val objectMapper = jacksonObjectMapper()

    private val log = LoggerFactory.getLogger(javaClass)

    override fun evaluate(category: String, question: String, answer: String, questionId: String): InterviewEvaluationResult {
        val prompt = """
            다음 기술 면접 답변을 채점해주세요.

            ## 카테고리: $category
            ## 질문: $question
            ## 후보자 답변
            $answer

            ## 채점 기준
            - 기술적 정확성: /40점 - 깊이와 응용력: /30점 - 실무 경험 연결: /20점 - 커뮤니케이션: /10점

            반드시 다음 JSON 형식으로만 응답하세요:
            {
                "questionId": "$questionId", "question": "$question",
                "userAnswer": "${answer.take(100)}...", "score": 72, "passed": true,
                "technicalAccuracy": 30, "depthAndApplication": 22,
                "practicalExperience": 12, "communicationClarity": 8,
                "correctAnswer": "...", "keyPointsMissed": ["..."], "improvements": "..."
            }
        """.trimIndent()

        return chatClient.prompt()
            .user(prompt)
            .call()
            .entity(InterviewEvaluationResult::class.java)
            ?: throw AiEvaluationException("면접 평가 실패")
    }

    override fun generateQuestions(categories: List<String>, count: Int): List<Map<String, String>> {
        val prompt = """
            다음 카테고리에서 백엔드 5년차 개발자 면접 질문 ${count}개를 선별해주세요.
            카테고리: ${categories.joinToString(", ")}

            반드시 다음 JSON 배열 형식으로만 응답하세요:
            [{"id": "q1", "category": "DB", "question": "...", "difficulty": "MEDIUM"}]
        """.trimIndent()

        val response = chatClient.prompt()
            .user(prompt)
            .call()
            .content() ?: "[]"

        return try {
            objectMapper.readValue<List<Map<String, String>>>(response)
        } catch (e: Exception) {
            log.warn("질문 생성 파싱 실패: ${e.message}")
            emptyList()
        }
    }
}
