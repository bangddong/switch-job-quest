# Ch21 Effort·Fast Mode·Thinking 적용 사례 (switch-job-quest)

> Ch21 원문: https://dhbang.co.kr/posts/study/harness-engineering-effort-fast-mode-thinking/

---

## 적용 전 현황 파악

Ch21을 읽기 전 orchestrator의 에이전트 스폰 패턴을 점검했다.

| 에이전트 | ultrathink 포함 여부 |
|---------|-------------------|
| be-feature-builder | ✅ 포함 |
| qa-reviewer | ✅ 포함 |
| fe-feature-builder | ❌ 누락 |
| design-reviewer | ❌ 누락 |

be-feature-builder와 qa-reviewer는 이전 세션에서 이미 추가했지만, fe-feature-builder와 design-reviewer는 누락된 채로 방치돼 있었다.

---

## 직접 겪은 사례 (switch-job-quest 프로젝트)

### 사례 1: fe-feature-builder에 ultrathink 누락 — 타입 불일치 반복

코딩 퀘스트 기능(PR #136)에서 fe-feature-builder가 BE API 응답 타입을 잘못 추론했다. `CodingQuestResponse`의 중첩 타입(`testCases`, `constraints` 필드)을 `string[]`으로 처리했는데, 실제 BE 반환은 `object[]`였다. QA 리뷰에서 잡혔지만 fe-feature-builder를 재스폰해야 했고, 재스폰 시 이번엔 props drilling 구조가 바뀌어 상위 컴포넌트도 수정됐다.

원인을 돌아보면, fe-feature-builder 스폰 프롬프트에 `ultrathink`가 없었다. BE 스펙 문서를 받아도 추론 없이 표면적인 타입만 읽은 것이다. FE 구현은 타입 정의 → props drilling → 컴포넌트 분리 → 상태 관리 순서로 연쇄 결정이 필요한 작업이라 높은 추론 수준이 맞다.

**대응책**: fe-feature-builder 스폰 프롬프트 첫 줄에 `ultrathink` 추가. 이제 BE 스펙을 받으면 타입 구조를 재귀적으로 분석한 뒤 구현을 시작한다.

### 사례 2: design-reviewer에 ultrathink 누락 — 기존 패턴 미참조

성장 기록 화면 등 여러 기능에서 design-reviewer가 기존 컴포넌트 패턴을 충분히 참고하지 않고 새 레이아웃을 제안했다. 기존 `QuestCard`, `ActivityItem` 등의 색상·간격 규칙을 무시하고 독립적인 스펙을 내놓아 fe-feature-builder가 일관성 없는 UI를 구현하게 됐다.

design-reviewer는 "참고할 기존 컴포넌트 경로"를 오케스트레이터에게 전달받지만, 경로를 파일 목록으로만 처리하고 실제 패턴을 깊이 분석하지 않았다. 컴포넌트 간 일관성은 표면적 읽기로는 파악하기 어렵다.

**대응책**: design-reviewer 스폰 프롬프트 첫 줄에 `ultrathink` 추가. 기존 컴포넌트 구조와 새 기능 요구사항 사이의 적합한 접점을 더 신중하게 추론하게 됐다.

---

## 적용하지 않은 것과 이유

| 항목 | 적용 여부 | 이유 |
|------|---------|------|
| `CLAUDE_CODE_EFFORT_LEVEL` 환경변수 | ❌ | 태스크별 `ultrathink`가 더 세밀함. 전역 고정은 단순 작업도 고비용으로 만듦 |
| Fast Mode (Opus 4.6 가속) | ❌ | 자동 메커니즘. 우리가 제어할 것 없음. 모든 에이전트가 sonnet 사용 중 |
| Advisor Tool | ❌ | 서버 사이드 tool로 외부 구성 불가. qa-reviewer가 이미 동일 역할 수행 |
| 숫자형 effort | ❌ | Anthropic 내부 전용 |
| `MAX_THINKING_TOKENS` | ❌ | sonnet 4.6의 adaptive thinking이 자동 처리 |

---

## 핵심 배운 것

Ch21의 핵심 통찰은 "medium을 기본, ultrathink를 마찰 없는 업그레이드 경로로 쓴다"는 설계다. 우리 harness에서 이걸 에이전트 단위로 재해석하면:

- **경량 판단 에이전트** (오케스트레이터 자신): 기본 effort로 분기/조율만 담당
- **구현 에이전트** (be/fe-feature-builder): 복잡한 연쇄 결정 필요 → `ultrathink`
- **설계 에이전트** (design-reviewer): 기존 패턴 분석 + 일관성 판단 → `ultrathink`
- **검토 에이전트** (qa-reviewer): 독립적 코드 리뷰, 계약 정합성 검증 → `ultrathink`

오케스트레이터만 ultrathink 없이 작동하는 게 맞다. 오케스트레이터는 "무엇을 누구에게 시킬지"를 결정하는 메타 판단 역할이라, 깊은 추론보다 빠른 분기가 더 중요하다.
