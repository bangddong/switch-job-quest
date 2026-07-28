# 리뷰 지적 원장 (Review Ledger)

> **이 파일은 커밋된다.** 브랜치가 삭제돼도, 세션이 끊겨도, 머신이 바뀌어도 살아남는 유일한 지적 저장소.
> 브랜치별 작업 파일 `.claude/qa-cache/<브랜치>.findings.md`는 **gitignore = 휘발성**이다.
> 그러니 **PR에서 안 고치기로 한 지적은 반드시 여기로 이관**해야 한다. 안 하면 사라진다.

## 규칙

| | |
|--|--|
| 언제 적나 | 브랜치 findings의 지적이 `deferred`로 판정될 때 — **PR 생성 전에** |
| 누가 적나 | orchestrator (qa-reviewer는 브랜치 findings까지만 담당) |
| 언제 지우나 | 지우지 않는다. 상태를 `closed`/`wontfix`로 바꾸고 근거를 남긴다 |
| 강제 장치 | `assert-qa-run.sh`가 `deferred` 지적의 원장 등재 여부를 검사 → 누락 시 PR 차단 |

**상태**: `open`(미처리) · `closed`(해소, 근거 필수) · `wontfix`(안 고치기로 확정, 근거 필수) · `obsolete`(코드가 바뀌어 무의미)

**출처 표기**: `<브랜치>/<F-ID>` — 훅이 이 문자열로 등재 여부를 찾는다. 형식을 바꾸지 말 것.

---

## 미해결 (open)

| ID | 출처 | 등급 | 내용 | 이관 사유 / 처리 방향 |
|----|------|:----:|------|---------------------|
| L-1 | `feat/phase1-ai-call-log-observability/F-2` | MEDIUM | **`ai.call.log.recorded{success=false}` 태그가 도달 불가.** `CacheMetricsAdvisor`가 정상 응답일 때만 `record()`를 부르고 `success = true`로 하드코딩한다(실패 경로는 조기 return). 어댑터 로직은 옳지만 운영에서 이 태그는 영원히 0이다 | client-ai 수정 = inprocess 경로 동작 변경이라 Phase 1의 "동작 불변" 원칙에 반함. **AI 실패율 관측은 Task 1.4의 HTTP 어댑터 에러 매핑으로 확보**하기로 했고 부분 확보됨. advisor 자체 개선은 **Phase 3 정리 대상** |
| L-2 | `feat/phase1-ai-api-rest-controllers/F-4` | LOW | `TechInterviewWireFormatContractTest`에 **wire format 계약과 에러 경로 계약이 혼재**. 파일명이 내용을 정확히 표현하지 못함 | 동일 `@SpringBootTest` 컨텍스트 재사용이 목적이라 기능 문제 없음. 리네임(`TechInterviewHttpContractTest` 등) 권고 수준 |
| L-3 | `feat/phase1-ai-call-log-observability/F-3` | LOW | `AiCallLogObservabilityAdapter.record(log: AiCallLog)`의 **파라미터명 `log`가 클래스 멤버 로거를 섀도잉**. `this.log`로 해소돼 동작은 정상이나 가독성 저하 | 파라미터명을 `entry`/`callLog`로 변경 권고 |
| L-4 | `feat/phase1-ai-api-config-migration/F-1` | LOW | **core-api `application.yml` 주석의 근거 서술이 과장.** "지금 지우면 inprocess 롤백이 깨진다"고 적었으나, 실제로는 `@Value` 기본값이 명시값과 동일해 지금 지워도 동작은 같다. 진짜 근거는 "향후 default drift 대비" | 결정(키 유지) 자체는 타당. **문구만 정정** 필요 |
| L-5 | `feat/phase1-ai-api-config-migration/F-2` | LOW | `AiConfigBindingTest`가 `Environment.getProperty` 기반이라, 실제 빈(`AiCallExecutor.maxRetry`)의 주입값 확인보다 **한 단계 간접적** | 동일 병합 property source를 조회하므로 실질 위험 없음. 더 강한 검증으로 올릴 여지만 있음 |
| L-6 | `feat/phase1-core-http-adapter/F-3` | LOW | `BaseAiHttpAdapter`가 응답을 항상 `String`으로 받은 뒤 파싱 → **메모리 이중화**(String + 파싱된 객체) | 406 회피를 위한 의도적 설계. AI 평가 결과 JSON 크기에선 실용상 무해. 응답이 커지면 재검토 |
| L-7 | `feat/phase1-core-http-adapter/F-4` | LOW | `be/clients/client-ai/build.gradle.kts`의 주석 *"core-api가 tools.jackson.databind.ObjectMapper 사용 중"*이 **1.4a 시점에 사실과 어긋났음** | ⚠️ **1.4b에서 Jackson 3로 되돌렸으므로 지금은 다시 사실일 가능성이 높다.** 확인 후 `obsolete` 처리 여부 판단 |
| L-8 | `refactor/jackson3-db-core/F-2` | LOW | **root `build.gradle.kts`의 subprojects 블록이 전역 J2 `jackson-module-kotlin`을 모든 모듈에 적용** + db-core는 `spring-boot-starter-json`(J2 계열)까지 있어 J2/J3 kotlin 모듈이 클래스패스에 공존(dead weight). 소스 참조는 이미 J3 0건 확인됨 | **전역 빌드 변경이라 blast radius가 커서 이번 PR 범위에서 의도적으로 제외.** 후속으로 root subprojects J2 모듈 제거 + db-core를 `spring-boot-starter-jackson`으로 전환. → CONTEXT 백로그가 실행 항목 소유 |

