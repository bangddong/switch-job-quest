package com.devquest.core.domain.port

data class Judge0Result(
    val stdout: String = "",
    val stderr: String = "",
    val status: String = "",
    val passed: Boolean = false
)

interface Judge0Port {
    fun execute(sourceCode: String, languageId: Int, stdin: String): Judge0Result
}
