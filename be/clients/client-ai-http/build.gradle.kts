dependencies {
    implementation(project(":core:core-domain"))

    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-jackson")

    // tools.jackson 코어(jackson-databind 등)는 Spring Boot 4 관리 의존성(jackson-bom 3.x)으로
    // 클래스패스에 이미 존재하지만, BaseAiHttpAdapter가 reified `readValue<T>` 확장 함수를 컴파일
    // 타임에 직접 참조하므로 Kotlin 모듈은 명시 선언 필요(버전은 BOM이 관리).
    implementation("tools.jackson.module:jackson-module-kotlin")

    // MockRestServiceServer(BaseAiHttpAdapterErrorMappingTest) 등 spring-test 유틸은
    // 루트 build.gradle.kts의 subprojects 블록이 이미 testImplementation으로 제공한다.
}
