# BE 아키텍처 규칙 (Kotlin + Spring Boot)

## 모듈 의존성 규칙 (반드시 준수)

| 모듈 | 허용 의존 |
|------|----------|
| core-enum | 없음 |
| core-domain | core-enum만 |
| core-api | 모든 모듈 (bootstrap) |
| db-core | core-domain, core-enum |
| client-ai | core-domain |
| client-ai-http | core-domain |
| daily-core | core-domain (+ Spring 최소) |
| support/* | 독립 |

**client-ai-http가 core-api를 의존하면 안 되는 이유**: daily-api(계획 중, B-2b)처럼 core-api가 아닌
별도 조립 지점이 AI 평가 HTTP 어댑터를 재사용해야 한다. client-ai-http가 core-api를 의존하면
방향이 역전되어 daily-api가 core-api를 통해서만 이 어댑터에 닿게 되고, 애초에 core-api로부터
분리한 목적(daily-api의 독립적 부팅)이 무효화된다. `client-ai`(인프로세스 구현)와도 서로 의존하지
않는다 — 둘은 같은 Port의 형제 구현체다.

**daily-core가 core-api를 의존하면 안 되는 이유**: `DailyMailScheduler`(스케줄러 조립)와
`DailyQuestionService`(웹 조립) 양쪽이 이 모듈의 콘텐츠 생성 로직(`DailyQuestionGeneratorPort`
구현)을 공유해서 쓴다. core-api를 의존하면 방향이 역전되어 두 조립 지점이 서로를 통해서만
콘텐츠 생성 로직에 닿게 되고, 장래 daily-api처럼 core-api가 아닌 별도 조립이 등장할 때 이
모듈을 재사용할 수 없게 된다.

**금지**: 어댑터 간 직접 의존 (db-core ↔ client-ai), core-domain에 Spring 어노테이션

## Port & Adapter 패턴

- **Port**: `core-domain/port/[Feature]EvaluatorPort` — 순수 인터페이스, Spring 의존 없음
- **Domain Model**: `core-domain/model/` — `data class`, 기본값 있는 프로퍼티
- **AI Adapter**: `client-ai/evaluator/` — `@Component`, `ChatClient` 주입, Port 구현
- **DB Adapter**: `db-core/adapter/` — `@Component`, Repository 주입, `toDomain()`/`toEntity()` 확장함수

## Controller / Service / DTO 패턴

- **DTO**: `data class [Name]RequestDto` + `@field:NotBlank`, `@field:Size` 검증
- **Controller**: `@RestController`, `@RequestMapping("/api/v1/...")`, `ApiResponse<T>` 래퍼
- **Service**: Port 인터페이스 주입 (구체 클래스 X), `@Transactional`
- **에러**: `ErrorType` enum → `ErrorCode` enum → `CoreException` → `ApiControllerAdvice`

## 시크릿 관리 규칙 (보안 — 반드시 준수)

- **`application.yml`의 `${VAR:default}` fallback에 시크릿 절대 금지**
  - ✅ 허용: `${JWT_EXPIRATION_MS:2592000000}` (숫자, 공개 설정값)
  - ❌ 금지: `${GITHUB_CLIENT_SECRET:실제값}`, `${JWT_SECRET:실제값}`
- **로컬 시크릿은 `application-local.yml`에만 작성** — 이 파일은 `.gitignore`에 등록됨
- `application-local.yml`, `application-secret.yml`은 커밋 금지 (`.gitignore` 적용됨)
- 새 시크릿 추가 시: `application.yml`에 `${NEW_VAR}` 만 추가 → `application-local.yml`에 실제 값 작성
- **사용자가 대화 중 키 값을 알려주면 즉시 `application-local.yml`에 기입** — 대화 종료 후 복구 불가

## Kotlin 스타일

- `val` 선호, `var`는 Entity 변경 필드만
- 확장함수로 매핑 (`Entity.toDomain()`)
- data class에 기본값 제공
- null 안전성 (`?.`, `?:`, `!!` 지양)
