package com.devquest.core.api.controller.v1

import com.devquest.core.api.controller.v1.request.DailyQuestionEvaluateRequestDto
import com.devquest.core.domain.DailyQuestionService
import com.devquest.core.support.response.ApiResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/daily-question")
class DailyQuestionController(
    private val dailyQuestionService: DailyQuestionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun getTodayQuestion(): ApiResponse<*> {
        val question = dailyQuestionService.getTodayQuestion()
        return ApiResponse.success(mapOf("question" to question))
    }

    @PostMapping("/evaluate")
    fun evaluate(
        @Valid @RequestBody request: DailyQuestionEvaluateRequestDto,
    ): ApiResponse<*> {
        return ApiResponse.success(dailyQuestionService.evaluate(request.question, request.answer))
    }
}
