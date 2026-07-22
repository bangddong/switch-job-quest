package com.devquest.ai.controller.request

data class Judge0ExecuteRequest(
    val sourceCode: String,
    val languageId: Int,
    val stdin: String,
    val expectedOutput: String,
)
