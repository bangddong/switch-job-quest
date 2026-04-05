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
