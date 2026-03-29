# GitHub Copilot Instructions — Switch Job Quest (DevQuest)

## Project Overview
5년차 백엔드 개발자의 이직 준비를 RPG 퀘스트로 구성한 풀스택 프로젝트.
모노레포 구조: `be/` (Kotlin + Spring Boot) + `fe/` (React 19 + TypeScript + Vite)

---

## Code Review Guidelines

### [아키텍처] 모듈 의존성 (BE)

반드시 아래 규칙을 준수하는지 확인할 것:

| 모듈 | 허용 의존 |
|------|----------|
| core-enum | 없음 |
| core-domain | core-enum만 |
| core-api | 모든 모듈 |
| db-core | core-domain, core-enum |
| client-ai | core-domain |

- `core-domain`에 Spring 어노테이션(`@Component`, `@Service` 등) 사용 금지
- 어댑터 간 직접 의존 금지 (db-core ↔ client-ai)
- Port는 `core-domain/port/`에 순수 인터페이스로 위치해야 함

### [아키텍처] Port & Adapter 패턴 준수 (BE)

- **Port**: `core-domain/port/` — Spring 의존 없는 순수 인터페이스
- **AI Adapter**: `client-ai/evaluator/` — `@Component`, `ChatClient` 주입, Port 구현체
- **DB Adapter**: `db-core/adapter/` — `@Component`, Repository 주입, `toDomain()`/`toEntity()` 확장함수
- Service는 구체 클래스가 아닌 Port 인터페이스를 주입받아야 함

### [코드 품질] Kotlin 스타일 (BE)

- `val` 선호, `var`는 Entity 변경 필드에만 허용
- `!!` (non-null assertion) 사용 지양 — `?.`, `?:` 활용
- 확장함수로 매핑: `Entity.toDomain()`, `Domain.toEntity()`
- data class에 기본값 제공
- DTO: `@field:NotBlank`, `@field:Size` 등 Bean Validation 어노테이션 사용

### [코드 품질] React/TypeScript 스타일 (FE)

- **named export 강제** — `export default` 사용 금지
- **인라인 스타일** (`React.CSSProperties`) 사용 — CSS 모듈, Tailwind 사용 금지
- **상태**: `useState` only — Redux, Context, Zustand 등 외부 상태관리 사용 금지
- **Props 인터페이스**: `interface [Name]Props { ... }` 명시적 정의 필수
- 데이터 흐름: App.tsx에서 props drilling + callback lifting 패턴 유지

### [보안] API 및 인증

- Controller에서 userId를 직접 받을 경우 — 인증 없이 타인의 데이터 접근 가능한지 확인
- AI 평가 결과를 클라이언트에 그대로 노출 시 민감 정보 포함 여부 확인
- 환경변수(`application.yml`, `client-ai.yml`)에 시크릿이 하드코딩되지 않았는지 확인

### [성능] AI 호출

- AI 평가 엔드포인트는 응답 시간이 길 수 있으므로 타임아웃 설정 여부 확인
- 동일한 입력에 대한 중복 AI 호출 방지 로직이 있는지 확인

---

## Commit Convention

```
<type>(<scope>): <message>
```
- type: feat, fix, chore, docs, refactor, test, style
- scope: be, fe, 또는 생략
- message: 영어, 소문자 시작, 현재형

---

## What to Flag

1. 모듈 의존성 규칙 위반
2. core-domain에 Spring 어노테이션 추가
3. FE에서 default export 사용
4. FE에서 외부 상태관리 라이브러리 도입
5. 환경변수 없이 하드코딩된 API 키 또는 URL
6. Port를 거치지 않고 어댑터를 직접 호출하는 코드
