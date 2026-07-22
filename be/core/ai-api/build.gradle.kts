tasks.named<Jar>("bootJar").configure {
    enabled = true
}

tasks.named<Jar>("jar").configure {
    enabled = false
}

dependencies {
    implementation(project(":core:core-domain"))
    // Phase 1 Task 1.1 — AI 평가자 구현체 부착. db-core는 절대 의존하지 않는다(AiCallLogPort는
    // 이 모듈의 AiCallLogObservabilityAdapter가 Task 1.3에서 이미 충족).
    implementation(project(":clients:client-ai"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}
