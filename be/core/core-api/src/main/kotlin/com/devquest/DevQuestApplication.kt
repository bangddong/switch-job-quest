package com.devquest

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.devquest"])
class DevQuestApplication

fun main(args: Array<String>) {
    runApplication<DevQuestApplication>(*args)
}
