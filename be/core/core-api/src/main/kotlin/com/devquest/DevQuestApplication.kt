package com.devquest

import com.devquest.migration.MigrationLauncher
import com.devquest.migration.MigrationMode
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import kotlin.system.exitProcess

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
    // 첫 인자가 migrate/validate/repair 면 웹 서버·스케줄러 없이 DB 마이그레이션만 하고 종료한다
    // (Fly release_command = "migrate"). 알 수 없는 맨 단어 인자는 parse 가 예외로 거부한다.
    if (MigrationMode.parse(args) != null) {
        exitProcess(MigrationLauncher.run(args))
    }
    runApplication<DevQuestApplication>(*args)
}
