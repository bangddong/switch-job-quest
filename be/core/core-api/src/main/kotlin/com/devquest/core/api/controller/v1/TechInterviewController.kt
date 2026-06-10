package com.devquest.core.api.controller.v1

import com.devquest.core.api.controller.v1.request.TechInterviewEvaluateRequestDto
import com.devquest.core.domain.TechInterviewService
import com.devquest.core.support.response.ApiResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/tech-interview")
class TechInterviewController(
    private val techInterviewService: TechInterviewService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/question")
    fun generateQuestion(
        @AuthenticationPrincipal(errorOnInvalidType = false) userId: String?,
        @RequestParam(defaultValue = "Java") techStack: String,
    ): ApiResponse<*> {
        return ApiResponse.success(techInterviewService.generateQuestion(userId, techStack))
    }

    @PostMapping("/evaluate")
    fun evaluate(
        @AuthenticationPrincipal(errorOnInvalidType = false) userId: String?,
        @Valid @RequestBody request: TechInterviewEvaluateRequestDto,
    ): ApiResponse<*> {
        return ApiResponse.success(
            techInterviewService.evaluate(userId, request.techStack, request.questions, request.answers)
        )
    }
}
