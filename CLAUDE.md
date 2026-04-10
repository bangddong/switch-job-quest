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
- 단순 질문, 읽기 전용 탐색은 직접 처리 가능
- **파일 수정이 수반되는 모든 작업은 반드시 feature 브랜치에서 진행** (main 직접 수정 금지)
- qa-reviewer는 구현 의도를 전달받지 않음 — 코드만 보고 독립적으로 판단
- 모든 에이전트는 `model: "sonnet"` 사용

**브랜치 규칙 (오케스트레이터 포함 모든 작업):**
```
# 작업 시작 전 반드시
git fetch origin main
git checkout -b <type>/<name> origin/main
```
- `feat/` : 새 기능
- `fix/`  : 버그 수정
- `chore/`: 설정·인프라·툴링 (`.claude/`, `.github/`, 빌드 등)
- `docs/` : 문서만 변경
- `refactor/`: 리팩토링

main 브랜치에서 Write/Edit 시도 시 → PreToolUse hook이 차단하고 브랜치 생성을 요구함

**디렉토리 구조:**
```
.claude/
├── agents/
│   ├── orchestrator.md       ← Agent 모드 오케스트레이터 (claude --agent orchestrator)
│   ├── be-feature-builder.md ← Sub-agent (BE 구현, be/ 전용, fe/ 쓰기 차단)
│   ├── fe-feature-builder.md ← Sub-agent (FE 구현, fe/ 전용, be/ 쓰기 차단)
│   ├── qa-reviewer.md        ← Sub-agent (통합 리뷰, permissionMode: plan)
│   ├── logic-reviewer.md     ← Sub-agent (BE 로직 리뷰, permissionMode: plan)
│   ├── convention-reviewer.md← Sub-agent (컨벤션 체크, permissionMode: plan)
│   └── test-writer.md        ← Sub-agent (테스트 작성, test/ 전용)
├── scripts/
│   ├── assert-not-main.sh    ← 메인 Claude: main 브랜치 Write/Edit 차단
│   ├── assert-be-path.sh     ← be-feature-builder: fe/ 쓰기 차단 스크립트
│   └── assert-fe-path.sh     ← fe-feature-builder: be/ 쓰기 차단 스크립트
└── skills/
    └── feature-dev/
        └── SKILL.md          ← 스킬 기반 오케스트레이터 (일반 세션용)

**Agent() 스폰 제한 (강한 격리가 필요할 때):**
`claude --agent orchestrator` 로 세션 시작 시 허용된 6개 sub-agent만 스폰 가능.
일반 세션에서 feature-dev 스킬 사용 시에는 적용되지 않음.

**변경 이력:**
| 날짜 | 변경 내용 | 사유 |
|------|----------|------|
| 2026-04-06 | feature-dev Team 하네스 신설 | 오케스트레이터의 확증 편향 제거, 컨텍스트 격리 |
| 2026-04-08 | Team → Sub-agent 패턴으로 전환 | TeamCreate/SendMessage 토큰 낭비 제거 |

## 설계·판단 규칙

### 리스크 선제 제시

다음 세 가지 상황에서는 **제안 전에 리스크를 먼저 제시**한다.

| 상황 | 예시 |
|------|------|
| 구조 변경 | 워크플로우 수정, 브랜치 보호 변경, harness 설계 변경 |
| 우회 행동 | `--admin`, `--force`, `--no-verify` 계열 명령어 |
| 멀티 스텝 계획 | 여러 PR/스프린트로 나뉘는 작업 |

**제시 형식:**
```
> 리스크: <이 접근의 가장 큰 약점>
> 전제 조건: <이 제안이 맞으려면 성립해야 하는 가정>
> 대안: <다른 접근이 있다면>
```

목적: "제안 → 사람이 반박 → 수정" 사이클을 "제안(+리스크) → 사람이 판단"으로 단축.
일반 구현(파일 수정, 기능 추가)에는 적용하지 않는다.

### 원인 파악 우선

막히거나 예상과 다른 결과를 마주쳤을 때, 우회보다 원인 파악을 먼저 한다.

- 우회책은 원인을 명시하고 트레이드오프를 밝힌 뒤 제안한다.
- "일단 되게 하고 나중에 고치자"는 접근은 하지 않는다.

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
