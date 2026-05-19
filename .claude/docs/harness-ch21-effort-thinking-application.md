# Ch21 Effort·Fast Mode·Thinking 적용 사례 (switch-job-quest)

> Ch21 원문: https://dhbang.co.kr/posts/study/harness-engineering-effort-fast-mode-thinking/

---

## 직접 겪은 사례 (switch-job-quest 프로젝트)

사례 1: fe-feature-builder 스폰 프롬프트에 ultrathink 누락 — 타입 추론 얕음

코딩 퀘스트 기능(PR #136)에서 fe-feature-builder가 BE API 스펙을 받고도 중첩 타입을 잘못 추론했습니다. `testCases`, `constraints` 필드가 실제로는 `object[]`인데 `string[]`으로 처리한 것입니다. QA 리뷰에서 잡혔고 fe-feature-builder를 재스폰했는데, 재스폰 시 수정 과정에서 props drilling 구조까지 바뀌어 상위 컴포넌트도 연쇄 수정됐습니다. 돌아보면 스폰 프롬프트 첫 줄에 `ultrathink`가 없었고, BE 스펙 문서를 받아도 표면적인 타입만 읽은 채 구현에 들어간 것입니다. FE 구현은 타입 정의 → props drilling → 컴포넌트 분리 순서로 연쇄 결정이 필요한 작업이라 낮은 추론 수준이 맞지 않습니다.

대응책: fe-feature-builder 스폰 프롬프트 첫 줄에 `ultrathink`를 추가했습니다. BE 스펙을 받으면 타입 구조를 재귀적으로 분석한 뒤 구현을 시작하게 됩니다.

사례 2: design-reviewer 스폰 프롬프트에 ultrathink 누락 — 기존 패턴 미참조

여러 기능에서 design-reviewer가 기존 컴포넌트 패턴을 충분히 참고하지 않고 새 레이아웃 스펙을 제안했습니다. 오케스트레이터가 "참고할 기존 컴포넌트 경로"를 전달하는데도, design-reviewer는 경로를 파일 목록으로만 처리하고 실제 색상·간격·구조 규칙을 깊이 분석하지 않았습니다. fe-feature-builder가 design-reviewer의 스펙을 그대로 따르다 보니 기존 컴포넌트와 시각적으로 어긋난 UI가 만들어졌습니다. 컴포넌트 간 일관성은 파일을 열어보는 것만으로는 파악하기 어렵고, 기존 패턴에서 추상화 원칙을 끌어내는 작업이 필요합니다.

대응책: design-reviewer 스폰 프롬프트 첫 줄에 `ultrathink`를 추가했습니다. 기존 컴포넌트 구조와 신규 기능 요구사항 사이의 접점을 더 신중하게 추론하게 됩니다.

---

`ultrathink`를 모든 에이전트에 무조건 박는 것은 맞지 않습니다. 오케스트레이터는 "무엇을 누구에게 시킬지" 빠르게 분기하는 역할이라 깊은 추론보다 빠른 판단이 더 중요합니다. 구현·설계·검토처럼 연쇄 결정이 필요한 에이전트에만 `ultrathink`를 두는 것이 Ch21에서 말하는 "작업 복잡도에 맞게 추론 깊이를 조정하라"는 원칙과 맞습니다. 스폰 프롬프트 첫 줄 한 단어가 그 조정의 전부입니다.
