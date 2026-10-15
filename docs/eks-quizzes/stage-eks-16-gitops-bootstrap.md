# EKS 학습 퀴즈 — Stage 5e 시크릿·DB 까지 ArgoCD (sync wave)
- 브랜치: stage/eks-16-gitops-bootstrap   · 날짜: 2026-10-15   · HEAD: f500b72 (퀴즈 시작 시점)
- 재료: `docs/eks-migration-log.md` 2026-10-15 「Stage 5e 세션」 엔트리, `k8s/gitops/apps/kustomization.yaml`, `k8s/gitops/application.yaml`, `k8s/base/postgres-static.yaml`, `infra/aws-eks/2-cluster/addons.tf`
- 형식: 개념 복습 → 서술형 1차 → (막히면 설명) → 다른 각도 서술형 2차

## Q1. PVC 를 wave 1, StatefulSet 을 wave 2 로 나누면?
- 사용자 답: "PVC가 StatefulSet을 기다렸을까?"
- 정오: △ (방향 맞음) — 근거: `postgres-static.yaml` `volumeBindingMode: WaitForFirstConsumer`, `kustomization.yaml` wave 2 주석.
- 설명: PVC 는 **파드가 배치될 때까지 Pending** → ArgoCD 는 Progressing 으로 보고 wave 2 로 안 넘어간다 → 파드는 wave 2 를 기다린다 → **교착**(에러 없이 `waiting for healthy state of PersistentVolumeClaim/…` 만).
  실측: SC · PV 05:55:53 동시 생성 → postgres-0 Ready 05:56:10.

### Q1-2. 반대로 StatefulSet wave 1, PVC wave 2 면?
- 사용자 답: "PVC를 참조할 수 없으니 뜨지 않으려나?"
- 정오: ✅ — StatefulSet 이 `claimName: postgres-data` 로 이름 참조 → 파드 Pending → StatefulSet 미Healthy → wave 2 영원히 안 옴. Q1 의 거울상. ⇒ 서로 필요한 것은 **같은 번호** 외엔 답이 없다.

## Q2. 비밀번호가 어긋난 상태에서 별도 Job 이 `psql -h postgres` 로 ALTER USER 하면? postStart 는 왜 되나?
- 사용자 답: "모르겠어!"
- 정오: ❌ → 설명 — 근거: SOP §6b `pg_hba.conf` 실측(local/127.0.0.1 trust · 나머지 scram-sha-256), 일지 P3(파드 IP 검사 exit 2 ↔ 소켓 ALTER 성공).
- 설명: Job 은 다른 파드 → TCP → scram 줄 → Secret 의 새 값 vs DB 의 옛 해시 → **접속부터 실패**. 고쳐야 할 순간에만 실패하는 도구.
  postStart 는 컨테이너 안 소켓 → trust → 비밀번호 없이 들어가 고친다.

### Q2-2. 동기화 확인에 `-h 127.0.0.1` 을 쓰면 안 되는 이유?
- 사용자 답: "127.0.0.1은 trust라 비밀번호를 묻지 않아. 맞는지 안 맞는지 검증이 안 된다. 예전에 한 번 실수했었지!"
- 정오: ✅ — 08-12 실제 실수(SOP §6b). Q2 의 Job 실패와 같은 표의 두 얼굴: **접속 경로가 인증 여부를 바꾼다.**

## Q3. ⓪(Application 삭제)을 건너뛰고 ExternalSecret 을 지우면? Application 을 지웠는데 왜 리소스가 남았나?
- 사용자 답: "1. application이 참조 중이라 삭제에 실패했으려나? 2. 정의 안 된 리소스가 있었나?"
- 정오: ❌ / ❌ → 설명 — 근거: SOP §8 ⓪, `application.yaml`(finalizer 없음 · selfHeal true), 일지 teardown 엔트리.
- 설명: ① 삭제는 **성공**하고 selfHeal 이 1~2초 만에 되살린다(5c 실측) — 지웠다고 믿고 destroy 로 넘어간다.
  ② `resources-finalizer.argocd.argoproj.io` 가 **없어서** cascade 가 없다 → 관리만 끊긴다. 있었다면 앱·시크릿이 줄줄이 지워지고 postgres 만 `Delete=false` 로 남는다.

### Q3-2. 그럼 `Delete=false` 는 언제 일하나?
- 사용자 답: "delete 될 때 마크 같아. 얘는 지우지 마라 하는"
- 정오: △ — `Prune=false` = git 에서 빠질 때(P4 실측), `Delete=false` = **Application 이 cascade 로 지워질 때**(finalizer 있을 때). 지금은 대기 중인 보험.

## Q4. 5d 도 metrics-server `.12` 였는데 왜 문제가 없었나? 왜 다른 애드온은 고정 안 했나?
- 사용자 답: "모르겠어"
- 정오: ❌ → 설명 — 근거: CloudTrail `CreateAddon`(10-14 · 10-15 = `.12`), 일지 관문(JSON 200 · protobuf 503), `addons.tf` 고정 주석.
- 설명: ① `.12` 가 깬 것은 본업(지표)이 아니라 **openapi 집계에 끼워 넣는 부수 기능**. 5d 에는 그 경로(protobuf openapi)를 쓰는 소비자가 없었다 → ArgoCD 가 들어오자 터짐. *"이전에 통과" = "그때 쓴 경로가 멀쩡"*.
  ② 고정하면 보안·버그 수정이 함께 멈추고, 카탈로그에서 내려가면 plan 이 실패한다. 깨진 하나만 고정하고 나머지는 CloudTrail 로 기록하는 편이 싸다.

### Q4-2. `.11` 이 내려가 새 빌드로 올릴 때, 싸게 확인하려면?
- 사용자 답: "깨져봐야 알지 않나"
- 정오: △ — 띄워 봐야 아는 건 맞다. 다만 **어디서 깨지느냐가 비용**: apply 직후 ArgoCD 설치 전 protobuf `/openapi/v2` 한 줄(튜토리얼 5e-2 관문) → 503 이면 즉시 destroy ≈ $0.05 (1차 세션처럼 다 깐 뒤 알면 $0.15). 과금 전엔 릴리스 노트로 업스트림 수정 포함 여부($0, 확증 아님).

## 요약
- 1차: 부분 1(Q1) / 틀림·모름 4(Q2 · Q3 ①② · Q4). 재확인(2차): 맞음 2(Q1-2 · Q2-2) / 부분 2(Q3-2 · Q4-2).
- 틀린 문제 재검토: Q2 → Q2-2 에서 같은 표(pg_hba)를 스스로 적용해 맞힘. Q3 → 설명 후 Q3-2 에서 "지우지 마라" 방향은 잡음, 계기(prune vs cascade) 구분은 설명으로 보충.
  Q4 → Q4-2 에서 "띄워 봐야 안다"는 맞췄고, 관문으로 비용을 줄이는 지점은 설명으로 보충.
- 최대 오개념: *"참조 중이면 삭제가 막힌다"* — Kubernetes 는 기본적으로 참조 잠금이 없고, 되살리는 것은 **컨트롤러(selfHeal)** 다.

<!-- QUIZ-PASSED -->
