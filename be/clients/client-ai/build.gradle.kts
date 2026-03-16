dependencies {
    implementation(project(":core:core-domain"))

    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
}
