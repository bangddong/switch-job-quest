# EKS 학습 퀴즈 — Stage 1 (ECR + 첫 앱 배포)

- 브랜치: `stage/eks-1-ecr` · 날짜: 2026-07-27 · HEAD: 9fce0ae (Stage 1 준비물)
- 재료: `docs/eks-migration-log.md` 2026-07-27 실측 (apply→배포→CrashLoop 3단계 진단→teardown)

## Q1. imagePullSecret 없이 노드가 프라이빗 ECR에서 이미지를 pull한 이유는?
- 사용자 답: 모르겠음
- 정오: ❌ (재검토)
- 정답/근거: **노드 EC2에 붙은 IAM 역할**의 `AmazonEC2ContainerRegistryReadOnly`(nodes.tf, Task 8).
  kubelet이 노드 인스턴스 프로파일 자격증명으로 ECR 토큰을 받아 pull한다. 파드/매니페스트는 인증 정보를
  몰라도 됨. 대조: 외부 레지스트리(GHCR/Docker Hub private)는 IAM이 모르므로 imagePullSecret 수동 필요.
  = "같은 클라우드 안이면 IAM 자동, 밖이면 imagePullSecret 수동."

## Q2. CrashLoop의 첫 원인이 DB가 아니라 Loki였던 이유 + 실무 교훈은?
- 사용자 답: 해당 환경변수(grafanaLokiUrl 등)가 주입되지 않아서 — 정확
- 정오: ✅
- 보강: ①부팅 순서상 **로깅(logback)이 DB보다 먼저** 초기화된다. prod 프로파일이 LOKI appender를
  무조건 켜서, 변수 없으면 `URI with undefined scheme`로 로깅 단계에서 죽음(DB 도달 전).
  ②Fly에는 secrets로 있던 변수라 **환경 의존이 숨어 있었다** → 플랫폼 이전 시 암묵 의존이 드러난다.
  → grep으로 필수 변수 7개 전수 파악이 두더지잡기보다 낫다.

## Q3. Service를 ClusterIP로 고른 이유는?
- 사용자 답: NAT 비용 때문 — 방향이 빗나감(부분)
- 정오: ⚠️ 부분 (재검토)
- 정답/근거: `type: LoadBalancer`는 K8s가 **NLB를 자동 생성** → tofu state 밖 → destroy 후 고아 과금.
  ClusterIP는 그 NLB를 애초에 안 만든다. NAT는 "나가는" 트래픽(노드 공인 IP로 해결), LB는 "들어오는"
  트래픽 — 방향이 반대. 오늘 teardown 고아검사 `LB: (없음)`이 이 선택의 결과.

## Q4. x86 러너로 빌드했다면 파드에 무슨 일이 났을까?
- 사용자 답: 모르겠음
- 정오: ❌ (재검토)
- 정답/근거: **`exec format error` → 즉시 CrashLoop**. 이미지 안 바이너리는 특정 CPU 명령어(x86 vs arm64)로
  컴파일돼 서로 못 읽는다. 노드가 arm64(Graviton)라 x86 바이너리를 실행 못 함. **pull은 성공하고 실행만
  실패**해서 원인 오인하기 쉬움. 해법: 노드 아키텍처에 러너 맞춤(arm 러너) 또는 멀티아키 이미지(buildx).
  apply 전 `architecture: arm64` 실측으로 원천 차단.

## Q5. 클러스터는 destroy됐는데 ECR 이미지가 살아남은 이유는?
- 사용자 답: 모르겠음
- 정오: ❌ (재검토)
- 정답/근거: ECR을 `2-cluster`가 아니라 **`0-bootstrap`(상주 레이어)**에 뒀다(#322). `tofu destroy`는
  실행한 레이어(2-cluster) state만 지운다. 수명이 다른 자산은 레이어를 분리한다 = 이미지(영속)를
  클러스터(휘발)와 함께 죽이지 않는다. → destroy-after-use에서 세션마다 재빌드(5분) 불필요.

## 요약
- 맞음 1 / 부분 1 / 틀림 3. 실습(핸즈온)은 전 단계 성공, 개념 연결은 미성숙.
- 틀린 문제 재검토: Q1(노드 IAM=ECR pull) · Q3(ClusterIP=NLB 고아 회피, 방향 구분) ·
  Q4(아키텍처 불일치=exec format error) · Q5(레이어 분리=수명 분리).
- 스스로 맞춘 Q2는 직접 로그 디버깅한 부분 — 손으로 겪은 것이 남는다는 방증.

<!-- QUIZ-PASSED -->
