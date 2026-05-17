package com.devquest.core.api.controller.v1

import com.devquest.core.api.controller.v1.request.CodeSubmitRequestDto
import com.devquest.core.domain.CodingQuestService
import com.devquest.core.support.response.ApiResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/coding")
class CodingQuestController(
    private val codingQuestService: CodingQuestService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/problem")
    fun generateProblem(
        @AuthenticationPrincipal userId: String,
        @RequestParam(defaultValue = "JAVA") language: String
    ): ApiResponse<*> {
        val problem = codingQuestService.generateProblem(userId, language)
        return ApiResponse.success(problem)
    }

    @PostMapping("/submit")
    fun submitCode(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: CodeSubmitRequestDto
    ): ApiResponse<*> {
        val result = codingQuestService.submitCode(userId, request.problemId, request.language, request.userCode)
        return ApiResponse.success(result)
    }

    @GetMapping("/level")
    fun getLevel(@AuthenticationPrincipal userId: String): ApiResponse<*> {
        val level = codingQuestService.getLevel(userId)
        return ApiResponse.success(mapOf("level" to level))
    }
}
