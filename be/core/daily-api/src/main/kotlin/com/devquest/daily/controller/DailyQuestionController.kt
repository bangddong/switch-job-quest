package com.devquest.daily.controller

import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.daily.controller.request.DailyQuestionEvaluateRequestDto
import com.devquest.daily.controller.request.DailyQuestionExplainRequestDto
import com.devquest.daily.controller.response.DailyApiResponse
import com.devquest.daily.service.DailyQuestionApiService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * core-api `DailyQuestionController`와 계약(경로·요청 필드)은 동일하지만 응답 봉투는
 * daily-api 자체 [DailyApiResponse]다(D-007). 예외는 [DailyApiControllerAdvice]가 처리한다 —
 * try-catch 없음.
 */
@RestController
@RequestMapping("/api/v1/daily-question")
class DailyQuestionController(
    private val dailyQuestionApiService: DailyQuestionApiService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun getTodayQuestion(): DailyApiResponse<Map<String, String>> {
        val question = dailyQuestionApiService.getTodayQuestion()
        return DailyApiResponse.success(mapOf("question" to question))
    }

    @PostMapping("/evaluate")
    fun evaluate(
        @Valid @RequestBody request: DailyQuestionEvaluateRequestDto,
    ): DailyApiResponse<TechInterviewResult> {
        return DailyApiResponse.success(dailyQuestionApiService.evaluate(request.question, request.answer))
    }

    @PostMapping("/explain")
    fun explain(
        @Valid @RequestBody request: DailyQuestionExplainRequestDto,
    ): DailyApiResponse<Map<String, String>> {
        val explanation = dailyQuestionApiService.explain(
            request.question, request.answer, request.feedback, request.userQuestion, request.modelAnswer,
        )
        return DailyApiResponse.success(mapOf("explanation" to explanation))
    }
}
