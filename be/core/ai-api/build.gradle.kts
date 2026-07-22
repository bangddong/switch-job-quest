tasks.named<Jar>("bootJar").configure {
    enabled = true
}

// Phase 1 Task 1.5 — 일반 jar를 다시 켠다. core-api가 parity 테스트 전용으로
// `testImplementation(project(":core:ai-api"))`를 갖는데(core/core-api/build.gradle.kts 참고),
// 일반 jar가 꺼져 있으면 그 project 의존이 소비할 아티팩트(default configuration output)가 없어
// "Unable to find the dependency" 경고와 함께 ai-api 클래스가 실제로 테스트 런타임 클래스패스에
// 오르지 못한다(컴파일은 되지만 컴포넌트 스캔 대상 클래스가 0개 — 실측 확인: AiCallLogPort 등
// NoSuchBeanDefinitionException). Fly 배포는 core-api의 bootJar만 단독으로 빌드하므로
// (be/Dockerfile 참고) ai-api의 jar 활성화는 그 빌드 그래프와 무관 — Fly 배포 불변식에 영향 없음.
tasks.named<Jar>("jar").configure {
    enabled = true
}

dependencies {
    implementation(project(":core:core-domain"))
    // Phase 1 Task 1.1 — AI 평가자 구현체 부착. db-core는 절대 의존하지 않는다(AiCallLogPort는
    // 이 모듈의 AiCallLogObservabilityAdapter가 Task 1.3에서 이미 충족).
    implementation(project(":clients:client-ai"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}
