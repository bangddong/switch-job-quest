package com.devquest.core.api.controller.v1

import com.devquest.core.domain.ProgressService
import com.devquest.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/progress")
class ProgressController(
    private val progressService: ProgressService
) {
    @GetMapping("/{userId}")
    fun getProgress(@PathVariable userId: String): ApiResponse<Map<String, Any>> {
        return ApiResponse.success(progressService.getProgress(userId))
    }
}
