// daily-core: 데일리 기술 면접 질문 "생성" 책임을 담는 라이브러리 모듈 (Phase 2 Stage B-1).
//
// core-domain 포트만 의존한다(client-ai와 동일한 패턴) — 어댑터 배선은 각 조립(core-api)이 한다.
// @Service/@Value 등 Spring 어노테이션은 여기서 허용된다(금지 규칙은 core-domain 한정).
// core-api 고유 개념(ApiResponse, CoreException 등)에는 의존하지 않는다.
// bootJar/jar 활성화 여부는 root build.gradle.kts의 subprojects 기본값(jar만 활성화)을 그대로 쓴다.
//
// ⚠️ 알려진 한계 (기록용, 고치지 않음) — split package: 이 모듈의 클래스는 패키지를
// `com.devquest.core.domain`으로 그대로 옮겼다(Stage B-1 이동 전 core-api의 패키지와 동일).
// core-api에도 같은 패키지 이름을 쓰는 파일이 12개 있다(`CodingQuestService`·`MailService`·
// `DailyQuestionService` 등) — 두 모듈이 정확히 같은 패키지를 나눠 갖는 split package 상태다.
// 이유: import 변경 범위를 최소화하기 위한 보수적 선택(패키지를 `com.devquest.daily` 등으로
// 바꾸면 core-api 쪽 소비 파일들의 import도 함께 바뀌어야 했다). 컴파일·컴포넌트 스캔(둘 다
// `com.devquest` 하위 전체 스캔)에는 문제가 없지만 모듈 경계로서는 냄새다 — `internal` 가시성이
// 모듈을 넘지 못하고, 장래 JPMS·정적분석 도구에서 걸릴 수 있다. 바로잡으려면: 이 모듈의 패키지를
// `com.devquest.daily`(ai-api가 `com.devquest.ai`를 쓰는 것과 같은 패턴)로 바꾸고, core-api의
// `DailyQuestionService`·`DailyMailScheduler`의 import를 함께 갱신해야 한다.

dependencies {
    implementation(project(":core:core-domain"))

    // @Service, @Value 등 최소한의 Spring 지원.
    implementation("org.springframework.boot:spring-boot-starter")

    // DataIntegrityViolationException(org.springframework.dao) — F-3 UNIQUE 충돌 복구 경로에서 사용.
    implementation("org.springframework:spring-tx")
}
