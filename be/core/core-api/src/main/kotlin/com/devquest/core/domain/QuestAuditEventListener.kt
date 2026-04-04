package com.devquest.core.domain

import com.devquest.core.domain.event.QuestEvaluatedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class QuestAuditEventListener {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun on(event: QuestEvaluatedEvent) {
        log.info(
            "Quest evaluated: userId={}, questId={}, score={}, grade={}, passed={}, xp={}",
            event.userId, event.questId, event.score, event.grade, event.passed, event.earnedXp
        )
    }
}
