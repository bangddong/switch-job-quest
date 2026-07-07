package com.devquest.core.api.controller.v1

import com.devquest.core.api.controller.v1.request.ResumeRequestDto
import com.devquest.core.domain.ResumeService
import com.devquest.core.support.response.ApiResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/resume")
class ResumeController(
    private val resumeService: ResumeService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun getResume(
        @AuthenticationPrincipal userId: String,
    ): ApiResponse<*> {
        return ApiResponse.success(resumeService.getResume(userId))
    }

    @PutMapping
    fun saveResume(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: ResumeRequestDto,
    ): ApiResponse<*> {
        val result = resumeService.saveResume(userId, request.content)
        return ApiResponse.success(result)
    }
}
