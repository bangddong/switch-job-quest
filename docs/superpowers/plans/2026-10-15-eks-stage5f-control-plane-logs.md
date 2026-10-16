# EKS Stage 5f — 컨트롤플레인 로그 계획서

> 2026-10-15 작성 · 브랜치 `stage/eks-17-controlplane-logs` · 상태: **완료 (2026-10-16 세션, P1~P8 통과 — §10)**
>
> EKS 트랙의 마지막 선택 과제. 5d 가 파드 로그 · 이벤트를 남겼다면, 5f 는 **클러스터 자체(API 서버)가 본 것**을 남긴다.
> 5d 계획서 「이번에 하지 않는 것」 첫 행의 후속이다. 세션 절차는 `docs/eks-session-sop.md` 를 따른다.
> 🔴 = 코드·실측으로 확인 · 🟡 = 문서·정황.

## 1. 무엇을 확인하려는가

한 문장: **"누가 · 언제 · 무엇을 했나"를 클러스터가 사라진 뒤에도 API 서버의 감사 로그로 답할 수 있다는 것을 확인한다.**

5d 의 로그는 *앱이 무엇을 말했나*, 이벤트는 *쿠버네티스가 무엇을 했나* 다. 둘 다 **요청한 주체**를 모른다.
감사(audit) 로그는 API 서버에 들어온 요청마다 *누가(user) · 무엇을(verb · resource) · 결과(code)* 를 남긴다.

## 2. 결정 사항

| # | 결정 | 선택 | 근거 |
|---|---|---|---|
| D1 | 🔑 기존 결정 반전 | `cluster.tf` 의 *"control-plane 로깅은 비용 회피로 생략"* → **변수 토글, 기본 끔** | 상시 켜지 않는 이유(비용)는 유효하다. 세션에서만 켠다. `db_mode` 와 같은 관례. 반전 표시는 `cluster.tf` 주석에 원문을 남기고 취소선 |
| D2 | 로그 종류 | 세션에서 **5종 전부**(api · audit · authenticator · controllerManager · scheduler) | 종류별 양을 실측해 "평소엔 무엇만 켤 만한가"를 숫자로 답하려면 다 켜 봐야 한다(P6) |
| D3 | 🔑 로그 그룹 | 0-bootstrap 에 `/aws/eks/devquest-eks/cluster` 를 **미리** 만든다(보존 30일) | 이름을 EKS 가 정한다. 없으면 EKS 가 **보존 무기한 · state 밖** 그룹을 만든다. 5d D3 과 같은 판단 |
| D4 | 수집기 맵과 분리 | 별도 리소스 `aws_cloudwatch_log_group.eks_control_plane` | `irsa-fluent-bit.tf:20` 이 `learning_log_groups` 의 **모든 값**에 쓰기 권한을 준다. 같은 맵에 넣으면 수집기가 감사 로그에 쓸 수 있게 된다 |
| D5 | 이름 어긋남 방지 | 2-cluster `aws_eks_cluster` 의 **precondition**: 로그를 켰으면 0-bootstrap output == `/aws/eks/${var.cluster_name}/cluster` | 5d U-2(`cluster_name` 이 0-bootstrap 에 없음). 단일화 대신 plan 단계 검사로. 실패 주입 확인 🔴(0-bootstrap 미적용 상태 → `Resource precondition failed`) |
| D6 | 세션 구성 | 5e 와 같은 구성(노드 3 · ArgoCD Application 1회 · Fluent Bit) + `-var control_plane_log_types=[…5종]` | 감사 로그에 **사람(나)과 컨트롤러(ArgoCD)** 두 주체가 함께 찍혀야 P2~P4 를 구분할 수 있다 |
| D7 | 🔴 계정 ID | 감사 로그의 `user.username` · authenticator 로그에 IAM ARN(계정 ID 포함)이 찍힌다 | 일지 · 튜토리얼 · 퀴즈에 옮길 때 `<account>` 로 지운다 |

## 3. 통과 기준

| # | 무엇을 | 어떻게 보나 | 통과 |
|---|---|---|---|
| P1 | 5종이 들어온다 | `describe-log-streams` | `kube-apiserver-*` · `kube-apiserver-audit-*` · `authenticator-*` · `kube-controller-manager-*` · `kube-scheduler-*` 스트림 |
| P2 | 🔑 **누가 지웠나** | `kubectl delete pod <core-api>` → Insights 로 audit 에서 `verb=delete` · 그 파드 이름 | `user.username` 에 내 IAM 주체(계정 ID 가림), 시각 일치 |
| P3 | 🔑 **거부된 요청이 남는다** | `kubectl --as=system:serviceaccount:default:default get secrets` | audit 에 `responseStatus.code=403` · `authorization.k8s.io/decision=forbid` |
| P4 | 🔑 **selfHeal 의 두 주체** | `kubectl delete deploy ai-api` → ArgoCD 가 되살림 | 같은 리소스에 `delete`(나) → `create`(`system:serviceaccount:argocd:argocd-application-controller`) 가 몇 초 간격으로 |
| P5 | IAM ↔ K8s 사용자 연결 | authenticator 로그 | 내 IAM ARN 이 어떤 K8s 사용자로 매핑됐는지(Access Entry) |
| P6 | 종류별 양 · 비용 | 스트림 접두어별 `stats count(*)` + 그룹 `IncomingBytes` | 종류별 비중 기록 → "평소엔 무엇만 켤 만한가" 판단 근거 |
| P7 | 뒷정리 · 고아 | SOP §8 · §9 (5f 개정 쿼리) | 고아 0. `/aws/eks/` 그룹은 **`devquest-eks/cluster` 1개, 보존 30일** — EKS 가 따로 만든 그룹 0 |
| P8 | destroy 뒤에도 읽힌다 | destroy 후 P2 · P4 질의 재실행 | 같은 결과 |

