# SKILL: question-bank-seed

mneme wiki(`E:/development/wiki/`)의 `tech/`, `ai-llm/` concept 페이지를 큐레이션해
`tech_question_bank` Flyway 시드 마이그레이션 PR을 생성한다.

> **불변식**: 빌드타임 시드 전용. sjq 앱 런타임 코드가 mneme/wiki를 호출하는 구조는 금지.
> (근거: CONTEXT.md "mneme wiki ↔ 앱 데이터 관계" 결정)

## 트리거

- 수동: `/question-bank-seed` 명령
- 반자동: SessionStart 훅(`check-wiki-question-candidates.sh`)이 미처리 wiki 페이지
  5건 이상이면 세션 시작 시 알림 → 사용자 승인 후 이 스킬 실행

---

## 실행 절차

### 1. 미처리 페이지 파악

```bash
# 처리 완료 목록 (repo-relative 경로 한 줄씩)
cat .claude/state/question-bank-seeded.txt 2>/dev/null

# 현재 wiki concept 페이지 전체
find E:/development/wiki/tech/pages E:/development/wiki/ai-llm/pages -name '*.md' | sort
```

두 목록의 차집합 = 이번 처리 대상. 대상 0건이면 종료.

### 2. 기존 뱅크 질문 파악 (중복 방지)

```bash
grep -h "INSERT INTO tech_question_bank" -A 50 \
  be/storage/db-core/src/main/resources/db/migration/V*.sql
```

기존 질문과 주제가 겹치면 새 질문 생성 금지.

### 3. 질문 생성

대상 페이지를 Read로 읽고, 페이지당 **1~2개** 질문을 생성한다.

- **관점**: 5년차 백엔드 개발자 이직 면접 기준. 정의 암기가 아니라
  "왜 그런가 / 어떤 트레이드오프인가 / 실무에서 어떻게 다루는가"를 묻는다.
- **category**: 기존 값 재사용 우선 — `java-spring`, `concurrency`, `database`,
  `system-design`, `ai-llm`, `network`, `infra`. 새 카테고리 남발 금지.
- **source**: `wiki:<카테고리>/<페이지 경로>` (예: `wiki:tech/pages/java-spring/concurrent-hashmap.md`)
- **reference_url**: wiki 페이지에 명시된 외부 참고 링크가 있으면 그 URL, 없으면 NULL.
  wiki 로컬 경로를 URL로 넣지 않는다 (앱에서 접근 불가).

### 4. Flyway 마이그레이션 작성

> ⚠️ **버전 번호는 두 디렉토리 합산 최대값 + 1** (V8 충돌 prod 다운 사고 재발 방지):
> ```bash
> ls be/core/core-api/src/main/resources/db/migration/ \
>    be/storage/db-core/src/main/resources/db/migration/
> ```

위치: `be/storage/db-core/src/main/resources/db/migration/V<N>__seed_tech_question_bank_<YYYYMM>.sql`

형식 (V10 시드와 동일):

```sql
INSERT INTO tech_question_bank (category, question, reference_url, source) VALUES
('java-spring', '질문 내용...', NULL, 'wiki:tech/pages/java-spring/concurrent-hashmap.md');
```

### 5. 처리 목록 갱신

이번에 처리한 wiki 페이지 경로를 `.claude/state/question-bank-seeded.txt`에 추가
(한 줄에 하나, `E:/development/wiki/` 기준 전체 경로, sort 유지).

### 6. 검증 + 커밋 + PR

```bash
# 브랜치 (이미 작업 브랜치면 생략)
git checkout -b chore/question-bank-seed-$(date +%Y%m) origin/main

cd be && ./gradlew :storage:db-core:build -x test && cd ..

git add be/storage/db-core/src/main/resources/db/migration/ .claude/state/question-bank-seeded.txt
git commit -m "chore(be): 질문 뱅크 wiki 시드 추가 — $(date +%Y-%m)"
git push -u origin <브랜치>
gh pr create --title "chore(be): 질문 뱅크 wiki 시드 추가 $(date +%Y-%m)" --base main --body "..."
```

PR body에 포함 (한국어):
- 추가 질문 목록 (카테고리 · 출처 wiki 페이지)
- Flyway 버전 번호 + 두 디렉토리 합산 중복 검사 결과

**사람 검수(PR 리뷰)가 큐레이션 단계다 — 머지 전 반드시 사용자가 질문 품질을 확인한다.**

---

## 완료 조건

- [ ] Flyway 버전이 두 디렉토리 합산 최대값 + 1
- [ ] 기존 질문과 주제 중복 없음
- [ ] `question-bank-seeded.txt` 갱신됨
- [ ] db-core 빌드 통과
- [ ] PR 생성 완료 (머지는 사용자 검수 후)
