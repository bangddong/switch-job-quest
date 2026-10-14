# EKS 학습 퀴즈 — Stage 5c ArgoCD

- 브랜치: stage/eks-14-argocd   · 날짜: 2026-10-14   · HEAD: 2b6fa29
- 재료: `docs/eks-migration-log.md` 2026-10-13 「Stage 5c 유료 세션」 엔트리, `k8s/gitops/`, `k8s/argocd/values.yaml`,
  계획서 `docs/superpowers/plans/2026-10-13-eks-stage5c-argocd.md` §10
- 시각은 전부 KST.
- 형식: 용어를 먼저 설명하고 한 문제씩 서술형. 막히면 가르친 뒤 다른 각도로 다시 묻는다.
- ⚠️ 사용자가 "md 말고 실제 로그를 보며 풀고 싶다"고 했지만 **남은 로그가 없었다**(노드와 함께 소멸, 컨트롤플레인
  로깅 미설정). 그래서 일지에 옮겨 적은 출력을 재료로 썼다. 이 공백은 계획서 §5 에 다음 Stage 후보로 등재했다.

## Q1. 손으로 바꾼 것은 1~2초 만에 되돌아가는데, 커밋은 왜 몇 분이 걸리나

일지 P3(수동 `kubectl scale` → 1~2초 내 복구)와 P5·P6(커밋 반영 1m08s ~ 2m51s)를 나란히 보여 주고 물었다.

- 사용자 답: "1번은 argocd 들렀다 오는 시간치고 너무 짧아 커맨드 자체가 실패한 것 같고 2번이 정상 동작된 것 같은데 ?"
- 정오: ❌ — 명령은 성공했다. `deployment.apps/daily-api scaled` 가 찍혔고 직후 레플리카가 바뀌었다가 되돌아갔다.
- 가르친 것: **watch 대 폴링.** 클러스터 쪽 변화는 API 서버가 ArgoCD 에 바로 **알려 준다**(watch). git 쪽 변화는
  ArgoCD 가 **주기적으로 물으러 가야** 안다(기본 약 3분). 그래서 같은 "되돌리기"인데 경로에 따라 속도가 다르다.

### Q1-2 (재확인)

1. 손으로 바꾼 것을 ArgoCD 는 어떻게 알았나 2. 되돌릴 때 GitHub 에 다녀왔나

- 사용자 답: "1. 주기적으로 물어봤다. 2. 커밋 해시가 같으면 이미 내려받은게 있기 때문에 다녀오지 않았을 것 같은데"
- 정오: 1 ❌ (watch 로 통보받았다) · 2 ✅ (같은 리비전 `d629819…` 의 렌더 결과를 재사용)

### Q1-3 (재확인, 실무 상황)

장애 중 동료가 `kubectl edit` 으로 핫픽스를 넣었고, 다른 동료는 `git revert` 로 되돌리려 한다.

- 사용자 답: "운영 클러스터의 환경 변수는 바로 적용되지만 revert한 내용이 다시 적용되려면 argoCd가 풀링하는 시점까지 기다려야하니 …
  정합성이 맞지 않을 수 있을 것 같아"
- 정오: 🟡 부분 — revert 가 폴링을 기다린다는 것은 ✅. **selfHeal 이 `kubectl edit` 을 1~2초 만에 되돌려 핫픽스 자체가 살아남지
  못한다**는 점과, 기다림을 줄이는 장치(webhook)는 답하지 않았다 → 설명으로 메움.

## Q2. 두 번째 daily-api 파드가 3분 동안 4번 다시 만들어진 이유

- 사용자 답: "모르겠어"
- 정오: ❌
- 가르친 것: HPA 가 `minReplicas: 2` 를 맞추려 2로 올리고(`New size: 2; reason: Current number of replicas below Spec.MinReplicas`),
  ArgoCD 가 git 의 `replicas: 1` 로 되돌렸다. 같은 필드를 두 주인이 다른 값으로 썼다. ArgoCD 는 반복을 감지해
  **백오프**했다(`retrying in 41.6s` → `retrying in 2m41s`). 해결은 git 에서 `replicas` 를 빼는 것
  (`k8s/gitops/apps/kustomization.yaml` 의 `op: remove`).
- 덧붙인 용어(사용자 질문): 백오프 = 재시도는 하되 간격을 늘린다. 서킷 브레이커 = 실패가 쌓이면 시도 자체를 끊는다.
  `CrashLoopBackOff`·`ImagePullBackOff` 의 BackOff 와 같은 말. 간격 규칙(배수·상한)은 확인하지 않았다.

### Q2-2 (재확인)

1. core-api·ai-api 는 git 에 `replicas: 1` 이 있는데 왜 싸우지 않나 2. core-api 에 `minReplicas: 1` HPA 를 붙이면 언제 문제가 드러나나

