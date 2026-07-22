package com.devquest.ai.controller.request

data class CodingProblemGenerateRequest(
    val difficulty: String,
    val language: String,
    val category: String,
)
