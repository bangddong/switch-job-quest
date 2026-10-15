# EKS 학습 퀴즈 — Stage 5d 로그 수집 (CloudWatch Logs)
- 브랜치: stage/eks-15-logging   · 날짜: 2026-10-15   · HEAD: e1220be (퀴즈 시작 시점)
- 재료: `docs/eks-migration-log.md` 2026-10-14 · 2026-10-15 엔트리, `k8s/logging/*.yaml`, `infra/aws-eks/0-bootstrap/log-groups.tf`, `docs/eks-session-sop.md` §5
- 형식: 개념 정리 → 서술형 1차 → (막히면 설명) → 다른 각도 서술형 2차

## Q1. `p4-nosuch` 이벤트가 `kubectl get events` 로는 6줄인데 CloudWatch 에는 18건이다. 왜 다른가, 시도 횟수를 셀 때 주의점은?
- 사용자 답: "CloudWatch 이벤트와 kubectl 이벤트랑 수집하는 게 달라서?"
- 정오: △ (방향만) — 근거: 2026-10-15 events 그룹 질의로 실측. `metadata.uid` 6개, 같은 uid 가 `count` 1→2→3… 으로 재수신
  (Pulling `025f7665…` count 1·2·3, BackOff `00219efa…` 1~5). 반복 시 API 객체의 `count` 가 올라가고(kubectl 은 그 객체 목록), watch 는 갱신마다 1건.
- 설명: 시도 횟수는 행 수가 아니라 uid 별 `max(count)`.

### Q1-2. 이벤트 수집기가 재시작하면 `count(*) by reason` / `max(count) by uid` 는?
- 사용자 답: "이벤트는 늘겠지. 어쨌든 이벤트가 발생했으니까. metadata 는 모르겠다. 로그성이라면 안 늘 것 같아"
- 정오: ✅ (결론 둘 다 맞음, ①의 이유 보정) — 근거: `k8s/logging/fluent-bit-events.yaml` `kube_retention_time 5m`.
  ① 새 이벤트가 아니라 최근 5분치 재수신으로 중복 → 커진다. ② 중복 행도 같은 uid·count → max 불변(멱등). at-least-once 전달.

## Q2. 로그 그룹 이름 오타 시 — 지금 설정 vs `auto_create_group On`+권한 있음, 어느 쪽이 나은가?
- 사용자 답: "모르겠어"
- 정오: ❌ → 설명 — 근거: `k8s/logging/fluent-bit.yaml` `auto_create_group Off`, `2-cluster/irsa-fluent-bit.tf` 에 `CreateLogGroup` 없음,
  `log-groups.tf` 주석. 지금 = 수집기 에러로 시끄럽게 실패(P1 에서 발견). On = 오타 이름으로 state 밖·보존 무기한 그룹을 조용히 생성,
  원래 그룹은 비어 장애 때 발견. 실례: 계정의 `/aws/lambda/test`(보존 무기한 고아).

### Q2-2. 같은 원리 — ESO 가 없는 시크릿 이름에 빈 Secret 을 만들어 줬다면?
- 사용자 답: "기동은 성공하겠지만 필요한 값이 없으니 런타임에서 에러. 같은 케이스의 조용한 실패"
- 정오: ✅ — 근거: `k8s/README.md` §2 (`APP_SECRET_NAME_PLACEHOLDER` 미치환 시 SecretSyncError). 보강: 원인과 증상 사이 거리가 멀어진다.

## Q3. 2차 세션에서 `cd … && tofu apply` 로 쳤고 정규식도 맞는데 마커가 안 생겼다. 왜? 무엇이 잡았나? 없었다면?
- 사용자 답: "추정이지만 tofu apply 로 시작하지 않기 때문에?"
- 정오: ❌ (1차 세션의 원인과 혼동) — 근거: `.claude/settings.json:62` 상대경로 등록, 직전 `cd` 로 셸 작업 폴더가 2-cluster 에 남음(🟡 강한 정황),
  `eks-session-marker.sh:39` 정규식은 매칭. 잡은 것 = SOP §5 마커 육안 확인. 없었다면 리퍼 꺼진 채 과금(09-16 15.8시간 전례).

### Q3-2. destroy 뒤 청소 훅도 안 돌았다. 옛 마커가 남았다면?
- 사용자 답: "실제론 죽었지만 살아있다고 표시되어 있으니 계속 죽이려 시도했으려나?"
- 정오: △ — 리퍼의 빈 destroy 는 무해(하트비트 훅이 과금 리소스 0 이면 마커 자가 삭제 — 마커 훅 주석 기준 🟡).
  진짜 위험 = **다음 세션의 마커 확인이 옛 파일로 거짓 통과**. → `ls` 가 아니라 `cat` 으로 `applied_at_h` 시각 확인.
  이 퀴즈로 튜토리얼 5d-2 의 `ls` 를 `cat` 으로 고치고 SOP §5 에 반영했다.

## Q4. 수집기를 앱보다 먼저 깐 이유, 그래도 놓칠 수 있는 파드는?
- 사용자 답: "앱의 설치 과정을 휘발되거나 문서로 남기는 것 외에 정상적인 로그 수집기를 통해 보고 싶으니까"
- 정오: ① ✅ / ② 미답 → 설명 — 근거: 일지 2026-10-15 `[결정]`(세션 중 내 판단 — 사용자 주장이 아니었음을 정정),
  tail `Read_from_Head` 기본 Off(🟡). ② 60초(`Refresh_Interval`) 안에 사라지는 파드 — 짧은 Job·즉시 삭제 파드. CrashLoop 는 같은 파드라 결국 수집.

### Q4-2. 5초짜리 CronJob 로그가 CloudWatch 에 없다. 설정 변경 없이 결과를 보려면?
- 사용자 답: "로그 쪽 말고 이벤트 쪽 보면 될까?"
- 정오: ✅ — events 그룹(API watch 라 파드 수명과 무관): SuccessfulCreate · Completed · BackoffLimitExceeded. 한계: 성패만, 앱 에러 내용은 없음.

## 요약
- 1차: 맞음 1(Q4①) / 부분 1(Q1) / 틀림·모름 3(Q2·Q3·Q4②). 재확인(2차): 맞음 3(Q1-2·Q2-2·Q4-2) / 부분 1(Q3-2).
- 틀린 문제 재검토: Q2 → Q2-2 에서 같은 원리를 ESO 에 적용해 맞힘. Q3 → 설명 후 Q3-2 에서 방향은 잡음, 거짓 통과 지점은 설명으로 보충.
  Q4② → Q4-2 에서 대안(이벤트) 도출.
- 퀴즈가 찾은 문서 구멍 2건 반영: 튜토리얼 5d-2 마커 확인 `ls`→`cat`+시각, 5d-6 시도 횟수는 `max(count) by uid`.

<!-- QUIZ-PASSED -->
