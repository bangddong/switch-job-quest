package com.devquest.ai.controller.request

data class SystemDesignEvaluateRequest(
    val problemStatement: String,
    val architectureDescription: String,
    val considerations: List<String>,
)
