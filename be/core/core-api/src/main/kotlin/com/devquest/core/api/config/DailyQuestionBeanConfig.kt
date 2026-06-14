package com.devquest.core.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DailyQuestionBeanConfig {

    @Bean
    fun dailyQuestionProperties(
        @Value("\${devquest.daily-question.tech-stack}") techStack: String,
    ): DailyQuestionProperties = DailyQuestionProperties(techStack = techStack)
}
