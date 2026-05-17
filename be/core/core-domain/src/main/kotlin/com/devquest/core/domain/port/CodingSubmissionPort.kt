package com.devquest.core.domain.port

interface CodingSubmissionPort {
    fun save(userId: String, problemId: Long, language: String, userCode: String, passed: Boolean, judgeResult: String): Long
}