- 사용자 답 (1차): "모르겠다" → "싸움의 조건 세 가지(같은 필드 · 주인 둘 · 다른 값)" 표를 주고 다시 물음
- 사용자 답 (2차): "아 hpa가 관리하지 않으니까 충돌날게 없는거구나? 그럼 만약 부하가 늘어나서 HPA가 늘리려고 할 때 arocd는 다시 1로 되돌리려 하겠네 ?"
- 정오: ✅ ✅
- 빠진 것: 사용자 영향(스케일아웃이 **정확히 필요한 순간에만** 깨진다 — 테스트 땐 조용하고 장애 때 터진다)은 설명으로 메움.
  🟡 부하 상황의 되돌림은 세션에서 재현하지 않았다. `minReplicas: 2` 충돌과 같은 원리로 추론한 것이다.

## Q3. 이미지 주소를 git 과 Application 으로 나눈 이유

1. 레지스트리 주소를 왜 git 에 안 적었나 2. Application 을 거치지 않고 `kubectl kustomize k8s/gitops/apps` 결과를 그대로 적용하면 파드는

- 사용자 답: "1. 보안적인 측면에서. 향후 작업할 secret 관련 일을 아직 안했잖아 2. sed를 못해서 실제 주소를 바라보지 못하니 fail될 것 같은데"
- 정오: 1 🟡 · 2 🟡
- 근거·교정:
  1. 레지스트리 주소는 시크릿이 아니다. 주소에 **AWS 계정 ID** 가 들어 있고 레포가 **공개**라서다(레포 규칙, 5c D7).
     시크릿 단계(ESO)를 해도 이 문제는 그대로 남는다 — 지키는 대상이 다르다. 공통점은 "git 에는 이름만, 값은 밖에서".
  2. 레지스트리가 없으면 런타임이 Docker Hub(`docker.io/devquest/...`)로 해석해 pull 실패 → `ErrImagePull` → **`ImagePullBackOff`**.
     앱이 시작도 안 했으니 앱 로그는 비어 있고 원인은 `kubectl describe pod` 의 Events 에만 있다.
     🟡 준비 단계에서 렌더 결과에 레지스트리가 없음은 확인했지만 실제 적용은 하지 않았다.

## Q4. application-controller 를 0개로 줄이면

1. 앱은 2. 손으로 바꾼 설정은 3. Application 상태 표시와 그 위험

- 사용자 답 (1차): "모르겠어"
- 정오: ❌
- 가르친 것 (일지 E3 실측): 앱 3개는 계속 동작했다(ArgoCD 는 요청 경로에 없다). 손으로 바꾼 것은 그대로 남았다(비교·교정하는
  주체가 controller 다). Application 은 계속 `Synced` — 실시간 측정값이 아니라 **controller 가 마지막으로 적은 메모**다.
  controller 를 1개로 되돌리자 4초 만에 git 상태로 복구됐다.

### Q4-2 (재확인, 당직 상황)

ArgoCD 화면은 전부 `Synced`·`Healthy` 인데 동료가 "운영 설정이 git 과 다른 것 같다"고 한다. 무엇부터 보나.

- 사용자 답: "gitops니까 git 설정이 먼저일 것 같아. Synced라고 표시해야 할 ArgoCD의 Controller pod가 정상 동작중인지 확인할 것 같아."
- 정오: ✅ — 메모를 쓰는 주체부터 의심했다.
- 보완: 파드가 살아 있어도 멈춰 있을 수 있으니 마지막 비교 시각(`status.reconciledAt`)도 본다(🟡 이번 세션에서 보지 않음).
  "git 이 먼저"는 정답의 기준으로는 맞지만, 당직자의 첫 순서는 **표시를 믿을 수 있는지** 확인하는 것이다.

## Q5. 왜 `k8s/base/` 를 통째로 맡기지 않았나

1. `ingress.yaml` 이 적용되면 AWS 에서 무슨 일이 2. `prune: true` 에서 누가 git 의 postgres 매니페스트를 지우거나 옮기면

- 사용자 답: "아직 우린 db등을 AWS에서 쓸 계획이 없어. 즉 얘네는 ArgoCD가 아직 관리하면 안돼"
- 정오: 🟡 — 결론은 맞고 근거가 사실과 다르다. 학습 클러스터는 세션마다 클러스터 안에서 postgres 를 띄운다(`postgres-static.yaml`).
- 가르친 것:
  1. `internet-facing` Ingress → AWS Load Balancer Controller 가 **ALB 를 만든다**. tofu state 밖이라 destroy 순서를 놓치면
     고아 ALB 로 과금된다(매 세션 "ALB 0" 확인 이유).
  2. prune = "내가 만든 것인데 git 에 없으면 지운다". postgres StatefulSet 이 지워져 세 앱의 DB 연결이 끊긴다.
     PV 는 `persistentVolumeReclaimPolicy: Retain`(`k8s/base/postgres-static.yaml:80`)이라 EBS 는 남지만 `Released` 로 남아
     자동으로 다시 붙지 않는다. 🟡 prune 실동작(E1)은 이번 세션에서 하지 못했다(계획서 §5 등재).

### Q5-2 (재확인)

