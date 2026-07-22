package com.devquest.ai.controller.request

data class CodingHintGetRequest(
    val problemId: Long,
    val title: String,
    val description: String,
    val hintLevel: Int,
)
