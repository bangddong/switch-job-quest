# 작업 컨텍스트

> Claude가 매 대화 끝에 업데이트하는 파일입니다.
> 새 대화 시작 시 이 파일을 먼저 읽으면 이전 상태를 이어받을 수 있습니다.

## 현재 브랜치 상태

| 브랜치 | 상태 | 설명 |
|--------|------|------|
| `main` | 최신 origin과 동기화됨 | 로컬은 리셋 완료 |
| `refactor/skill-level-to-career-duration` | **작업 중** | PR #12 오픈 |

## 열린 PR

| PR | 브랜치 | 상태 | 내용 |
|----|--------|------|------|
| [#12](https://github.com/bangddong/switch-job-quest/pull/12) | `refactor/skill-level-to-career-duration` | 오픈, CI 통과 | 기술 스택 레벨 입력 방식 `상/중/하` → `경력기간(예: Java:5년)` 변경 |

## 최근 결정 사항

- **기술 스택 자가 진단(1-1) 입력 형식 변경**: `상/중/하`가 주관적이어서 `기술명:경력기간` 형식으로 교체
  - FE: `formConfig.ts` 레이블/플레이스홀더 수정
  - BE: `SkillAssessmentEvaluator.kt` 프롬프트에 형식 설명 추가 + 진단 기준 문구 수정
  - 테스트: `SkillAssessmentEvaluatorTest` 신규 추가 (코파일럿 리뷰 대응)

## 다음 작업

- [ ] PR #12 머지 (CI 통과 확인 후)
- [ ] 머지 후 Vercel 자동 배포 확인

## 환경 메모

- FE Vercel 프로젝트: `fe/.vercel/project.json` (`switch-job-quest`, `team_G2dvpYbZ8iZ0gU18lvcId6zn`)
- BE는 Vercel 미연결 (별도 배포)
- FE 배포: `fe/` 디렉토리 기준으로 `vercel --prod` 실행