git 에 없는, `kubectl` 로 따로 올린 metrics-server·postgres 를 prune 이 지우나.

- 사용자 답: "왠지 지울 것 같아 주문서에 없으니까"
- 정오: ❌
- 가르친 것: ArgoCD 는 자기가 만든 리소스에 **추적 표식**을 남기고, prune 은 표식이 있는 것만 본다. 표식 없는 리소스는 비교 대상조차
  아니다. 그렇지 않으면 Application 하나 만드는 순간 시스템 파드·다른 팀 앱이 전부 지워진다. 그래서 postgres 를 한 번이라도
  Application 으로 만들면 그때부터 prune 대상이 된다 — "처음부터 맡기지 않는다"가 의미 있는 이유.
  🟡 3.x 기본 표식이 라벨인지 어노테이션인지는 클러스터에서 직접 보지 않았다.

### Q5-3 (재확인, 다른 각도)

동료가 `kubectl run debug --image=busybox` 로 띄운 파드를 두고 퇴근했다. 1. ArgoCD 가 지우나 2. 남아 있다면 누구 책임이고 어떻게 찾나

- 사용자 답: "1. 아니 안지우겠지! 애초에 ArgoCD가 만든 애가 아니니까! 2. 굳이 책임을 따진다면 그 동료일거고 찾는 방법은 ... git과 비교한다 ?"
- 정오: 1 ✅ · 2 🟡 — 책임은 맞다. 찾는 방법은 git 과 비교해서는 안 나온다: ArgoCD 의 diff 는 **표식이 있는 리소스만** 다루므로
  이 파드는 ArgoCD 화면에도 diff 에도 나오지 않는다. **클러스터 쪽에서 표식이 없는 리소스를 찾아야 한다.** 실무에서는 아예
  운영 클러스터의 `kubectl` 쓰기 권한을 막거나 정책 도구로 막는다 → 설명으로 메움.

## 요약

| 문제 | 1차 | 재확인 | 최종 |
|---|---|---|---|
| Q1 watch 대 폴링 | ❌ | Q1-2 반 ✅ · Q1-3 부분 | 닫음 |
| Q2 HPA ↔ ArgoCD 싸움 | ❌ 모르겠다 | Q2-2 ✅ ✅ (2차) | 닫음 |
| Q3 이미지 주소 분리 | 🟡 🟡 | — (교정 설명) | 닫음 |
| Q4 controller 0개 | ❌ 모르겠다 | Q4-2 ✅ | 닫음 |
| Q5 base 통째로 / prune | 🟡 | Q5-2 ❌ → Q5-3 1 ✅ · 2 🟡 | 닫음 |

- 1차에서 온전히 맞은 문제 0 / 5 (부분 2). 재확인 문제의 핵심 질문은 모두 맞혔다(Q2-2, Q4-2, Q5-3 의 1).
- ⚠️ **사용자가 직접 답하지 않고 설명으로만 메운 곳**: Q1-3 의 "selfHeal 이 핫픽스를 지운다"와 webhook, Q2-2 의 사용자 영향,
  Q3 두 문항의 정확한 이유(계정 ID · `ImagePullBackOff`), Q5-3 의 2(표식 없는 리소스는 클러스터 쪽에서 찾는다).
  "닫음"은 재확인 문제를 맞혔다는 뜻이지 이 지점들을 스스로 답했다는 뜻이 아니다. 다음에 다시 볼 곳이다.
- 틀린 문제 재검토:
  - Q1 — 일지 2026-10-13 P3 · P5 · P6 시각표
  - Q2 — 일지 P4, `k8s/gitops/apps/kustomization.yaml` (`op: remove` `/spec/replicas`)
  - Q3 — `k8s/gitops/application.yaml` (`kustomize.images`), 계획서 D7
  - Q4 — 일지 E3
  - Q5 — `k8s/base/kustomization.yaml` 주석, `k8s/base/postgres-static.yaml:80`, 계획서 §5 (E1 미실행)

### 메타 교훈

**다섯 문제가 모두 "ArgoCD 가 무엇을 보고 있고 무엇을 안 보고 있나"를 묻는 문제였다.**

| 겉보기 | 실제 |
|---|---|
| ArgoCD 는 git 을 계속 본다 | git 은 **주기적으로** 묻고, 클러스터는 **통보**받는다 (Q1) |
| `replicas` 는 한 곳이 정한다 | 주인이 둘이면 서로 덮어쓴다 — 조용하다가 부하 때 터진다 (Q2) |
| `Synced` 는 지금 상태다 | controller 가 **마지막에 적은 메모**다 (Q4) |
| 주문서에 없으면 지운다 | **자기 표식이 있는 것만** 지운다 — 그래서 한 번 맡기면 되돌리기 어렵다 (Q5) |

5b 퀴즈의 교훈("누가 그 일을 하는가")과 같은 줄기다. 이번엔 "그 주체가 **무엇을 보고** 판단하는가"까지 내려갔다.

<!-- QUIZ-PASSED -->
