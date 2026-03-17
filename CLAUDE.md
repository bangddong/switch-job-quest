# Switch Job Quest (DevQuest)

5년차 백엔드 개발자의 이직 준비를 RPG 퀘스트로 구성한 풀스택 프로젝트.
모노레포 구조 (`be/` + `fe/`). 제품 언어는 **한국어**.

## 커밋 컨벤션

```
<type>(<scope>): <message>
```

- **type**: feat, fix, chore, docs, refactor, test, style
- **scope**: be, fe, 또는 생략
- **message**: 영어, 소문자 시작, 현재형

예: `feat(be): add resume evaluator port and adapter`

---

## BE 아키텍처 (Kotlin + Spring Boot)

### 모듈 구조

```
be/
  core/core-enum        — 공유 enum (QuestStatus 등)
  core/core-domain      — Port 인터페이스 + Domain Model (순수 Kotlin, Spring 의존 없음)
  core/core-api          — Controller, Service, DTO, 에러처리 (bootJar 활성)
  storage/db-core        — JPA Entity, Repository, DB Adapter
  clients/client-ai      — AI 평가기 Adapter (Spring AI + ChatClient)
  support/logging        — 로깅 설정
  support/monitoring     — 모니터링 설정
  tests/api-docs         — API 문서 테스트
```

### 의존성 규칙 (반드시 준수)

| 모듈 | 허용 의존 |
|------|----------|
| core-enum | 없음 |
| core-domain | core-enum만 |
| core-api | 모든 모듈 (bootstrap) |
| db-core | core-domain, core-enum |
| client-ai | core-domain |
| support/* | 독립 |

**금지**: 어댑터 간 직접 의존 (db-core ↔ client-ai), core-domain에 Spring 어노테이션

### Port & Adapter 패턴

- **Port**: `core-domain/port/[Feature]EvaluatorPort` — 순수 인터페이스, Spring 의존 없음
- **Domain Model**: `core-domain/model/` — `data class`, 기본값 있는 프로퍼티
- **AI Adapter**: `client-ai/evaluator/` — `@Component`, `ChatClient` 주입, Port 구현
- **DB Adapter**: `db-core/adapter/` — `@Component`, Repository 주입, `toDomain()`/`toEntity()` 확장함수

### AI 평가기 프롬프트 구조

```
1. 컨텍스트 설명
2. 사용자 입력 (번호 매기기)
3. 채점 기준 (총 100점, 항목별 배점)
4. JSON 스키마 (예시 포함)
5. "반드시 다음 JSON 형식으로만 응답하세요 (다른 텍스트 금지):"
```

호출: `chatClient.prompt().user(prompt).call().entity(ResultClass::class.java)`

### Controller / Service / DTO 패턴

- **DTO**: `data class [Name]RequestDto` + `@field:NotBlank`, `@field:Size` 검증
- **Controller**: `@RestController`, `@RequestMapping("/api/v1/...")`, `ApiResponse<T>` 래퍼, 에러 시 `throw CoreException(ErrorType.XXX)`
- **Service**: Port 인터페이스 주입 (구체 클래스 X), `@Transactional`, `LoggerFactory.getLogger(javaClass)`
- **에러**: `ErrorType` enum → `ErrorCode` enum → `CoreException` → `ApiControllerAdvice`

### Kotlin 스타일

- `val` 선호, `var`는 Entity 변경 필드만
- 확장함수로 매핑 (`Entity.toDomain()`)
- data class에 기본값 제공
- null 안전성 (`?.`, `?:`, `!!` 지양)

### 설정

- `application.yml`에서 모듈별 yml import (`db-core.yml`, `client-ai.yml` 등)
- DB: H2 인메모리 (개발), HikariCP
- AI: `spring.ai.anthropic` 설정, claude-sonnet-4-5
- Virtual threads 활성화

---

## FE 아키텍처 (React 19 + TypeScript + Vite)

### Feature 구조

```
fe/src/
  app/App.tsx            — 루트 (View 상태, props drilling)
  features/
    [feature-name]/
      components/        — React 컴포넌트
      api/               — API 래퍼
      types/             — 로컬 타입
      constants/         — 설정 데이터
      index.ts           — 배럴 익스포트
  components/ui/         — 공유 UI (ScoreRing, ProgressBar, GradeTag)
  hooks/                 — 커스텀 훅 (useUserId)
  lib/                   — API 클라이언트 (callAiCheck<T>)
  types/                 — 전역 타입 (api.types.ts, quest.types.ts)
  styles/                — global.css
```

### 컴포넌트 패턴

- **함수형 컴포넌트** + `named export` (default export 사용 안 함)
- **Props 인터페이스**: `interface [Name]Props { ... }`
- **인라인 스타일**: `React.CSSProperties` 객체 — CSS 모듈, styled-components, Tailwind 사용 안 함
- **상태**: `useState` only — Redux, Context, Zustand 등 외부 상태관리 사용 안 함
- **데이터 흐름**: App.tsx에서 props drilling + callback lifting

### 다크 테마 컬러 팔레트

| 용도 | 색상 |
|------|------|
| 배경 | `#060610` |
| 입력 배경 | `#0A0E1A` |
| 카드 배경 | `#0F172A` |
| 테두리 | `rgba(255,255,255,0.08)` |
| 주요 텍스트 | `#F8FAFC` / `#F1F5F9` |
| 보조 텍스트 | `#475569` |
| Teal (주요 액센트) | `#4ECDC4` |
| Purple | `#A78BFA` |
| Amber/Gold | `#F59E0B` |
| Green (성공) | `#10B981` |
| Red (실패) | `#EF4444` |
| Blue (학습) | `#60A5FA` |

폰트: `'Courier New', monospace`

### API 패턴

- `lib/apiClient.ts`: `callAiCheck<T>(endpoint, body, userId)` — fetch + `ApiResponse<T>` 파싱
- Feature별 래퍼: `features/[name]/api/` — 타입 안전 래퍼
- Vite 프록시: `/api` → `http://localhost:8080`

### TypeScript 규칙

- `strict: true`, `noUnusedLocals`, `noUnusedParameters`, `noUncheckedIndexedAccess`
- 경로 별칭: `@/*` → `src/*`
- 전역 타입은 `types/`, 피처 로컬 타입은 `features/[name]/types/`

---

## 빌드 & 실행

```bash
# BE
cd be && ./gradlew :core:core-api:bootRun

# FE
cd fe && npm install && npm run dev
```

- BE: http://localhost:8080
- FE: http://localhost:5173 (Vite 프록시로 BE 연결)
