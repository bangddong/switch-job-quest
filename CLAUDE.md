# Switch Job Quest (DevQuest)

5년차 백엔드 개발자의 이직 준비를 RPG 퀘스트로 구성한 풀스택 프로젝트.
모노레포 (`be/` + `fe/`). 제품 언어: **한국어**.

## 작업별 참고 문서

| 작업 | 참고 |
|------|------|
| BE 코드 작성/수정 | `be/CLAUDE.md` (자동 로드) |
| FE 코드 작성/수정 | `fe/CLAUDE.md` (자동 로드) |
| 커밋 / PR / 브랜치 | `.claude/docs/git-strategy.md` |
| 배포 / 환경변수 | `.claude/docs/deployment.md` |
| 멀티 에이전트 운영 | `.claude/docs/agent-workflow.md` |
| 사용자 직접 실행 필요한 작업 | `.claude/TASKS.md` ← 파일이 존재하면 미완료 작업 있음 |
| 현재 작업 상태 / PR / 최근 결정 | `.claude/CONTEXT.md` ← 새 대화 시작 시 **먼저 읽기** |

## 하네스: Feature Dev Sub-agents

**목표:** BE + FE 기능 구현을 순차 sub-agent로 처리하여 역할 격리 및 객관적 QA 보장

**에이전트 (순차 실행):**
| 에이전트 | 역할 | 순서 |
|---------|------|------|
| `be-feature-builder` | BE 구현 → API 스펙 반환 | 1 |
| `fe-feature-builder` | FE 구현 (BE 스펙 수신) | 2 |
| `qa-reviewer` | 코드 리뷰 + BE↔FE 계약 정합성 QA (수정 불가, 보고만) | 3 |

**스킬:**
| 스킬 | 용도 |
|------|------|
| `feature-dev` | 풀스택 기능 구현 순차 오케스트레이터 |

**실행 규칙:**
- 새 기능/퀘스트 구현 요청 시 `feature-dev` 스킬을 통해 sub-agent 순차 처리
- 단순 질문, 설정 변경, 단일 파일 수정은 직접 처리
- qa-reviewer는 구현 의도를 전달받지 않음 — 코드만 보고 독립적으로 판단
- 모든 에이전트는 `model: "sonnet"` 사용

**디렉토리 구조:**
```
.claude/
├── agents/
│   ├── be-feature-builder.md ← Sub-agent (BE 구현)
│   ├── fe-feature-builder.md ← Sub-agent (FE 구현)
│   ├── qa-reviewer.md        ← Sub-agent (통합 리뷰)
│   ├── logic-reviewer.md     ← Sub-agent (BE 로직 단독 리뷰)
│   ├── convention-reviewer.md← Sub-agent (컨벤션 체크)
│   ├── test-writer.md        ← Sub-agent (테스트 작성)
│   ├── be-developer.md       ← 미사용 (Team 패턴 레거시)
│   └── fe-developer.md       ← 미사용 (Team 패턴 레거시)
└── skills/
    └── feature-dev/
        └── SKILL.md

**변경 이력:**
| 날짜 | 변경 내용 | 사유 |
|------|----------|------|
| 2026-04-06 | feature-dev Team 하네스 신설 | 오케스트레이터의 확증 편향 제거, 컨텍스트 격리 |
| 2026-04-08 | Team → Sub-agent 패턴으로 전환 | TeamCreate/SendMessage 토큰 낭비 제거 |

## 멀티 에이전트 운영 방식

Claude가 기획자/오케스트레이터 역할. BE/FE 작업은 독립 에이전트에 위임.
세부 운영 지침은 `.claude/docs/agent-workflow.md` 참고.

## 커밋 컨벤션

```
<type>(<scope>): <message>
```

- **type**: feat, fix, chore, docs, refactor, test, style
- **scope**: be, fe, 또는 생략
- **message**: 영어, 소문자 시작, 현재형

예: `feat(be): add resume evaluator port and adapter`
