tasks.named<Jar>("bootJar").configure {
    enabled = true
}

tasks.named<Jar>("jar").configure {
    enabled = false
}

// Phase 1 Task 1.5 — parity 테스트 전용 별도 소스셋.
//
// 왜 일반 `test` 소스셋에 두지 않는가(실측으로 발견한 함정): parity 테스트는 core-api 안에서 실제
// ai-api 클래스(`AiApiApplication` 등)를 함께 띄워야 해서 `testImplementation(project(":core:ai-api"))`가
// 필요하다. 처음에는 이걸 그냥 `testImplementation`에 추가했는데, 그 순간 core-api의 **일반** 테스트
// 클래스패스에도 ai-api 클래스가 함께 올라가 버렸다. `DevQuestApplication`(core-api의 진짜
// `@SpringBootApplication`, `scanBasePackages=["com.devquest"]`)을 쓰는 기존 전체 컨텍스트 테스트들
// (`ApplicationContextTest`·`AiTransportInprocessSwitchTest` 등)이 그 넓은 스캔 범위 안에 이제 막
// 함께 올라온 ai-api의 `@Component`들까지 주워버려 `AiCallLogPort` 빈이 2개(코어 db-core용 vs
// ai-api 관측용)로 늘어나 애매해지는 등 43개 테스트가 실측 회귀로 깨졌다. `@SpringBootApplication`은
// `excludeFilters`를 노출하지 않아 `DevQuestApplication` 쪽에서 ai-api 패키지를 배제할 방법이 없다
// (프로덕션 부트스트랩 애노테이션을 통째로 갈아엎는 건 이 태스크 범위에 비해 과한 위험).
// → 정공법은 **클래스패스 자체를 분리**하는 것. `parityTest`라는 별도 소스셋을 만들어 ai-api 의존을
// 거기에만 넣으면, 일반 `test` 태스크(233개 기존 테스트)는 ai-api를 전혀 모르는 채로 예전과 동일하게
// 돌고, parity 테스트는 자기만의 격리된 클래스패스에서 돈다. 프로덕션 `main` 소스셋은 애초에 ai-api를
// 의존하지 않으므로 bootJar·Fly 배포 산출물에도 무관하다.
sourceSets {
    // 관례상 `src/parityTest/kotlin`·`src/parityTest/resources`가 기본으로 잡히므로 srcDir를
    // 별도로 추가하지 않는다(추가하면 리소스가 두 번 잡혀 `processParityTestResources`가
    // "duplicate entry"로 실패한다 — 실측 확인).
    create("parityTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val parityTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations["testImplementation"])
}
configurations["parityTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

val parityTest = tasks.register<Test>("parityTest") {
    description = "Phase 1 Task 1.5 — 실제 ai-api 프로세스 vs core HTTP 어댑터 parity 테스트(기본 test/check와 분리)"
    group = "verification"
    testClassesDirs = sourceSets["parityTest"].output.classesDirs
    classpath = sourceSets["parityTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}

dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":core:core-domain"))
    implementation(project(":support:logging"))
    implementation(project(":support:monitoring"))
    implementation(project(":storage:db-core"))
    implementation(project(":clients:client-ai"))

    testImplementation(project(":tests:api-docs"))

    // parity 테스트 전용 — 위 소스셋 설명 참고. 일반 testImplementation이 아니라 parityTestImplementation
    // 이어야 233개 기존 테스트의 클래스패스가 오염되지 않는다.
    parityTestImplementation(project(":core:ai-api"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    // Jackson 3 Kotlin 모듈. client-ai가 이미 같은 좌표를 implementation으로 갖고 있지만 그 스코프는
    // 소비 모듈(core-api)의 compileClasspath로 새지 않는다(runtimeClasspath에서만 보임) — Task 1.4b:
    // BaseAiHttpAdapter가 reified `readValue<T>` 확장 함수를 컴파일 타임에 직접 참조하므로 명시 선언 필요.
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    implementation("com.bucket4j:bucket4j-core:8.10.1")
}
