package com.devquest.core.domain.port

import com.devquest.core.domain.model.AiCallLog

interface AiCallLogPort {
    fun record(log: AiCallLog)
}
