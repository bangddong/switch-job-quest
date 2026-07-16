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
| 스킬 작성/수정 | `.claude/docs/skill-guide.md` |
| 사용자 직접 실행 필요한 작업 | `.claude/TASKS.md` ← 파일이 존재하면 미완료 작업 있음 |
| 현재 작업 상태 / PR / 최근 결정 | `.claude/CONTEXT.md` ← 새 대화 시작 시 **먼저 읽기** |

## mneme (외부 성장 두뇌) 사용 규칙

`http://localhost:8080/mcp`에 상시 가동되는 공유 두뇌 MCP. 매 세션 초기화되지 않고
경험·지식이 누적된다. 작업 흐름에 다음을 끼워 넣는다.

| 시점 | 호출 | 목적 |
|------|------|------|
| 작업 시작 전 | `skill_suggest(task, session_id)` | 관련 스킬·과거 교훈을 받아 계획에 반영 |
| 새 역량 등장 시 | `skill_seed(name, description)` | 스킬 등록 (이 프로젝트 스킬은 `sjq-` 접두어) |
| 작업 종료 후 | `episode_reflect(task, outcome, success, score, skills_used, session_id)` | 결과 반성 제출 → 성향 학습 |
| 지식 검색/축적 | `wiki_search` / `wiki_inject` | 위키 카테고리 `switch-job-quest/` 사용 |

- mneme 서버가 꺼져 있으면 도구가 안 보인다 — 무시하고 평소대로 진행하면 된다(선택적 의존).
- 자세한 동작 원리는 `E:/development/mneme-mcp/docs/` 참고.

## EKS 작업 일지 규칙 (EKS 이관 작업 종료 시까지 유지)

EKS 관련 작업(계획: `infra/aws-eks/README.md`) 중에는 **`docs/eks-migration-log.md`를 실시간 유지**한다.
블로그 "AWS free tier $200 EKS 실습기" 원고 소스다.

| 규칙 | 내용 |
|------|------|
| 기록 시점 | 이벤트 발생 **즉시 append** — 작업 후 몰아쓰기 금지 |
| 기록 대상 | ① 비용 결정(리소스 on/off, 선택지별 예상 비용, $200 잔여 추정) ② 아키텍처·도구 선택(+기각 대안·이유) ③ 막힌 지점(**에러 원문 그대로** + kubectl 등 당시 상태) ④ 해결 과정(실패 시도 포함, 원인) ⑤ 예상·문서와 실제가 달랐던 것 |
| 엔트리 형식 | 날짜(YYYY-MM-DD) 헤더 아래 시간순. 태그 `[비용]` `[결정]` `[막힘]` `[해결]` `[메모]` 중 하나. 다듬은 문장 말고 **사실·명령어·출력·숫자 그대로** |
| 비용 추적 | 일지 최상단 누적 비용 테이블(리소스\|시작일\|종료일\|예상 비용\|크레딧 잔여) — 변동 시마다 갱신 |
| 에이전트 전파 | orchestrator·서브에이전트에 EKS 작업 위임 시 **이 규칙을 프롬프트에 포함** — 위임 작업의 결정·에러·해결도 반환받아 일지에 기록 (또는 에이전트가 직접 append) |

### 정답 경로 문서 — `docs/eks-tutorial-steps.md` (일지와 별도 유지)

단계가 **최종 성공하면** "처음 하는 사람이 그대로 따라 할 수 있는 형태"로 기록한다:

- 실패 시도 제외 — **동작 확인된 명령어만** 실행 순서대로
- 각 명령어에 기대 출력 또는 확인 방법 명시 (예: "`kubectl get pods`에서 Running이면 정상")
- 단계별 사전 조건: 도구 버전, AWS 권한, 앞 단계 산출물
- 손으로 고친 설정 파일은 **최종본 기준** 경로·내용(또는 레포 파일 참조) 명시
- **비용 발생 시작 지점**과 실습 후 cleanup 명령어 포함
- 이전 단계 방식을 갈아엎으면 **과거 단계도 최종 방식으로 갱신** — 문서는 항상 "지금 처음부터 따라 하면 되는 상태" 유지

### 스크린샷 규칙

- **캡처는 사용자가 직접 한다. 에이전트는 브라우저로 캡처하지 않는다.**
- 대신 튜토리얼 문서에서 GUI 화면이 필요한 단계마다 자리표시를 남긴다:
  `<!-- 캡처 필요: step-XX-<설명>.png — 어떤 화면에서 무엇이 보여야 하는지 한 줄 -->`
  (예: "EKS 클러스터 상세 페이지에서 Status: Active 상태")
- 파일명 `step-XX-<설명>.png`, 위치 `docs/images/eks-tutorial/`. 사용자가 캡처를 넣으면
  자리표시를 실제 이미지 참조로 교체한다.
- `eks-tutorial-steps.md` 최상단에 **캡처 체크리스트**(파일명 | 필요한 화면 | 완료) 테이블 유지 —
  몰아 찍을 때 남은 것이 보이게.
- **휘발성 화면**(프로비저닝 중 상태, 특정 시점 비용 대시보드 등 리소스 삭제 후 재현 불가)은
  자리표시만 남기지 말고 **그 즉시 사용자에게 "지금 찍어야 함" 알림**.
