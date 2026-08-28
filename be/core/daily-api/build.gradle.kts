// daily-api: Phase 2 Stage B-2b — daily-core·client-ai-http·db-core를 조립해 단독으로 기동하는
// Spring Boot 앱을 만든다. core-api를 의존하지 않는다(결정 D-007) — ApiResponse/CoreException 등
// core-api 고유 개념은 이 모듈에서 쓸 수 없고, 이 모듈이 자체 응답 형식·에러 매핑을 갖는다.
//
// core:core-api의 testImplementation에 이 모듈을 추가하지 않는다(be/core/core-api/build.gradle.kts:9-33
// 참고 — 다른 앱 모듈을 core-api 테스트 클래스패스에 올리면 기존 테스트가 깨진다).

tasks.named<Jar>("bootJar").configure {
    enabled = true
}

// `jar`는 루트 build.gradle.kts의 subprojects 기본값(enabled = true)을 그대로 쓴다 — daily-api를
// project(...) 의존하는 다른 모듈이 없으므로 ai-api처럼 별도로 켤 필요가 없다(함정 ⑤ 참고).

dependencies {
    implementation(project(":core:core-domain"))
    implementation(project(":core:daily-core"))
    implementation(project(":clients:client-ai-http"))
    implementation(project(":storage:db-core"))

    // client-ai-http·db-core가 전이 의존을 `implementation` 스코프로만 선언해 daily-api의
    // compileClasspath로 새지 않는다(함정 ④) — 아래를 직접 선언해야 한다.
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    // Jackson 3 Kotlin 모듈 — BaseAiHttpAdapter(client-ai-http)가 reified `readValue<T>` 확장
    // 함수를 컴파일 타임에 직접 참조하므로 명시 선언 필요(core-api build.gradle.kts와 동일 이유).
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.bucket4j:bucket4j-core:8.10.1")
    // C-1 — k8s 매니페스트 계약(k8s/base/core-api.yaml)이 요구하는 readiness probe
    // (`/actuator/health/readiness`)를 충족하기 위해 추가. `/health`(liveness)는 이 모듈이 직접 만든
    // 상수 컨트롤러라 actuator 없이도 동작하지만, readiness는 실제 DB 상태를 반영해야 하므로
    // actuator의 DataSourceHealthIndicator가 필요하다.
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // readiness 회귀 테스트(DailyHealthReadinessTest)용 — Spring Boot 4는 웹mvc 테스트 지원(`@AutoConfigureMockMvc`)을
    // `spring-boot-test-autoconfigure`에서 별도 아티팩트로 분리했다(core-api build.gradle.kts와 동일 이유,
    // 패키지는 `org.springframework.boot.webmvc.test.autoconfigure`로 이동, 실측 확인).
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
}
