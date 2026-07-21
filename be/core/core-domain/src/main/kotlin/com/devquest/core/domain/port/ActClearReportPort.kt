package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.ActClearReportResult
import com.devquest.core.domain.port.ai.AiEvaluatorPort

interface ActClearReportPort : AiEvaluatorPort {
    fun generate(actId: Int, actTitle: String, questScores: Map<String, Int>): ActClearReportResult
}
