dependencies {
    implementation(project(":core:core-domain"))

    // AI provider
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")

    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("tools.jackson.module:jackson-module-kotlin")

    // RC2에서 anthropic-java-client-okhttp가 transitive에서 제거됨 — 통합 테스트에서 직접 사용
    testImplementation("com.anthropic:anthropic-java-client-okhttp:2.35.0")
}

// 일반 test에서 integration 태그 제외
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

// 실제 AI 호출 통합 테스트 태스크
tasks.register<Test>("integrationTest") {
    description = "실제 Anthropic API를 호출하는 통합 테스트 (ANTHROPIC_API_KEY 필요)"
    group = "verification"
    // 일반 test 태스크와 같은 소스셋/클래스패스 재사용
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
    }
    environment("ANTHROPIC_API_KEY", System.getenv("ANTHROPIC_API_KEY") ?: "")
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
