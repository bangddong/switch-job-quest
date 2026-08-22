// daily-core: 데일리 기술 면접 질문 "생성" 책임을 담는 라이브러리 모듈 (Phase 2 Stage B-1).
//
// core-domain 포트만 의존한다(client-ai와 동일한 패턴) — 어댑터 배선은 각 조립(core-api)이 한다.
// @Service/@Value 등 Spring 어노테이션은 여기서 허용된다(금지 규칙은 core-domain 한정).
// core-api 고유 개념(ApiResponse, CoreException 등)에는 의존하지 않는다.
// bootJar/jar 활성화 여부는 root build.gradle.kts의 subprojects 기본값(jar만 활성화)을 그대로 쓴다.

dependencies {
    implementation(project(":core:core-domain"))

    // @Service, @Value 등 최소한의 Spring 지원.
    implementation("org.springframework.boot:spring-boot-starter")

    // DataIntegrityViolationException(org.springframework.dao) — F-3 UNIQUE 충돌 복구 경로에서 사용.
    implementation("org.springframework:spring-tx")
}
