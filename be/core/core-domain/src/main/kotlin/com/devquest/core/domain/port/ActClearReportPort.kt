package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.ActClearReportResult

interface ActClearReportPort {
    fun generate(actId: Int, actTitle: String, questScores: Map<String, Int>): ActClearReportResult
}
