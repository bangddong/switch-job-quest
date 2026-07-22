package com.devquest.ai.controller.request

data class EssayEvaluateRequest(
    val dissatisfactions: List<String>,
    val goals: List<String>,
    val fiveYearVision: String,
)
