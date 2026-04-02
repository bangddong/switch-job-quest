# 작업 컨텍스트

> Claude가 매 대화 끝에 업데이트하는 파일입니다.
> 새 대화 시작 시 이 파일을 먼저 읽으면 이전 상태를 이어받을 수 있습니다.

## 현재 브랜치 상태

| 브랜치 | 상태 | 설명 |
|--------|------|------|
| `main` | PR #18 머지됨 | UX Sprint 1 (E+F) 완료 |
| `feat/ux-sprint2` | 작업 중 (PR #19 오픈) | UX 포기 방지 Sprint 2 (B+C) |

## 열린 PR

| PR | 브랜치 | 상태 | 내용 |
|----|--------|------|------|
| [#19](https://github.com/bangddong/switch-job-quest/pull/19) | `feat/ux-sprint2` | 오픈 | 오늘의 미션 배너 + 퀘스트 브리핑 화면 |

## 최근 결정 사항

### UX 포기 방지 시스템 Sprint 2 (2026-04-02)
- **B. TodayMissionBanner**: QuestMap 상단 다음 추천 퀘스트 배너 (QUEST_NEXT 연결 그래프 활용)
- **C. QuestBriefingView**: 퀘스트 진입 전 브리핑 화면 (XP/소요시간/AI검사/태스크 미리보기)
- `briefing` View kind 추가 → ActCard 클릭 + 배너 CTA 모두 브리핑 거쳐 QuestDetail 진입

### UX 포기 방지 시스템 Sprint 1 (2026-04-02)
- **기획**: 6개 포기 지점 분석 → 7개 기능(A-G) → 4개 스프린트 로드맵 (`.claude/docs/ux-retention-plan.md`)
- **E. NextQuestCard**: AI 통과 시 다음 퀘스트 연결 카드 표시
- **F. RetryCoachCard**: AI 실패 시 개선 포인트 + "이전 답변 불러오기" 버튼

### 4-BOSS 지원 패키지 평가 (2026-04-02)
- **BE**: `POST /api/v1/ai-check/boss-package` — 이력서+GitHub+블로그+목표포지션 종합 평가
- **FE**: `BossPackageResultCard` — 5개 점수 바 + 강점/개선사항/종합피드백 표시

### 퀘스트 히스토리 & 성장 대시보드 (2026-04-01)
- **BE**: `quest_history` 테이블에 AI 평가 시도마다 기록 저장 (Port & Adapter 패턴)
- **BE**: `GET /api/v1/progress/history`, `GET /api/v1/progress/history/{questId}` 추가
- **FE**: `features/growth/` 신설 — ScoreTimeline(꺾은선), 퀘스트별 최고점(바 차트), 최근 시도 목록
- **FE**: 퀘스트 맵 하단 "📈 성장 기록" 버튼으로 진입

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

- [ ] PR #19 CI 확인 후 머지
- [ ] Sprint 3 (추후): D (필드별 작성 가이드) + G (복귀 배너, BE 필요)
- [ ] Sprint 4 (추후): A (온보딩 스토리텔링)

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
