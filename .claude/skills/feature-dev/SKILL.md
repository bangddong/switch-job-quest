---
name: feature-dev
description: "BE + FE 기능 구현 팀을 조율하는 오케스트레이터. 새 퀘스트/기능 구현, BE API 추가, FE 연동, 풀스택 기능 개발 요청 시 반드시 이 스킬을 사용. 후속 작업: 기능 수정, 재구현, 부분 변경, 버그 수정 요청 시에도 이 스킬 사용."
---

# Feature Dev Orchestrator

BE → FE → QA를 순차 sub-agent로 조율하여 풀스택 기능을 구현하는 통합 스킬.

## 실행 모드: 순차 Sub-agent

## 에이전트 구성

| 에이전트 | 파일 | 역할 | 순서 |
|---------|------|------|------|
| be-feature-builder | `.claude/agents/be-feature-builder.md` | BE 구현 → API 스펙 반환 | 1 |
| fe-feature-builder | `.claude/agents/fe-feature-builder.md` | FE 구현 (BE 스펙 수신) | 2 |
| qa-reviewer | `.claude/agents/qa-reviewer.md` | 코드 리뷰 + 정합성 검토 | 3 |

## 워크플로우

### Phase 0: 컨텍스트 확인

- `.claude/CONTEXT.md` 읽기
- 열린 PR 있으면 → 이어서 진행
- 새 작업이면 → Phase 1으로

### Phase 1: 작업 분석

사용자 요청에서 파악:
- 어떤 퀘스트/기능인가? (questId, actId 포함)
- BE만? FE만? 풀스택?
- 브랜치 전략: `feat/[feature-name]` 단일 브랜치

### Phase 2: BE 구현 (Sub-agent 1)

```
Agent(
  subagent_type: "general-purpose",
  prompt: """
    [be-feature-builder.md 파일 전체 내용 포함]

    ## 이번 작업
    기능명: [기능명]
    questId: [id], actId: [id], XP: [값]

    ## 구현 내용
    [구체적인 구현 요청]

    ## 브랜치
    git fetch origin main
    git checkout -b feat/[feature-name] origin/main

    ## 완료 시 반환
    1. 구현한 파일 목록
    2. API 스펙 (엔드포인트, Request/Response 타입)
    3. PR 번호 (생성 후)
  """
)
```

→ 반환값에서 **API 스펙** 추출하여 Phase 3에 전달

### Phase 3: FE 구현 (Sub-agent 2)

BE 완료 후 실행. API 스펙을 prompt에 포함한다.

```
Agent(
  subagent_type: "general-purpose",
  prompt: """
    [fe-feature-builder.md 파일 전체 내용 포함]

    ## 이번 작업
    기능명: [기능명]

    ## BE API 스펙 (be-feature-builder 산출물)
    엔드포인트: POST /api/v1/ai-check/[endpoint]
    Request: { userId, [fields...] }
    Response: { [fields...] }

    ## 구현 내용
    [구체적인 구현 요청]

    ## 브랜치
    git fetch origin main
    git checkout feat/[feature-name]  ← BE와 동일 브랜치

    ## 완료 시 반환
    1. 구현한 파일 목록
    2. PR 번호 (BE PR에 FE 커밋 포함 or 별도 PR)
  """
)
```

### Phase 4: QA 리뷰 (Sub-agent 3)

BE + FE 완료 후 실행.

```
Agent(
  subagent_type: "general-purpose",
  prompt: """
    [qa-reviewer.md 파일 전체 내용 포함]

    ## 리뷰 대상
    기능명: [기능명]
    BE 브랜치/PR: [번호 또는 브랜치명]
    FE 변경사항: [파일 목록]

    리뷰 완료 후 보고서를 반환한다.
  """
)
```

→ 반환된 보고서에서 **CRITICAL** 여부 확인

### Phase 5: CRITICAL 처리

CRITICAL 없음 → Phase 6으로

CRITICAL 있음:
- BE CRITICAL → be-feature-builder 재스폰 (수정 지시 + 브랜치 이어서)
- FE CRITICAL → fe-feature-builder 재스폰 (수정 지시)
- 수정 후 qa-reviewer 재실행
- 최대 2회 재시도. 실패 시 사용자에게 보고

### Phase 6: 완료 보고

```
## 기능 구현 완료: [기능명]

### 구현 내역
- BE: [PR 번호/링크] — [핵심 변경사항]
- FE: [PR 번호/링크] — [핵심 변경사항]

### QA 결과
- BE: [이상 없음 / WARNING N건]
- FE: [이상 없음 / WARNING N건]
- BE↔FE 정합성: [일치 / 불일치 항목]

### 다음 단계
- [ ] PR 머지
- [ ] [추가 확인 항목]
```

## 에러 핸들링

| 상황 | 처리 |
|------|------|
| 빌드 실패 | 에러 로그 포함하여 해당 에이전트 재스폰 |
| BE↔FE 스펙 불일치 | fe-feature-builder에 BE 스펙 재전달 후 수정 지시 |
| 브랜치 충돌 | `git rebase origin/main` 지시 포함하여 재스폰 |
| 2회 재시도 실패 | 오케스트레이터가 직접 처리 또는 사용자 보고 |

## BE만 / FE만 요청 시

- BE만: Phase 2만 실행 → qa-reviewer에 BE만 검토 요청
- FE만: Phase 3만 실행 (API 스펙은 사용자 요청에서 파악) → qa-reviewer에 FE만 검토 요청
