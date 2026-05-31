package com.devquest.core.api.controller.v1

import com.devquest.core.api.controller.v1.request.UserEmailRequestDto
import com.devquest.core.domain.DailyMailScheduler
import com.devquest.core.domain.UserEmailService
import com.devquest.core.support.response.ApiResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/user")
class UserEmailController(
    private val userEmailService: UserEmailService,
    private val dailyMailScheduler: DailyMailScheduler,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PutMapping("/email")
    fun saveEmail(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: UserEmailRequestDto,
    ): ApiResponse<*> {
        userEmailService.saveEmail(userId, request.email)
        return ApiResponse.success(mapOf("email" to request.email))
    }

    @GetMapping("/email")
    fun getEmail(
        @AuthenticationPrincipal userId: String,
    ): ApiResponse<*> {
        return ApiResponse.success(mapOf("email" to userEmailService.getEmail(userId)))
    }

    @PostMapping("/email/test-mail")
    fun testMail(@AuthenticationPrincipal userId: String): ApiResponse<*> {
        dailyMailScheduler.sendDailyTechInterviewMail()
        return ApiResponse.success(mapOf("message" to "발송 트리거 완료"))
    }
}
