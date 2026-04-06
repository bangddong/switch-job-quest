---
name: feature-dev
description: "BE + FE 기능 구현 팀을 조율하는 오케스트레이터. 새 퀘스트/기능 구현, BE API 추가, FE 연동, 풀스택 기능 개발 요청 시 반드시 이 스킬을 사용. 후속 작업: 기능 수정, 재구현, 부분 변경, 버그 수정 요청 시에도 이 스킬 사용."
---

# Feature Dev Orchestrator

BE + FE + QA 에이전트 팀을 조율하여 풀스택 기능을 구현하는 통합 스킬.

## 실행 모드: 에이전트 팀 (Team)

## 에이전트 구성

| 팀원 | 파일 | 역할 | 출력 |
|------|------|------|------|
| be-developer | `.claude/agents/be-developer.md` | BE 구현 + API 스펙 전달 | BE PR |
| fe-developer | `.claude/agents/fe-developer.md` | FE 구현 + 스펙 수신 | FE PR |
| qa-reviewer | `.claude/agents/qa-reviewer.md` | 코드 리뷰 + 정합성 QA | 리뷰 보고서 |

## 팀 통신 흐름

```
오케스트레이터
    │ TeamCreate
    ▼
[be-developer] ──SendMessage(API 스펙)──► [fe-developer]
      │                                        │
      └──SendMessage(BE 완료)──► [qa-reviewer] ◄──SendMessage(FE 완료)──┘
                                      │
                              SendMessage(리뷰 결과)
                              ├──► be-developer
                              └──► fe-developer
```

## 워크플로우

### Phase 0: 컨텍스트 확인

기존 진행 중인 작업이 있는지 확인한다.
- 열린 PR 있으면 → 이어서 진행 (PR 번호 파악)
- 새 작업이면 → Phase 1으로

### Phase 1: 작업 분석

1. 사용자 요청에서 파악할 것:
   - 어떤 퀘스트/기능인가? (questId, actId 포함)
   - BE만? FE만? 풀스택?
   - 기존 패턴과 다른 특이사항이 있는가?

2. 브랜치 전략 결정:
   - 풀스택: `feat/[feature-name]` 단일 브랜치 or BE/FE 분리
   - 분리 시 병렬 PR 가능 (FE가 BE 머지 기다릴 필요 없음)

3. 작업 디렉토리 생성:
   ```bash
   mkdir -p .claude/worktrees
   ```

### Phase 2: 팀 구성

> **중요:** `.claude/agents/` 파일은 `subagent_type`으로 직접 참조 불가.
> 반드시 `subagent_type: "general-purpose"` + `prompt`에 에이전트 파일 내용 포함 방식으로 스폰.

```
TeamCreate(
  team_name: "feature-dev-team",
  members: [
    {
      name: "be-developer",
      subagent_type: "general-purpose",  // agent_file 직접 참조 불가 — 내용을 prompt에 포함
      model: "sonnet",
      prompt: """
        [기능명] BE 구현을 담당한다.
        
        작업 내용: [구체적인 구현 내용]
        questId: [id], actId: [id], XP: [값]
        
        브랜치: feat/[feature-name]
        시작:
          git fetch origin main
          git checkout -b feat/[feature-name] origin/main
        
        구현 완료 후:
        1. fe-developer에게 API 스펙 SendMessage
        2. PR 생성
        3. qa-reviewer에게 완료 SendMessage
        
        ## 건드리면 안 되는 파일 (fe-developer 담당)
        - fe/ 디렉토리 전체
      """
    },
    {
      name: "fe-developer",
      agent_file: ".claude/agents/fe-developer.md",
      model: "sonnet",
      prompt: """
        [기능명] FE 구현을 담당한다.
        
        작업 내용: [구체적인 구현 내용]
        
        브랜치: feat/[feature-name] (BE와 동일 브랜치 or 분리)
        시작:
          git fetch origin main
          git checkout -b feat/[feature-name]-fe origin/main
        
        be-developer로부터 API 스펙 수신을 기다린 후 타입 정의 및 연동 구현.
        스펙 수신 전에도 컴포넌트 UI 구조는 선행 작업 가능.
        
        구현 완료 후:
        1. PR 생성
        2. qa-reviewer에게 완료 SendMessage
        
        ## 건드리면 안 되는 파일 (be-developer 담당)
        - be/ 디렉토리 전체
      """
    },
    {
      name: "qa-reviewer",
      agent_file: ".claude/agents/qa-reviewer.md",
      model: "sonnet",
      prompt: """
        be-developer와 fe-developer 양쪽 완료 알림을 모두 수신한 후 리뷰를 시작한다.
        
        리뷰 대상:
        - BE PR: [번호 또는 브랜치명]
        - FE PR: [번호 또는 브랜치명]
        
        코드를 직접 수정하지 않는다.
        리뷰 완료 후 각 에이전트에게 결과를 SendMessage로 전달한다.
      """
    }
  ]
)
```

### Phase 3: 작업 등록

```
TaskCreate(tasks: [
  {
    title: "BE 구현",
    description: "[기능명] Domain Model, Port, Adapter, Service, Controller",
    assignee: "be-developer"
  },
  {
    title: "FE 구현",
    description: "[기능명] 타입 정의, API 클라이언트, 컴포넌트, App.tsx 연결",
    assignee: "fe-developer",
    depends_on: []  // API 스펙은 SendMessage로 수신, 완전 의존 없음
  },
  {
    title: "QA 리뷰",
    description: "BE + FE 코드 리뷰, BE↔FE 계약 정합성 검증",
    assignee: "qa-reviewer",
    depends_on: ["BE 구현", "FE 구현"]
  }
])
```

### Phase 4: 팀 실행 모니터링

팀원들이 자체 조율하며 작업한다. 오케스트레이터는 다음 상황에서만 개입한다:

- **팀원이 막혔을 때**: 에러 메시지 포함 알림 → 해결 방향 SendMessage
- **CRITICAL 리뷰 결과 수신 시**: 수정 방향 팀원에게 재지시
- **브랜치 충돌 발생 시**: rebase 지시

### Phase 5: 완료 종합

1. qa-reviewer 리뷰 보고서 수신
2. CRITICAL 없음 → 사용자에게 완료 보고
3. CRITICAL 있음 → 해당 에이전트에 수정 재지시 후 재리뷰

완료 보고 형식:
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
- [ ] [추가 확인 필요한 항목이 있으면 기재]
```

## 에러 핸들링

| 상황 | 처리 |
|------|------|
| 에이전트가 빌드 실패 | 에러 로그 받아서 원인 분석 후 재지시 |
| BE↔FE 스펙 불일치 | qa-reviewer 보고서 기준으로 be-developer에 수정 지시 |
| 브랜치 충돌 | `git rebase origin/main` 지시 |
| 최대 2회 재시도 후 실패 | 오케스트레이터가 직접 처리 또는 사용자에게 보고 |

## 테스트 시나리오

**정상 흐름:**
1. "새 퀘스트 3-3 기술 블로그 리뷰 기능 추가해줘"
2. BE 구현 → API 스펙 전달 → FE 구현 → QA 리뷰 → 완료 보고

**에러 흐름:**
1. qa-reviewer가 BE↔FE 타입 불일치 CRITICAL 발견
2. be-developer에게 수정 지시 → 수정 후 qa-reviewer 재검토