| L-10 | `fix/timezone-consistency/F-2` | LOW | **`-Duser.timezone` 수정을 실제 Alpine 이미지로는 검증하지 못했다.** QA 환경에 docker가 없어 로컬 Temurin JDK 21 실행 검증(`TZ=UTC java -Duser.timezone=Asia/Seoul` → `systemDefault()=Asia/Seoul`, env를 이김)과 JDK 아키텍처 근거(번들 tzdb)로 대체했다 | 배포 후 **1회 실측이면 확정**된다: prod 로그 타임스탬프 오프셋이 `+09:00`인지 확인(Grafana) 또는 `fly ssh console -C "java -XshowSettings:properties -version"`의 `user.timezone`. → `.claude/TASKS.md`에 사용자 실행 항목으로 등재 |

## 처리 완료 (closed / wontfix / obsolete)

| ID | 출처 | 등급 | 내용 | 처리 |
|----|------|:----:|------|------|
| L-9 | `fix/daily-question-window/F-2` | LOW→**실질 MEDIUM** | **시각 zone 불일치 — 저장은 ambient zone, 조회는 명시적 `Asia/Seoul`.** 저장(`LocalDateTime.now()`, 20곳)과 조회(`now(ZoneId.of("Asia/Seoul"))`, 4곳)가 어긋나 최대 9시간 skew. 실제 발현 2종: ①데일리 메일 — cron이 09:00 KST(=UTC 00:00)라 **날짜가 우연히 맞아떨어져** 동작 중이었고 **이른 쪽 마진 0분**(08:00이었으면 "오늘의 질문"이 `DAILY_QUESTION_NOT_FOUND`) ②코딩 스트릭 — `CodingRankAdapter:18`이 `createdAt.toLocalDate()`(UTC), `CodingQuestService:255`가 KST `today`와 비교 → 00:00~09:00 KST 제출이 전날로 기록돼 스트릭 과소 계산 | ✅ **closed — #336에서 수정.** JVM 기본 zone을 `-Duser.timezone=Asia/Seoul`로 고정(Dockerfile) + Gradle 테스트에도 동일 인자(CI≡prod) + 회귀 가드 `TimezoneConsistencyTest`. 호출지 20곳은 건드리지 않음.<br>🔴 **정정 기록**: 최초 기술서에 *"`Dockerfile`·`fly.toml` 어디에도 TZ 설정이 없다"* 고 적었으나 **사실과 다름** — `be/fly.toml:14`·`k8s/base/core-api.yaml:42`에 **`TZ=Asia/Seoul`이 이미 있었다**(당시 루트 `fly.toml`을 grep해 파일 부재로 오탐). 진짜 원인은 *미설정*이 아니라 **Alpine에 `tzdata`가 없어 `TZ`가 에러 없이 무시되고 UTC로 폴백**한 것. `-Duser.timezone`은 JDK 번들 tzdb로 해석돼 tzdata 없이 동작하므로 이 선택이 정답. 함정 재발 방지용 주석을 Dockerfile·fly.toml·k8s 매니페스트에 남김 |
| L-9-b | `fix/timezone-consistency/F-3` | LOW | 배포 전(UTC naive)/후(KST naive) 저장값 **불연속** — 백필 마이그레이션 미실시 | **wontfix (의도적).** 영향은 배포 당일 00:00~09:00 KST 경계 제출 건에 한정되고, 스트릭은 매일 재계산돼 **self-healing**. 개인 프로젝트 규모에서 백필은 위험 대비 이득이 낮다 |

---

## 이미 다른 곳에 기록된 것 (원장 중복 등재 안 함)

아래는 `.claude/CONTEXT.md`의 **백로그 / 다음 작업** 섹션이 소유한다. 원장에 이중 등재하지 않는다.

- `CodingQuestService.generateProblem`/`submitCode` 트랜잭션 재배치 보류 (#308 MEDIUM) → CONTEXT "서비스 분해 에픽" 잔존 리스크
- `core-api/application-prod.yml`의 Boot 3 잔재 키 `server.error.*` (#308 LOW) → CONTEXT 백로그
- 죽은 설정 `devquest.ai.pass-score`·`interview-questions` (#306 부수 발견) → CONTEXT 백로그
- Jackson 2 잔재 (`CodingQuestService`·인터셉터 2종) (#308 LOW) → CONTEXT 백로그
