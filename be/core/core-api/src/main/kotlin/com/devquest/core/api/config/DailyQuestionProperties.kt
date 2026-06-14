package com.devquest.core.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "devquest.daily-question")
data class DailyQuestionProperties(
    val techStack: String = "",
)
