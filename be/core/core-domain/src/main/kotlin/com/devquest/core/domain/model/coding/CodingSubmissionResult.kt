package com.devquest.core.domain.model.coding

data class CodingSubmissionResult(
    val problemId: Long = 0,
    val passed: Boolean = false,
    val stdout: String = "",
    val stderr: String = "",
    val message: String = ""
)