- 터미널 출력은 이미지 금지 — **텍스트 코드블록**.
- **remote 세션 이미지 전송법** (확정): 채팅 인라인 이미지·파일은 실행 디스크에 안 닿고 클립보드도
  격리됨. → 사용자가 **GitHub 댓글창에 Ctrl+V(자동 업로드) → URL 전달** → 에이전트가 `gh` 토큰으로
  받아 저장: `curl -sSL -H "Authorization: token $(gh auth token)" -o <경로> <url>` (익명은 404).

### 재현 검증 (이관 완료 후 필수)

클러스터 전체 destroy → **`eks-tutorial-steps.md`만 보고** 처음부터 재현. 글 공개 전 문서 정확성 검증.
막히면 문서를 고치고 재시도 — 통과할 때까지 완료 아님.

EKS 이관 작업이 끝나면 이 섹션을 제거한다.

## 브랜치 규칙

모든 파일 수정은 반드시 작업 브랜치에서 진행 (main 직접 수정 금지).

```bash
git fetch origin main
git checkout -b <type>/<name> origin/main
```

- `feat/` : 새 기능
- `fix/`  : 버그 수정
- `chore/`: 설정·인프라·툴링
- `docs/` : 문서만 변경
- `refactor/`: 리팩토링

main에서 Write/Edit 시도 시 → PreToolUse hook이 차단하고 브랜치 생성을 안내함.

## 진척 기록 규칙

모든 작업의 시작과 완료는 반드시 아래 파일에 반영한다.

### CONTEXT.md (`.claude/CONTEXT.md`)

| 시점 | 해야 할 것 |
|------|-----------|
| 작업 시작 시 | 브랜치명 기록, PR 생성 후 번호 추가 |
| 작업 완료 시 | 완료된 PR 번호·내용·날짜 추가, "다음 작업" 갱신 |

### TASKS.md (`.claude/TASKS.md`)

| 시점 | 해야 할 것 |
|------|-----------|
| 사용자 직접 실행이 필요한 작업 발생 시 | 항목 추가 (단계별 명령어 포함) |
| 해당 작업 완료 확인 시 | 항목 제거 |

- CONTEXT.md가 80줄 초과 시 오래된 항목을 `.claude/CONTEXT.archive.md`로 이동

## Compact Instructions

> 이 섹션은 auto-compaction과 수동 `/compact` 모두에 자동 주입된다.

### 반드시 보존할 상태
- 현재 브랜치명, 열린 PR 번호·제목
- 최근 완료된 PR 번호·내용·날짜 (최대 3건)
- CONTEXT.md의 "다음 작업" 항목 전체
- CONTEXT.md의 "알아둬야 할 비자명적 결정" 항목 전체

### 아키텍처 규칙 (절대 잊지 말 것)
- 에이전트 실행 순서: orchestrator → be-feature-builder → (design-reviewer) → fe-feature-builder → qa-reviewer
- BE 거부 규칙: core-domain에 Spring 어노테이션 금지 / db-core ↔ client-ai 직접 의존 금지 / `!!` 사용 금지
- FE 거부 규칙: default export 금지 / CSS 모듈·Tailwind·styled-components 금지 / Context·Redux·Zustand 금지
- 테스트 거부 규칙: Evaluator 테스트에 `@Mock`/`@InjectMocks` 사용 금지 → `RETURNS_DEEP_STUBS` 패턴 사용

### 이 세션에서 발생한 것 (있으면 보존)
- 시도했다 거부한 접근법과 그 이유
- 발생한 에러와 수정 방법

## 빨간 깃발 (이 생각이 들면 멈춰라)

| 생각 | 올바른 행동 |
|------|------------|
| "이건 너무 간단해서 테스트가 필요 없어" | `tdd` 스킬 적용 |
| "일단 써보고 테스트 나중에" | TDD 위반 → 삭제 후 재시작 |
| "아마 수정됐을 거야" | `verification-before-completion` 스킬 적용 |
| "이번 한 번만 빠르게" | 예외 없음 |
| "원인은 모르겠지만 이거 바꿔보자" | `systematic-debugging` Phase 1로 |
| "이미 3번 시도했는데 안 되네, 4번 더 해보자" | 아키텍처 재검토 후 사용자에게 보고 |
| "설계는 나중에, 일단 코딩" | `brainstorming` 스킬 |
| "아마 이런 뜻이겠지" | `clarify-before-execute` 스킬 적용 |
| "됐어요! 완료!" | 검증 증거 없으면 금지 |

## 설계·판단 규칙

### 리스크 선제 제시

구조 변경 / 우회 명령어(`--admin`, `--force`, `--no-verify`) / 멀티 스텝 계획 제안 시 반드시:

```
> 리스크: <이 접근의 가장 큰 약점>
> 전제 조건: <이 제안이 맞으려면 성립해야 하는 가정>
> 대안: <다른 접근이 있다면>
```

일반 구현(파일 수정, 기능 추가)에는 적용하지 않는다.

### 원인 파악 우선

막히거나 예상과 다른 결과 → 우회 전에 원인 파악 먼저. "일단 되게 하고 나중에 고치자" 금지.

## 커밋 컨벤션

```
<type>(<scope>): <message>
```

- **type**: feat, fix, chore, docs, refactor, test, style
- **scope**: be, fe, 또는 생략
- **message**: 한국어, 현재형
- **Co-Authored-By 태그 포함 금지**

예: `feat(be): resume 평가자 port 및 adapter 추가`
