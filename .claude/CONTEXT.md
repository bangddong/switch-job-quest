# 작업 컨텍스트

> Claude가 매 대화 끝에 업데이트하는 파일입니다.
> 새 대화 시작 시 이 파일을 먼저 읽으면 이전 상태를 이어받을 수 있습니다.

## 현재 브랜치 상태

| 브랜치 | 상태 | 설명 |
|--------|------|------|
| `main` | 최신 (PR #12 머지됨) | 기술스택 레벨 → 경력기간 변경 반영 |
| `feat/interview-coach-be` | **PR #13 오픈** | BE 면접 코치 API |
| `feat/interview-coach-fe` | **PR #14 오픈** | FE 면접 코치 UI |

## 열린 PR

| PR | 브랜치 | 상태 | 내용 |
|----|--------|------|------|
| [#13](https://github.com/bangddong/switch-job-quest/pull/13) | `feat/interview-coach-be` | 오픈, CI 대기 중 | 면접 코치 API 3개 (start/answer/report) |
| [#14](https://github.com/bangddong/switch-job-quest/pull/14) | `feat/interview-coach-fe` | 오픈, CI 대기 중 | 면접 코치 4단계 코칭 UI |

## 최근 결정 사항

### 멀티 에이전트 패턴 도입 (2026-03-31)
- Claude가 기획자/오케스트레이터 역할 담당
- BE/FE 각각 독립 에이전트(isolation: worktree)로 병렬 작업
- 에이전트 권한 문제 → `~/.claude/settings.json`에 permissions 추가로 해결
- 에이전트 완료 후 기획자(Claude)가 코드 리뷰 → 보완 지시 또는 직접 수정 후 PR

### 면접 코치 기능 (2026-03-31)
- **컨셉**: 전담 코치가 처음부터 끝까지 함께하는 1:1 코칭 세션
- **흐름**: JD 입력 → JD 분석 → 질문별 Q&A + 즉시 피드백 → 종합 리포트
- **BE**: Stateless API (클라이언트가 히스토리 전달), `InterviewCoachPort` Port & Adapter 패턴
- **FE**: `features/interview-coach/` 신규 feature, CoachBubble 말풍선 UI

## 다음 작업

- [ ] PR #13 (BE) 머지 — CI 통과 확인 후, BE 먼저
- [ ] PR #14 (FE) 머지 — BE 머지 후
- [ ] Vercel 자동 배포 확인
- [ ] 이후 방향: 4-BOSS 퀘스트 구현 또는 추가 기능 논의

## 멀티 에이전트 운영 노하우

- `settings.json` permissions 설정 필수 (Bash, Write, Edit, Read, Glob, Grep)
- 에이전트는 `isolation: "worktree"` + `run_in_background: true` 조합으로 실행
- 에이전트 완료 후 반드시 기획자(Claude)가 핵심 파일 직접 읽고 리뷰
- 리뷰 후 보완 있으면 SendMessage로 이전 에이전트 컨텍스트 이어서 지시 가능

## 환경 메모

- FE Vercel 프로젝트: `fe/.vercel/project.json` (`switch-job-quest`, `team_G2dvpYbZ8iZ0gU18lvcId6zn`)
- BE는 Vercel 미연결 (별도 배포)
- FE 배포: `fe/` 디렉토리 기준으로 `vercel --prod` 실행
