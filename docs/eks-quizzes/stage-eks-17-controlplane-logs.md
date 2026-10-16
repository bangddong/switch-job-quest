# EKS 학습 퀴즈 — Stage 5f 컨트롤플레인 로그 (감사 로그)
- 브랜치: stage/eks-17-controlplane-logs   · 날짜: 2026-10-16   · HEAD: 88c9a41

> 퀴즈 전에 개념 5개를 먼저 설명했다(삭제 2줄 · 사칭 · patch 201 · audit 98.0%(설명 당시 98.4% 로 잘못 말함 — QA F-1) · 그룹 선생성 이유). 재료: 일지 2026-10-16.

## Q1. 감사 로그에 `user.username = system:serviceaccount:argocd:argocd-application-controller` 로 Secret `delete` 가 찍혔다. "ArgoCD 가 범인" 이라고 결론 내도 되나?
- 사용자 답: 맞을 것 같다. `user.username` 은 IAM 이 그대로 들어가는데 거기 ArgoCD 컨트롤러라고 적혀 있다.
- 정오: 🟡 부분 정답
  - 맞은 것: **실행 주체**는 컨트롤러가 맞다. 사칭이었다면 `user` 는 사람의 IAM, SA 는 `impersonatedUser` 에 찍힌다(P3 실측).
  - 빠진 것: ArgoCD 는 git 을 따르는 **집행자**다. 지웠다면 대개 prune — git 에서 누가 그 리소스를 뺐는지를 ArgoCD sync 이력 · `git log` 로 이어서 봐야 한다. 감사 로그는 "마지막 손"만 알려 준다.
  - 근거: 일지 2026-10-16 P3 · P4, `k8s/gitops/application.yaml`(prune: true)

## Q2. 로그 그룹을 미리 만들지 않고 로그를 켠 채 apply → destroy 하면 무엇이 남고, 왜 tofu 가 못 지우나? 지금은 어디서 막나?
- 사용자 답: 미리 만들지 않으면 생성 때 임의의 로그 그룹명을 채택하나?
- 정오: ❌ — 이름은 임의가 아니라 **고정** `/aws/eks/<클러스터>/cluster`. EKS 가 같은 이름으로 **보존 무기한 · state 밖** 그룹을 만든다 → destroy 는 state 에 있는 것만 지우므로 고아.
  막는 곳: ① 0-bootstrap 선생성 ② `cluster.tf` precondition(plan 에서 멈춤 — 실패 주입 확인) ③ SOP §9 고아 질의.
  - 근거: `infra/aws-eks/0-bootstrap/log-groups.tf`, `infra/aws-eks/2-cluster/cluster.tf` precondition, 일지 2026-10-15 실패 주입

## Q3. 운영이라면 5종 중 무엇을 상시 켜고 무엇을 끄겠나? 실측 숫자로.
- 사용자 답: 모르겠다.
- 정오: ❌(미응답) — 해설: audit 외 4~5종은 30분에 ~1MB 로 사실상 무료이고 진단에 쓰여 상시 켠다. audit 는 ≈ $0.067/h 로 학습 클러스터엔 +36% 지만, 운영은 클러스터가 커 비율이 작고 **사고 뒤에 소급해서 켤 수 없으므로** 보통 켜 두고 보존 · 로그 클래스로 비용을 조절한다. 같은 숫자로 환경에 따라 답이 반대가 된다.
  - 근거: 일지 2026-10-16 P6, 계획서 §10

## 재검토

### R1. (Q2) EKS 가 스스로 만드는 그룹 이름과, destroy 뒤에도 남는 이유는?
- 사용자 답: `/aws/eks/클러스터명/cluster` 에 자동 생성. destroy 이후에도 사는 이유는 tofu state 로 관리하지 않기 때문.
- 정오: ✅

### R2. (Q3) 학습 클러스터는 audit 를 끄고 운영은 켜 두는 이유를 한 문장으로.
- 사용자 답: 학습에선 접근자가 나뿐이라 추적이 필요 없고, 운영에선 필요하기 때문.
- 정오: ✅ (보충: 운영에서는 사고 뒤 소급 불가라 "미리" 켜 둬야 한다)

## 요약
- 1차: 맞음 0 / 부분 1 / 틀림 2. 틀린 문제 재검토: Q2 → R1 ✅, Q3 → R2 ✅. Q1 은 해설로 보충(실행 주체 ≠ 원인).

<!-- QUIZ-PASSED -->
