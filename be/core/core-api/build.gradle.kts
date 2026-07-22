tasks.named<Jar>("bootJar").configure {
    enabled = true
}

tasks.named<Jar>("jar").configure {
    enabled = false
}

dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":core:core-domain"))
    implementation(project(":support:logging"))
    implementation(project(":support:monitoring"))
    implementation(project(":storage:db-core"))
    implementation(project(":clients:client-ai"))

    testImplementation(project(":tests:api-docs"))

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
