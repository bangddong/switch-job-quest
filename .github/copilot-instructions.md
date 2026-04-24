# GitHub Copilot Instructions — Switch Job Quest (DevQuest)

**언어 규칙: 모든 리뷰 코멘트, 제안, 피드백은 반드시 한국어로 작성한다.**

이직 준비 RPG 앱. 모노레포: `be/` (Kotlin + Spring Boot) + `fe/` (React 19 + TypeScript + Vite)
퀘스트별 AI 평가(Anthropic API)를 핵심 기능으로 사용한다.

---

## 1. 코드 어시스턴트 (자동완성/제안 시 준수)

- BE: `core-domain`에 Spring 어노테이션(`@Component`, `@Service`) 사용 금지. 순수 Kotlin만.
- BE: Service는 Port 인터페이스를 주입받아야 한다. 구체 클래스(Adapter) 직접 주입 금지.
- BE: `!!` 사용 금지. `?.let`, `?:` 또는 `requireNotNull`로 대체할 것.
- FE: `export default` 사용 금지. named export만 허용.
- FE: 외부 상태관리(Redux, Zustand, Context) 도입 금지. `useState`만 사용.

## 2. PR 설명 생성

- PR 제목은 커밋 컨벤션을 따른다: `<type>(<scope>): <message>` (message는 한국어, 현재형)
- 변경 이유(Why)를 포함할 것. 이유가 자명한 경우 생략 가능. "무엇을 바꿨는지"만 나열하지 말 것.
- BE 변경 시: 영향받는 모듈 목록을 명시할 것 (core-domain / client-ai / db-core 등).
- FE 변경 시: 어떤 퀘스트(예: 1-1, 2-BOSS) UI에 영향을 주는지 명시할 것.
- AI 평가 엔드포인트 추가/변경 시: 연결된 퀘스트 ID와 평가 기준을 PR 본문에 기재할 것.

## 3. 테스트 작성

- AI Evaluator 테스트는 실제 API를 호출하지 않는다. `ChatClient`를 Mock으로 대체할 것.
- 테스트 파일은 구현 클래스와 동일한 패키지 경로에 위치시킬 것 (`*Test.kt`).
- 평가 결과 검증 시: score 범위(0~100), 필수 필드 존재 여부를 반드시 assert할 것.
- FE 컴포넌트 테스트 작성 시: Props 인터페이스 기준으로 케이스를 나눌 것.
- 경계값(빈 문자열, 최대 길이 초과)에 대한 DTO 검증 테스트를 포함할 것.

## 4. 도메인 컨텍스트

- 퀘스트 ID 형식: `{act}-{순번}` 또는 `{act}-BOSS` (예: `1-1`, `2-BOSS`).
- AI 평가는 항상 score(숫자)와 feedback(문자열) 필드를 포함한 result를 반환한다.
- 새 퀘스트 추가 시 `fe/src/features/quest-map/constants/questData.ts`와 BE Port/Adapter가 쌍으로 추가되어야 한다.
- 모듈 의존 방향: core-api → core-domain ← client-ai, db-core (도메인이 중심).
- userId는 현재 인증 없이 클라이언트에서 전달된다. userId 기반 데이터 격리가 없으므로 타인 데이터 접근 가능성을 항상 인지할 것.

## 5. PR 인라인 리뷰 코멘트 형식 (자동화 연동 — 반드시 준수)

PR 인라인 리뷰 코멘트 작성 시 아래 규칙을 따른다. 자동화 시스템이 이 형식을 기반으로 동작한다.

**코드 수정 제안 (줄 단위 변경이 가능한 경우):**
반드시 GitHub suggestion 블록을 사용한다.

````suggestion
// 개선된 코드를 여기에 작성
````

suggestion 블록이 감지되면 자동화 시스템이 해당 내용을 PR 브랜치에 직접 커밋한다.

**아키텍처/설계 제안 (코드 한 줄로 표현 불가한 경우):**
자연어로 작성한다. suggestion 블록 없이 자연어만 있는 코멘트는 이슈로 자동 등록된다.

> **중요:** suggestion 블록 없이 코드 수정을 제안하지 말 것. 자연어로만 된 코드 수정 제안은 아키텍처 제안으로 분류된다.