## 4. 세션 전 준비 ($0)

1. 0-bootstrap **로컬 apply**(승인 게이트) — `Plan: 1 to add`(`aws_cloudwatch_log_group.eks_control_plane`). 5d D5 와 같은 이유(CI 는 머지 때만 apply).
2. 2-cluster plan 에 `-var control_plane_log_types=[…]` → precondition 통과 확인.
3. 세션 예상: 60~70분 ≈ $0.20 + 로그 수집(감사 로그 양 미측정, 🟡 수십 MB → $0.05 미만).

## 5. 이번에 하지 않는 것

| 항목 | 왜 |
|---|---|
| 감사 정책(무엇을 얼마나 자세히 남길지) 조정 | EKS 는 감사 정책을 사용자가 바꿀 수 없다(관리형). 양을 줄이는 수단은 **종류를 끄는 것**뿐이다 |
| 감사 로그 기반 알람(예: 403 급증) | 5d 와 같은 이유 — 수집이 먼저, 알람은 SNS · 비용이 따로 |
| `cluster_name` 을 0-bootstrap 과 단일화 | D5 의 precondition 으로 어긋남은 plan 에서 막힌다. 단일화는 레이어 간 변수 공유 설계라 별도 작업 |

## 10. 결과

> 2026-10-16 세션 09:21~09:51 KST(30분) · **P1~P8 전부 통과** · ≈ $0.14(클러스터 $0.093 + 로그 수집 $0.046). 상세 · 원문은 일지 같은 날짜.

| # | 결과 | 계획과 달랐던 것 |
|---|---|---|
| P1 | ✅ 스트림 6종 × 2 | 🔴 `cloud-controller-manager` 가 고르지 않았는데 온다. ×2 는 컨트롤플레인 인스턴스 2대 |
| P2 | ✅ 내 IAM 주체 delete 200 | 🔴 delete 가 **2줄** — 1.1초 뒤 `system:node:…`(kubelet)가 실제 제거. `sourceIPs` 에 **집 공인 IP** → D7 확장 |
| P3 | ✅ `list secrets 403 decision=forbid` | 🔴 `user.username` 은 **나**, SA 는 `impersonatedUser` — 계획의 질의(`user.username = SA`)는 0건이었다 |
| P4 | ✅ delete(나) → **0.9초 뒤** ArgoCD | 🔴 ArgoCD 는 `create` 가 아니라 **`patch` 201**(ServerSideApply). 계획의 `verb=create` 질의는 0건 |
| P5 | ✅ `access granted … groups="[]" username=<IAM ARN>` | `uid` 에 IAM 고유 ID(AIDA…)도 → 가림 |
| P6 | ✅ audit **98.0%**, 평상시 1.47MB/분 ≈ $0.067/h(클러스터의 +36%), 나머지 합 ~$0 | 설치 직후 2분 30MB 폭주(🟡 대형 CRD 본문 추정) |
| P7 | ✅ 고아 0, `/aws/eks/` 는 미리 만든 그룹 1개 · 보존 30 | — |
| P8 | ✅ destroy 후 P4 질의 같은 3행 | — |

**결론**: 평소 켤 만한 것은 audit 를 뺀 종류(사실상 무료). audit 는 "누가 그랬나"를 물을 세션에서만 — 기본 끔 유지(D1)가 숫자로 섰다.

### 부수 발견 (이번에 처리하지 않음)

| 항목 | 왜 지금 안 하나 |
|---|---|
| 세션 마커 훅이 **명령 본문 속 apply 글자**(일지 heredoc)에 매칭해 `applied_at` 을 다시 썼다(09:21 → 09:38, 리퍼 기준 17분 지연). L-69 와 같은 종류 | 하네스 동결 — 제품 작업을 막지 않았다. 일지 쓰기를 Edit 도구로 바꿔 회피 |
| 마커 청소 훅 미동작(destroy 가 `cd … &&` 형태) — 3번째 | 같은 이유. SOP 의 수동 삭제 절차로 처리 중(L-70) |
| 설치 직후 audit 폭주의 원인 확인(요청 본문 크기 분해) | 결론(audit 는 세션에서만)을 바꾸지 않는다 |
