dependencies {
    implementation(project(":core:core-domain"))

    // AI provider
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")

    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
}
