# 작업 컨텍스트

> Claude가 매 대화 끝에 업데이트하는 파일입니다.
> 새 대화 시작 시 이 파일을 먼저 읽으면 이전 상태를 이어받을 수 있습니다.

## 현재 브랜치 상태

| 브랜치 | 상태 | 설명 |
|--------|------|------|
| `main` | PR #21 머지됨 | UX 포기 방지 Sprint 1~4 모두 완료 |

## 열린 PR

| PR | 브랜치 | 설명 |
|----|--------|------|
| #22 | `feat/act-v-final-boss` | ACT V 5-BOSS 취뽀 달성 화면 + 퀘스트 연결 정리 |

## 최근 결정 사항

### CI check name 수정 (2026-04-02)
- **원인**: job에 `name:` 없으면 check run = `"build"`, branch protection은 `"FE CI / build"` 요구 → 불일치
- **수정**: `fe-ci.yml`에 `name: FE CI / build`, `be-ci.yml`에 `name: BE CI / test` 추가
- **효과**: PR #21부터 `--admin` 없이 정상 머지 가능

### ACT V 5-BOSS 취뽀 달성 화면 (2026-04-03)
- **BE**: `POST /api/v1/ai-check/journey-report` — 전체 여정 AI 감성 회고 내러티브 생성
- **FE**: `FinalBossView` — 합격 신고 입력 → 취뽀 타이틀/통계/AI 내러티브/마지막 한 마디
- **FE**: `questConnections.ts` — ACT I~V 전 퀘스트 연결 완성 (10개 추가)
- **공통**: GitHub Copilot 리뷰 코멘트 한국어 지시 추가

### UX 포기 방지 시스템 — 전체 완료 (2026-04-02)
- **Sprint 1** (PR #18): E (다음 퀘스트 연결 카드) + F (재도전 코치 + 이전 답변 불러오기)
- **Sprint 2** (PR #19): B (오늘의 미션 배너) + C (퀘스트 브리핑 화면)
- **Sprint 3** (PR #20): D (필드별 작성 가이드 `?` 버튼) + G (복귀 배너, BE lastCompletedAt)
- **Sprint 4** (PR #21): A (온보딩 스토리텔링 5슬라이드 인트로)
- 기획 문서: `.claude/docs/ux-retention-plan.md`

### 4-BOSS 지원 패키지 평가 (2026-04-02)
- **BE**: `POST /api/v1/ai-check/boss-package` — 이력서+GitHub+블로그+목표포지션 종합 평가
- **FE**: `BossPackageResultCard` — 5개 점수 바 + 강점/개선사항/종합피드백 표시

### 퀘스트 히스토리 & 성장 대시보드 (2026-04-01)
- **BE**: `quest_history` 테이블에 AI 평가 시도마다 기록 저장 (Port & Adapter 패턴)
- **FE**: `features/growth/` — ScoreTimeline, 퀘스트별 최고점 바 차트, 최근 시도 목록

### 멀티 에이전트 패턴 도입 (2026-03-31)
- Claude가 기획자/오케스트레이터 역할 담당
- BE/FE 각각 독립 에이전트(isolation: worktree)로 병렬 작업
- 에이전트 완료 후 기획자(Claude)가 코드 리뷰 → 보완 지시 또는 직접 수정 후 PR

### 면접 코치 기능 (2026-03-31)
- **컨셉**: 전담 코치가 처음부터 끝까지 함께하는 1:1 코칭 세션
- **BE**: Stateless API, `InterviewCoachPort` Port & Adapter 패턴
- **FE**: `features/interview-coach/` 신규 feature, CoachBubble 말풍선 UI

## 다음 작업

- [ ] PR #22 머지 후 5-2 수동 완료 UI 추가 검토
- [ ] ACT V 전체 검증 (5-1 AI 폼 → 5-2 수동 → 5-BOSS)

## 멀티 에이전트 운영 노하우

- `settings.json` permissions 설정 필수 (Bash, Write, Edit, Read, Glob, Grep)
- 에이전트는 `isolation: "worktree"` + `run_in_background: true` 조합으로 실행
- **에이전트가 main worktree에서 브랜치 체크아웃하는 문제 발생** → 완료 후 `git branch --show-current` 확인 필수
- 에이전트 완료 후 반드시 기획자(Claude)가 핵심 파일 직접 읽고 리뷰
- 리베이스 충돌 발생 시 Claude가 직접 처리

## 환경 메모

- FE Vercel 프로젝트: `fe/.vercel/project.json` (`switch-job-quest`, `team_G2dvpYbZ8iZ0gU18lvcId6zn`)
- BE는 Vercel 미연결 (별도 배포)
- FE 배포: `fe/` 디렉토리 기준으로 `vercel --prod` 실행
