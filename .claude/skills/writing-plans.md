# SKILL: Writing Plans (구현 계획)

**트리거:** 설계 승인 후, 멀티스텝 작업 시작 전

**저장 위치:** `.claude/plans/YYYY-MM-DD-<feature-name>.md`

## 계획 헤더 (필수)

```markdown
# [기능명] 구현 계획

**Goal:** [한 문장 목표]
**Architecture:** [2-3문장 접근법]
**Tech Stack:** [주요 기술/라이브러리]
```

## 태스크 구조 (2~5분 단위)

```markdown
### Task N: [컴포넌트명]

**Files:**
- Create: `exact/path/to/file.kt`
- Modify: `exact/path/to/existing.kt:123-145`
- Test: `be/src/test/kotlin/.../test.kt`

- [ ] Step 1: 실패하는 테스트 작성
- [ ] Step 2: 테스트 실행하여 실패 확인
      Run: `cd be && ./gradlew test --tests "...ClassName"`
      Expected: FAIL with "..."
- [ ] Step 3: 최소한의 구현 코드 작성
- [ ] Step 4: 테스트 실행하여 통과 확인
- [ ] Step 5: 커밋
      `git commit -m "feat(be): ..."`
```

## 절대 금지

- "TBD", "TODO", "나중에 구현" 같은 placeholder
- "위와 유사하게" (코드를 그대로 반복할 것)
- 실제 코드 없이 설명만 있는 단계

## 완료 후

계획 파일을 커밋하고 → `executing-plans` 스킬로 전환
