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
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation("org.springframework.boot:spring-boot-starter-validation")
}
