---
description: "Use /review to trigger a senior-level code review on staged changes or the latest commit. Checks BE/FE architecture compliance, patterns, and conventions."
user_invocable: true
---

# Senior Code Review

스테이지된 변경(`git diff --cached`)이 있으면 그것을, 없으면 최근 커밋(`git diff HEAD~1`)을 리뷰한다.

## 리뷰 절차

### 1. 변경 범위 파악

```bash
# 스테이지된 변경 확인
git diff --cached --stat
# 없으면 최근 커밋
git diff HEAD~1 --stat
```

변경된 파일을 BE/FE로 분류하고, 각각의 체크리스트를 적용한다.

### 2. BE 체크리스트

#### 아키텍처 위반 (FAIL)
- [ ] 어댑터 간 직접 의존 (db-core ↔ client-ai)
- [ ] Port 인터페이스에 Spring 어노테이션 (`@Component`, `@Service` 등)
- [ ] core-domain에 Spring/JPA 의존성
- [ ] Service에서 구체 Adapter 클래스 직접 주입 (Port 인터페이스 대신)

#### 의존성 규칙 위반 (FAIL)
- [ ] core-enum: 다른 모듈 의존
- [ ] core-domain: core-enum 외 의존
- [ ] db-core: client-ai 의존
- [ ] client-ai: db-core 의존

#### 패턴 준수 (WARN)
- [ ] DTO에 `@field:` 접두사 검증 미사용
- [ ] Controller에서 `ApiResponse` 래퍼 미사용
- [ ] AI 평가기에서 프롬프트 구조 미준수 (컨텍스트→입력→기준→JSON)
- [ ] Domain Model에 기본값 누락
- [ ] Adapter에 `toDomain`/`toEntity` 매핑 누락

#### Kotlin 스타일 (WARN)
- [ ] 불필요한 `var` 사용 (Entity 변경 필드 제외)
- [ ] `!!` 사용
- [ ] 로거 미사용 또는 println 사용

### 3. FE 체크리스트

#### 구조 위반 (FAIL)
- [ ] CSS 모듈 / styled-components / Tailwind 사용
- [ ] Context API / Redux / 외부 상태관리 사용
- [ ] 클래스 컴포넌트 사용
- [ ] default export 사용

#### 패턴 준수 (WARN)
- [ ] Props 인터페이스 미정의
- [ ] 배럴 익스포트(index.ts) 미등록
- [ ] API 래퍼가 `callAiCheck<T>` 미사용
- [ ] 타입 파일 위치 부적절 (전역 타입이 feature에, 또는 반대)

#### 테마 준수 (WARN)
- [ ] 팔레트 외 색상 사용
- [ ] `'Courier New', monospace` 외 폰트
- [ ] 외부 UI 라이브러리 컴포넌트 사용

### 4. 공통 체크

#### 커밋 메시지 (WARN)
- [ ] `<type>(<scope>): <message>` 형식 미준수
- [ ] type: feat/fix/chore/docs/refactor/test/style 외 사용
- [ ] scope: be/fe 외 사용 또는 불일치

### 5. 판정 기준

| 등급 | 조건 |
|------|------|
| **PASS** | FAIL 항목 0개, WARN 항목 2개 이하 |
| **WARN** | FAIL 항목 0개, WARN 항목 3개 이상 |
| **FAIL** | FAIL 항목 1개 이상 |

### 6. 출력 형식

```
## Code Review Result: [PASS | WARN | FAIL]

### Issues Found
- [FAIL] 설명 (파일:라인)
- [WARN] 설명 (파일:라인)

### Good Practices
- 칭찬할 부분 나열

### Summary
한 줄 요약
```

아키텍처 위반은 반드시 구체적 파일과 라인을 지적한다.
칭찬 섹션은 항상 포함한다 — 좋은 패턴을 따른 부분을 인정한다.
