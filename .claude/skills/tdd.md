# SKILL: Test-Driven Development (TDD)

**트리거:** 모든 기능 구현, 모든 버그 수정

## 철의 법칙

```
실패하는 테스트 없이는 프로덕션 코드 없다
```

테스트 전에 코드를 썼다면? **삭제하고 처음부터.**

## RED → GREEN → REFACTOR

### RED — 실패하는 테스트 작성

```kotlin
// BE 예시 (Kotlin)
@Test
fun `재시도 3회 후 성공한다`() {
    var attempts = 0
    // 실패하는 동작 정의
    // assertions
}
```

```typescript
// FE 예시 (TypeScript)
test('renders error state when fetch fails', () => {
  // 실패하는 동작 정의
  // assertions
});
```

### Verify RED — 반드시 실패 확인 (절대 생략 불가)

```bash
# BE
cd be && ./gradlew test --tests "com.devquest...ClassName.테스트명"

# FE
cd fe && npm test -- --testNamePattern="테스트명"
```

테스트가 통과하면 → 기존 동작을 테스트하는 것 → 테스트 수정 필요

### GREEN — 최소한의 코드

- 테스트를 통과하는 가장 단순한 코드만
- 추가 기능, 다른 코드 리팩토링 금지

### Verify GREEN — 통과 확인 (절대 생략 불가)

### REFACTOR — 정리

- 중복 제거, 이름 개선
- 테스트는 계속 GREEN 유지

## 프로젝트 특유 금지 패턴

```
BE: Evaluator 테스트에 @Mock/@InjectMocks 금지
    → RETURNS_DEEP_STUBS 패턴 사용

BE: core-domain에 Spring 어노테이션 금지
    → 순수 Kotlin으로 테스트 가능해야 함
```

## 자기 검증 체크리스트 (완료 전)

- [ ] 모든 새 함수/메서드에 테스트 존재
- [ ] 각 테스트가 실패하는 것을 직접 확인함
- [ ] 테스트가 예상한 이유로 실패함 (기능 없음, 오타 아님)
- [ ] 최소한의 코드로 통과
- [ ] 모든 테스트 통과
- [ ] 경고, 에러 없는 깨끗한 출력
