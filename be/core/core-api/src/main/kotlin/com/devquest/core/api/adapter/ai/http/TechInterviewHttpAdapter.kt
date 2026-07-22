package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.core.domain.port.TechInterviewPort
import tools.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient

/**
 * `TechInterviewPort`의 HTTP 어댑터 — ai-api `POST /internal/ai/tech-interview/{questions,evaluate,
 * daily-question,explain-followup}` 호출.
 *
 * `daily-question`·`explain-followup`은 ai-api가 `text/plain;charset=UTF-8`로 응답한다(반환 타입이
 * 순수 `String`이기 때문 — `TechInterviewWireFormatContractTest`로 실측 확정). [BaseAiHttpAdapter.postText]
 * 는 Accept를 강제하지 않아(계획 문서 함정 (c)) 이 2개에서 406이 나지 않는다.
 *
 * `recentQuestions`·`modelAnswer`는 포트 인터페이스의 기본값/nullable이 Kotlin 컴파일 시점에 호출부에서
 * 이미 해석되므로(오버라이드는 기본값을 재선언할 수 없음) 이 함수는 항상 구체적인 값을 받는다.
 * `modelAnswer`는 포트 시그니처 자체가 `String?`이라 명시적으로 null이 전달될 수 있고, 그 null은
 * 그대로 JSON에 실려도 ai-api DTO가 nullable이라 정상 처리된다.
 */
class TechInterviewHttpAdapter(
    restClient: RestClient,
    objectMapper: ObjectMapper,
) : BaseAiHttpAdapter(restClient, objectMapper), TechInterviewPort {

    override fun generateQuestions(techStack: String): TechInterviewResult =
        postJson("/internal/ai/tech-interview/questions", TechInterviewQuestionsHttpRequest(techStack))

    override fun evaluate(techStack: String, questions: List<String>, answers: List<String>): TechInterviewResult =
        postJson(
            "/internal/ai/tech-interview/evaluate",
            TechInterviewEvaluateHttpRequest(techStack, questions, answers),
        )

    override fun generateDailyQuestion(techStack: String, recentQuestions: List<String>): String =
        postText(
            "/internal/ai/tech-interview/daily-question",
            TechInterviewDailyQuestionHttpRequest(techStack, recentQuestions),
        )

    override fun explainFollowup(
        question: String,
        answer: String,
        feedback: String,
        userQuestion: String,
        modelAnswer: String?,
    ): String =
        postText(
            "/internal/ai/tech-interview/explain-followup",
            TechInterviewExplainFollowupHttpRequest(question, answer, feedback, userQuestion, modelAnswer),
        )
}

private data class TechInterviewQuestionsHttpRequest(
    val techStack: String,
)

private data class TechInterviewEvaluateHttpRequest(
    val techStack: String,
    val questions: List<String>,
    val answers: List<String>,
)

private data class TechInterviewDailyQuestionHttpRequest(
    val techStack: String,
    val recentQuestions: List<String>,
)

private data class TechInterviewExplainFollowupHttpRequest(
    val question: String,
    val answer: String,
    val feedback: String,
    val userQuestion: String,
    val modelAnswer: String?,
)
