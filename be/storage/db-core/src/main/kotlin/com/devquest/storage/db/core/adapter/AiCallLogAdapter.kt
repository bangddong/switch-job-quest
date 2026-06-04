package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.model.AiCallLog
import com.devquest.core.domain.port.AiCallLogPort
import com.devquest.storage.db.core.AiCallLogEntity
import com.devquest.storage.db.core.AiCallLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AiCallLogAdapter(
    private val repository: AiCallLogRepository,
) : AiCallLogPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun record(log: AiCallLog) {
        try {
            repository.save(
                AiCallLogEntity(
                    evaluatorName = log.evaluatorName,
                    modelName = log.modelName,
                    inputTokens = log.inputTokens,
                    outputTokens = log.outputTokens,
                    cacheReadTokens = log.cacheReadTokens,
                    cacheCreationTokens = log.cacheCreationTokens,
                    latencyMs = log.latencyMs,
                    success = log.success,
                )
            )
        } catch (e: Exception) {
            this.log.warn("AiCallLog 저장 실패 (무시): {}", e.message)
        }
    }
}
