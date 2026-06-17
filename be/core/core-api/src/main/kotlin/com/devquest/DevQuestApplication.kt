package com.devquest

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication(
    scanBasePackages = ["com.devquest"],
    // micrometer-registry-otlp가 classpath에 있으면 auto-configured OtlpMeterRegistry 생성됨.
    // 수동 bean(OtlpMetricsConfig)만 사용하도록 직접 제외.
    // excludeName: core-api 모듈에 해당 클래스 의존성 없어 String으로 지정.
    excludeName = ["org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp.OtlpMetricsExportAutoConfiguration"],
)
class DevQuestApplication

fun main(args: Array<String>) {
    runApplication<DevQuestApplication>(*args)
}
