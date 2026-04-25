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

에이전트 파일 위치: `.claude/agents/` / 스크립트: `.claude/scripts/` / 스킬: `.claude/skills/`

## 진척 기록 규칙

모든 작업의 시작과 완료는 반드시 아래 파일에 반영한다.

### CONTEXT.md (`.claude/CONTEXT.md`)

| 시점 | 해야 할 것 |
|------|-----------|
| 작업 시작 시 | "현재 브랜치 상태" 또는 "다음 작업" 항목에 진행 중임을 기록 |
| 작업 완료 시 | 완료된 PR 번호·내용·날짜 추가, 결정 사항 기록, "다음 작업" 갱신 |
| 사용자가 Claude 밖에서 직접 처리한 경우 | 다음 대화 시작 시 사용자에게 확인 후 반영 |

### TASKS.md (`.claude/TASKS.md`)

| 시점 | 해야 할 것 |
|------|-----------|
| 사용자 직접 실행이 필요한 작업 발생 시 | 항목 추가 (단계별 명령어 포함) |
| 해당 작업 완료 확인 시 | 항목 제거 또는 완료 섹션으로 이동 |
| 미완료 항목이 없을 때 | "현재 미완료 항목 없음" 한 줄만 남김 |

> 작업 시작·완료 후 이 두 파일을 업데이트하지 않으면 다음 대화에서 상태를 이어받을 수 없다.

### CONTEXT.md 압축 규칙

- CONTEXT.md가 80줄을 초과하면 오래된 "최근 완료" 항목을 `.claude/CONTEXT.archive.md`로 이동
- "알아둬야 할 비자명적 결정" 항목은 코드에 반영됐으면 삭제, 아직 비자명적이면 유지

## Compact Instructions
- 현재 브랜치명, 열린 PR 번호·제목 반드시 보존
- 에이전트 실행 순서: orchestrator → be-feature-builder → (design-reviewer) → fe-feature-builder → qa-reviewer
- BE 거부 규칙: core-domain에 Spring 어노테이션 금지 / db-core ↔ client-ai 직접 의존 금지 / `!!` 사용 금지
- FE 거부 규칙: default export 금지 / CSS 모듈·Tailwind·styled-components 금지 / Context·Redux·Zustand 금지
- 테스트 거부 규칙: Evaluator 테스트에 `@Mock`/`@InjectMocks` 사용 금지 → `RETURNS_DEEP_STUBS` 패턴 사용
- 이 세션에서 시도했다 거부한 접근법과 그 이유
- 발생한 에러와 수정 방법 (특히 생성자 변경 → @Mock 누락, verify() 파라미터 불일치 패턴)
- PassCriteriaPolicy 정규화가 필요한 Evaluator와 단순 result.passed 사용이 정상인 케이스 구분

## 컨텍스트 관리 (/compact)

컨텍스트가 쌓이면 `/compact`로 압축한다. 상황별 템플릿:

```
# PR 완료 직후
/compact Keep: branch=<브랜치명>, PR #<번호> 머지 완료, 다음 작업=<항목>

# 디버깅/탐색 세션 후
/compact Keep: 원인=<발견한 원인>, 수정 방향=<적용할 방향>, 현재 브랜치=<브랜치명>

# 새 기능 시작 전
/compact Keep: 현재 main 최신, 다음 기능=<기능명>, 관련 파일=<파일 경로>
```

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
- **message**: 한국어, 현재형
- **Co-Authored-By 태그 포함 금지**

예: `feat(be): resume 평가자 port 및 adapter 추가`
