plugins {
    kotlin("jvm")
    kotlin("plugin.spring") apply false
    kotlin("plugin.jpa") apply false
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
}

allprojects {
    group = "${property("projectGroup")}"
    version = "${property("applicationVersion")}"

    repositories {
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
        maven { url = uri("https://repo.spring.io/snapshot") }
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.jetbrains.kotlin.plugin.jpa")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")

    dependencyManagement {
        imports {
            mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
        }
    }

    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
        annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of("${property("javaVersion")}")
        }
    }

    kotlin {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict")
        }
    }

    tasks.named<Jar>("bootJar").configure {
        enabled = false
    }

    tasks.named<Jar>("jar").configure {
        enabled = true
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        // -Duser.timezone=Asia/Seoul: CI(우분투 러너, 기본 UTC)와 prod(Dockerfile ENTRYPOINT에서
        // 동일 설정)가 같은 zone에서 돌게 해서 "CI는 초록인데 prod에서만 다르게 동작"을 막는다.
        // fix/timezone-consistency — 회귀 가드: TimezoneConsistencyTest.
        jvmArgs("-Dfile.encoding=UTF-8", "-Duser.timezone=Asia/Seoul")
        finalizedBy(tasks.named("jacocoTestReport"))

        // Testcontainers(Java)는 `docker context`를 읽지 않고 DOCKER_HOST 환경변수 또는 기본 소켓
        // (/var/run/docker.sock)만 본다. macOS에서 Docker Desktop 대신 colima를 쓰면 기본 소켓이
        // 없어 Docker가 멀쩡히 떠 있어도 "Could not find a valid Docker environment"로 실패한다
        // (`docker` CLI는 context를 읽으므로 정상 동작해 보여 헷갈리기 쉽다 — 실측: FlywayMigrationIntegrationTest).
        // CI(ubuntu-latest)는 표준 소켓을 그대로 쓰므로 아래 조건은 항상 거짓 → 무해하다.
        if (System.getenv("DOCKER_HOST") == null && !File("/var/run/docker.sock").exists()) {
            val colimaSocket = File(System.getProperty("user.home"), ".colima/default/docker.sock")
            if (colimaSocket.exists()) {
                environment("DOCKER_HOST", "unix://${colimaSocket.absolutePath}")
                // Ryuk(테스트 후 컨테이너 정리용 리소스 리퍼)은 자신을 컨테이너로 띄우면서 도커 소켓
                // 파일을 자기 안에 바인드 마운트하려 하는데, colima는 소켓을 VM 안에서 포워딩하는
                // 구조라 이 마운트가 "mkdir ...docker.sock: operation not supported"로 실패한다
                // (실측: 이 조건 없이 돌리면 Ryuk 컨테이너 기동 단계에서 InternalServerErrorException).
                // 표준 소켓(/var/run/docker.sock, 예: CI ubuntu-latest·Docker Desktop)에서는 위
                // 분기 자체를 안 타므로 Ryuk은 그대로 켜진 채 동작한다 — 여기서만 끈다.
                // 컨테이너 정리는 대신 각 테스트의 @AfterAll에서 수동으로 한다(예: FlywayMigrationIntegrationTest).
                environment("TESTCONTAINERS_RYUK_DISABLED", "true")
            }
        }
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}
