# SKILL: collect-conference-refs

국내 기술 컨퍼런스 발표 자료를 수집하여 `conference-references.json`을 업데이트하고 PR을 생성한다.

## 트리거

- 수동: `/collect-conference-refs` 명령
- 자동: 월 1회 cron 스케줄

---

## 실행 절차

### 1. 현재 상태 파악

```bash
cat be/clients/client-ai/src/main/resources/references/conference-references.json
```

기존 references URL 목록을 파악하여 중복 추가를 방지한다.

### 2. 최신 발표 탐색 (WebSearch)

아래 컨퍼런스별로 검색하되, **현재 연도 기준 최신 발표 우선**:

| 컨퍼런스 | 검색 키워드 | YouTube 채널 |
|----------|------------|-------------|
| 토스 SLASH | `토스 SLASH 발표 YouTube` | youtube.com/@toss.tech |
| 우아콘 | `우아콘 발표 YouTube 우아한테크` | youtube.com/@woowatech |
| DEVIEW | `네이버 DEVIEW 발표 YouTube` | youtube.com/@naverdev |
| INFCON | `인프콘 발표 YouTube` | inflearn.com 공식 채널 |
| if(kakao) | `카카오 if kakao 발표 YouTube` | youtube.com 검색 |

검색 후 **실존 확인 필수**: 찾은 URL을 WebFetch로 접근 가능한지 확인.

### 3. 카테고리 분류

발견한 발표를 아래 카테고리 중 하나에 분류:

| 카테고리 | 해당 주제 |
|----------|----------|
| `java` | JVM, GC, Virtual Thread, 동시성, 코루틴, 힙 메모리 |
| `messaging` | Kafka, 카프카, 메시지 큐, 이벤트 스트리밍 |
| `msa` | MSA, 마이크로서비스, 분산 시스템, CQRS, Saga |
| `database` | DB 인덱스, JPA, N+1, 트랜잭션, MVCC |
| `performance` | Redis, 캐시, 성능 최적화, TPS |
| `spring` | Spring 내부 동작, Bean, AOP, Security |
| `network` | HTTP, TCP, 로드밸런싱, DNS, TLS |

### 4. JSON 업데이트

기존 파일에 새 항목 추가. 형식:

```json
{
  "title": "발표 제목 | 컨퍼런스명",
  "url": "https://www.youtube.com/watch?v=...",
  "source": "컨퍼런스명",
  "year": 2024
}
```

**금지**: 접근 불가 URL, 추측 URL, 한 카테고리에 3개 초과 추가

### 5. 브랜치 생성 및 커밋

```bash
git checkout -b chore/update-conference-refs origin/main
# JSON 수정 후
git add be/clients/client-ai/src/main/resources/references/conference-references.json
git commit -m "chore: 컨퍼런스 참고자료 업데이트 — $(date +%Y-%m)"
git push -u origin chore/update-conference-refs
```

### 6. PR 생성

```bash
gh pr create \
  --title "chore: 컨퍼런스 참고자료 업데이트 $(date +%Y-%m)" \
  --base main \
  --body "..."
```

PR body에 포함:
- 추가된 발표 목록 (카테고리별)
- 각 URL 실존 확인 여부

---

## 완료 조건

- [ ] 새 발표 1개 이상 추가됨
- [ ] 모든 추가된 URL이 실존 확인됨
- [ ] 중복 URL 없음
- [ ] 빌드 통과 (`cd be && ./gradlew build -x test`)
- [ ] PR 생성 완료
