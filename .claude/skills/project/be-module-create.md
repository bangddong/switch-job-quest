---
description: BE 새 Gradle 모듈을 추가할 때 사용. 모듈 카테고리 결정, build.gradle.kts 생성, settings.gradle.kts 등록, core-api 연결까지 안내.
---

# BE 모듈 생성 가이드

## 1. 모듈 카테고리 결정

| 카테고리 | 경로 | 역할 | 허용 의존 |
|---------|------|------|----------|
| core | `be/core/[모듈명]` | 도메인 로직, 열거형 | core-enum만 (core-domain), 없음 (core-enum) |
| storage | `be/storage/[모듈명]` | DB 어댑터 | core-domain, core-enum |
| clients | `be/clients/[모듈명]` | 외부 서비스 어댑터 | core-domain |
| support | `be/support/[모듈명]` | 횡단 관심사 | 독립 (다른 모듈 의존 금지) |
| tests | `be/tests/[모듈명]` | 테스트 전용 | 필요한 모듈 |

## 2. 디렉토리 생성

```
be/[카테고리]/[모듈명]/
  build.gradle.kts
  src/main/kotlin/com/devquest/[카테고리 패키지]/[모듈 패키지]/
  src/main/resources/        (설정 파일 필요 시)
  src/test/kotlin/com/devquest/...
```

패키지 매핑:
- core → `com.devquest.core.[모듈명]`
- storage → `com.devquest.storage.[모듈명]`
- clients → `com.devquest.client.[모듈명]`
- support → `com.devquest.support.[모듈명]`

## 3. build.gradle.kts 작성

```kotlin
dependencies {
    // 카테고리별 허용 의존만 추가
    implementation(project(":core:core-domain"))  // storage, clients만

    // 모듈 고유 의존성
    // 예: implementation("org.springframework.boot:spring-boot-starter-data-jpa")
}
```

루트 build.gradle.kts에서 공통 플러그인(Kotlin, Spring Boot, JPA)이 이미 적용되므로 중복 선언 금지.

## 4. settings.gradle.kts 등록

`be/settings.gradle.kts`의 `include()` 블록에 추가:

```kotlin
include(
    // ... 기존 모듈들
    "[카테고리]:[모듈명]",
)
```

## 5. core-api에 의존성 추가

`be/core/core-api/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":[카테고리]:[모듈명]"))
}
```

## 6. 설정 파일 (필요 시)

`src/main/resources/[모듈명].yml` 생성 후 `application.yml`에서 import:

```yaml
spring:
  config:
    import:
      - classpath:[모듈명].yml
```

## 검증 체크리스트

- [ ] 의존성 규칙 위반 없음 (어댑터 간 직접 의존 금지)
- [ ] settings.gradle.kts에 등록됨
- [ ] core-api에서 의존성 추가됨
- [ ] `./gradlew :core:core-api:compileKotlin` 성공
