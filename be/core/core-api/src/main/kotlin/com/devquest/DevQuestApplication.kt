package com.devquest

import com.devquest.core.api.config.DailyQuestionProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@EnableConfigurationProperties(DailyQuestionProperties::class)
@SpringBootApplication(scanBasePackages = ["com.devquest"])
class DevQuestApplication

fun main(args: Array<String>) {
    runApplication<DevQuestApplication>(*args)
}
