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

    override fun record(entry: AiCallLog) {
        try {
            repository.save(
                AiCallLogEntity(
                    evaluatorName = entry.evaluatorName,
                    modelName = entry.modelName,
                    inputTokens = entry.inputTokens,
                    outputTokens = entry.outputTokens,
                    cacheReadTokens = entry.cacheReadTokens,
                    cacheCreationTokens = entry.cacheCreationTokens,
                    latencyMs = entry.latencyMs,
                    success = entry.success,
                )
            )
        } catch (e: Exception) {
            log.warn("AiCallLog 저장 실패 (무시)", e)
        }
    }
}
