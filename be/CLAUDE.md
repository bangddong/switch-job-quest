# BE 아키텍처 규칙 (Kotlin + Spring Boot)

## 모듈 의존성 규칙 (반드시 준수)

| 모듈 | 허용 의존 |
|------|----------|
| core-enum | 없음 |
| core-domain | core-enum만 |
| core-api | 모든 모듈 (bootstrap) |
| db-core | core-domain, core-enum |
| client-ai | core-domain |
| support/* | 독립 |

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

## Kotlin 스타일

- `val` 선호, `var`는 Entity 변경 필드만
- 확장함수로 매핑 (`Entity.toDomain()`)
- data class에 기본값 제공
- null 안전성 (`?.`, `?:`, `!!` 지양)
