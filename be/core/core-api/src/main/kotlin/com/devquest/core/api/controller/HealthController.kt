package com.devquest.core.api.controller

import com.devquest.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {

    @GetMapping("/health")
    fun health(): ApiResponse<String> {
        return ApiResponse.success("DevQuest API is running")
    }
}
