# 스킬 작성 가이드

> 근거: [토스 Skill 품질 루브릭](https://toss.tech/article/skill-quality-rubric)에서
> 이 프로젝트 규모(1인, 스킬 ~12개, orchestrator 결정론적 주입)에 맞는 원칙만 채택.
> 30항목 CI 루브릭 전체 도입은 과잉으로 판단 — 스킬 수십 개·다수 작성자가 되면 재검토.

## 이 프로젝트의 스킬 동작 방식 (전제)

- 스킬은 description 기반 자동 트리거가 아니라 **orchestrator가 작업 유형표로 결정론적 주입**
  (`orchestrator.md` 4·6단계 — 신규 기능이면 tdd + verification 전문을 sub-agent 프롬프트에 붙여넣음)
- → 스킬 본문 한 줄 한 줄이 **매 스폰마다 토큰 비용**. 콘텐츠 규칙이 가장 중요하다.

## 작성 규칙

### 1. 조직 고유 맥락만 (MAJOR)

LLM이 이미 아는 일반론 금지. 이 프로젝트에서만 유효한 결정·수치·경로만 쓴다.

- ❌ "TDD는 테스트를 먼저 작성하는 개발 방법론입니다"
- ✅ "Evaluator 테스트에 `@Mock`/`@InjectMocks` 금지 → `RETURNS_DEEP_STUBS` 패턴"
- ✅ "Flyway 버전은 두 디렉토리(core-api·db-core) 합산 최대값+1" (V8 사고 근거)

### 2. 구체성 ≥ 1 (MINOR)

각 규칙에 수치·코드·Why·실패 시나리오 중 최소 하나를 붙인다.
"~를 주의한다" 같은 문장은 근거 없으면 삭제.

### 3. 분량 150줄 이하 (MINOR)

주입 방식이라 토스 기준(500줄)보다 빡빡하게. 넘으면:
- 필요할 때만 읽는 상세는 `.claude/docs/`로 분리하고 스킬엔 "언제 읽는지" 조건과 함께 링크
- 참조 중첩 금지 (스킬→docs→docs 연쇄 X)

### 4. 트리거 명시 (MAJOR)

파일 상단에 `## 트리거` — 수동 명령어 + 자동(훅/cron/주입표) 경로를 모두 적는다.
orchestrator 주입 대상이면 어느 단계 어느 작업 유형인지 명시.

### 5. 안전성 (BLOCKER — 훅이 자동 차단)

`assert-skill-safety.sh`가 `gh pr create` 시 검사:
- 평문 secret 값 할당 형태 (`api_key: xxxx...`)
- destructive 패턴 (`rm -rf`, `--force`, `--no-verify`, `chmod 777`, `dd if=`, fork bomb)

금지 사례로 언급이 필요하면 명령어 대신 서술로 쓴다 ("강제 푸시 금지").

## 체크리스트 (새 스킬 PR 전)

- [ ] 일반론 0줄 — 전부 이 프로젝트 고유 맥락인가
- [ ] 150줄 이하
- [ ] `## 트리거` 섹션 존재
- [ ] 완료 조건 체크리스트 존재
- [ ] 안전성 훅 통과 (PR 생성 시 자동)

## 방치 스킬 정리

daily 리포트의 "Skill Usage" 섹션에서 주입/호출 0회가 지속되는 스킬은 삭제 후보.
(토스 "다음 단계"의 호출 빈도 결합 — 우리는 activity.jsonl로 구현)
