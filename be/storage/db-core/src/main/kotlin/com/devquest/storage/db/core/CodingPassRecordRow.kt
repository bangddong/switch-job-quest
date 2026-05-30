package com.devquest.storage.db.core

import java.time.LocalDateTime

data class CodingPassRecordRow(
    val problemId: Long,
    val difficulty: String,
    val createdAt: LocalDateTime
)
