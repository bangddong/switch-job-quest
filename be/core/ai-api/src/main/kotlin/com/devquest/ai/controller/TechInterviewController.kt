package com.devquest.ai.controller

import com.devquest.ai.controller.request.TechInterviewDailyQuestionRequest
import com.devquest.ai.controller.request.TechInterviewEvaluateRequest
import com.devquest.ai.controller.request.TechInterviewExplainFollowupRequest
import com.devquest.ai.controller.request.TechInterviewQuestionsRequest
import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.core.domain.port.TechInterviewPort
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ai/tech-interview")
class TechInterviewController(
    private val techInterviewPort: TechInterviewPort,
) {

    @PostMapping("/questions")
    fun generateQuestions(@RequestBody request: TechInterviewQuestionsRequest): TechInterviewResult =
        techInterviewPort.generateQuestions(request.techStack)

    @PostMapping("/evaluate")
    fun evaluate(@RequestBody request: TechInterviewEvaluateRequest): TechInterviewResult =
        techInterviewPort.evaluate(request.techStack, request.questions, request.answers)

    // 반환 타입이 순수 String이라 기본 컨버터가 text/plain을 고를 수 있다 — Task 1.4의 RestClient가
    // core-domain 계약과 동일하게 JSON 문자열로 역직렬화할 수 있도록 명시적으로 JSON을 강제한다.
    @PostMapping("/daily-question", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun generateDailyQuestion(@RequestBody request: TechInterviewDailyQuestionRequest): String =
        techInterviewPort.generateDailyQuestion(
            request.techStack,
            // Kotlin default 파라미터 소실 대응 — 필드 생략 시 서버측 기본값(emptyList())으로 복원
            request.recentQuestions ?: emptyList(),
        )

    @PostMapping("/explain-followup", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun explainFollowup(@RequestBody request: TechInterviewExplainFollowupRequest): String =
        techInterviewPort.explainFollowup(
            request.question,
            request.answer,
            request.feedback,
            request.userQuestion,
            request.modelAnswer,
        )
}
