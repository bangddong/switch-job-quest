package com.devquest.core.domain.port

import com.devquest.core.domain.model.DailyQuestionContent
import java.time.LocalDate

interface DailyQuestionContentPort {
    fun findToday(mailType: String, date: LocalDate): DailyQuestionContent?
    fun save(content: DailyQuestionContent): DailyQuestionContent
    fun findQuestionsSince(mailType: String, since: LocalDate): List<String>
}
