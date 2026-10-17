# EKS 학습 퀴즈 — 튜토리얼 재현 검증 Stage 4 → 5f (R1 · R2)
- 브랜치: stage/eks-18-tutorial-repro   · 날짜: 2026-10-17   · HEAD: 36bcd9d
- 재료: `docs/eks-migration-log.md` 「튜토리얼 재현 검증 R1」「R2 (R2a/R2b)」 실측
- 진행: 개념 3개(우선순위·선점 / state 밖 리소스와 삭제 순서 / 영속 로그 그룹)를 먼저 설명한 뒤 출제

## Q1. ALB 컨트롤러에 메모리 requests 를 적었는데도 core-api 가 쫓겨났다. 왜 Pending 이 아니라 선점인가? 막는 방법 하나는?
- 사용자 답: 노드 내 여유 메모리가 없어서? 막으려면 필수 노드 설정을 해야 할 것 같다
- 정오: △ (절반)
  - 자리 부족은 **조건**이지 원인이 아니다. 원인은 **우선순위** — LBC 차트 기본값 `priorityClassName: system-cluster-critical`(≈20억) > core-api(0). 높은 쪽이 오면 낮은 쪽을 쫓아내고, 같거나 낮으면 Pending 으로 줄을 선다. requests 는 *"자리가 있나"* 만 정하고 *"누가 비키나"* 는 우선순위가 정한다.
  - 막는 법: ⓐ 노드 증설(R1 실제 해결 — 노드그룹 desiredSize=2) ⓑ LBC priorityClassName 하향 ⓒ 앱에 PriorityClass 부여 ⓓ taint/nodeSelector 로 시스템·앱 노드 분리 — "필수 노드" 는 ⓓ 에 가까우나 범위가 넓었다
- 근거: 일지 R1 — `helm show values eks/aws-load-balancer-controller --version 3.5.0` → `priorityClassName: system-cluster-critical`, `FailedScheduling pod/core-api-… 1 Insufficient memory, 1 Too many pods`

## Q2. destroy 시 Karpenter 정리 스크립트가 **노드그룹 삭제 뒤 · 클러스터 삭제 전** 이어야 하는 이유는?
- 사용자 답: 의존 관계에 따라 서로 관리하는 부분이 다르기 때문에?
- 정오: △
  - *뒤* 여야 하는 이유: Karpenter 컨트롤러는 노드그룹 노드 위에서 돈다 — 살아 있는 동안 지우면 갈 곳 없는 파드를 보고 **EC2 를 다시 띄운다**.
  - *전* 이어야 하는 이유: Karpenter EC2 는 tofu state 밖 — 남으면 ① 과금 고아 ② ENI·SG 연결로 서브넷·SG 삭제가 막혀 destroy 실패.
- 근거: 일지 R2a — 5b-7 미검증 경로를 실물로 확인(Karpenter 노드 `t4g.small` 을 살린 채 destroy → 정리 스크립트가 실제 EC2 종료)

## Q3. 08-12 처럼 막혀서 코드를 열어 해결한 세션을 *재현* 으로 세지 않는 이유는?
- 사용자 답: 모르겠어
- 정오: ❌
  - 재현 검증이 시험하는 것은 **사람이 아니라 문서**다. 독자는 `eks-tutorial-steps.md` 만 갖는다. 코드를 열면 문서의 빈칸을 **내 지식이 메워** "통과" 로 끝나고 결함은 *숨는다*. R1 의 daily-api 배포 단계 누락이 그 예 — 코드를 열었으면 바로 배포하고 넘어가 문서에는 계속 빠져 있었다.
  - 레포 반복 실패 형태 *"검사가 주장보다 헐겁다"* 와 같은 구조: 주장 = *문서가 맞다*, 헐거운 검사 = *결국 됐다*.
- 근거: 계획서 `docs/superpowers/plans/2026-10-16-eks-tutorial-repro-stage4-5f.md` 「규칙」 1; 일지 R1 — `services "daily-api" not found` → 튜토리얼 4-0b 신설

## 재검토 (틀린·부분 문제)

### Q1'. 노드 1대, core-api(우선순위 0)가 자리를 차지. 같은 0 인 새 파드가 오면?
- 사용자 답: 둘 다 0 이면 새 파드가 못 뜰 것 같다
- 정오: ✅ — Pending. 보충: *"정할 수 없어서"* 가 아니라 *더 높지 않으면 선점 자격이 없어서* 먼저 온 쪽이 남는다

### Q2'. 정리 스크립트를 노드그룹 삭제 **전** 에 돌리면?
- 사용자 답: 카펜터가 다시 살릴 것 같다
- 정오: ✅

### Q3'. 재현 중 막혀서 `.tf` 를 열어 해결했다면 그다음 할 일은?
- 사용자 답: 기록해야 한다 — 어떤 문제로 막혔고 어떻게 해결했는지
- 정오: ✅ (보충) — ① 기록 ② **문서 수정** ③ **고친 문서만 보고 그 단계를 다시 통과**. 기록만 하면 일지엔 남아도 튜토리얼은 그대로다

## 요약
- 1차: 맞음 0 / 부분 2 / 틀림 1. 재검토 3문제 전부 ✅.
- 약했던 축: **"조건"과 "원인" 구분**(Q1 — 자리 부족 vs 우선순위), **재현 = 문서 검증** 이라는 목적(Q3).

<!-- QUIZ-PASSED -->
