# EKS Stage 5e — 시크릿·DB 까지 ArgoCD 로 (sync wave) 계획서

> 2026-10-15 작성 · 브랜치 `stage/eks-16-gitops-bootstrap` · 상태: **계획 (세션 전)**
>
> 5c 가 앱 3종만 ArgoCD 에 맡기고 남긴 손 단계(ESO 설치 → 시크릿 참조 → postgres → 비밀번호 동기화)를
> 전부 옮긴다. 5c 계획서 D5 · U-7 · 「이번에 하지 않는 것」의 후속이다.
> 세션 절차는 `docs/eks-session-sop.md` 를 따르고, 이 문서는 **그 위에 얹는 5e 고유 부분**만 적는다.
> 🔴 = 코드·실측으로 확인 · 🟡 = 문서·정황으로만 확인(실클러스터 미확인).

## 1. 무엇을 확인하려는가

한 문장: **Application 하나를 넣으면, ArgoCD 가 ESO → 시크릿 → postgres → core-api → 나머지 앱 → HPA 순서를
지켜 클러스터를 끝까지 세우고, 사람이 치던 비밀번호 동기화는 postgres 가 뜰 때 스스로 한다는 것을 확인한다.**

쪼개면:

1. **sync wave** — 같은 Application 안에서 리소스에 번호를 붙이면, ArgoCD 는 앞 번호가 *Healthy* 가 된 뒤에 다음 번호를 적용한다.
2. 🔑 *Synced(적용됨)* 와 *Healthy(쓸 수 있음)* 는 다르다. 순서 제어는 **Healthy 를 기다리는 것**이라서 의미가 있다.
3. 지워지면 안 되는 것(DB)을 **prune 이 켜진 Application 안에** 두는 방법 — 리소스 단위 `Prune=false`.
4. 사람 단계 제거 — `ALTER USER` 를 postgres 컨테이너의 **postStart 훅**으로.

## 2. 결정 사항

| # | 결정 | 선택 | 근거 |
|---|---|---|---|
| D1 | 🔑 범위 | **전부**: ESO 설치 · SecretStore · ExternalSecret 3종 · postgres(static PV) · 앱 3종 · NetworkPolicy · HPA (2026-10-15 사용자 결정) | 남는 손 단계 = ArgoCD helm 설치 + Application 1회 apply. 5c U-7 이 가로막힘으로 지목한 비밀번호 동기화를 D6 으로 없앤다 |
| D2 | 🔑 Application 구조 | 기존 `devquest-apps` **하나**를 **multi-source** 로: ① ESO helm 차트 ② git `k8s/gitops/apps` | §5 U-3. wave 는 **Application 하나 안에서만** 순서를 보장한다. 기각: app-of-apps(자식 health 판정용 `argocd-cm` 커스텀 필요 + 자식이 git 에서 렌더돼 **계정 ID 가 든 IRSA ARN 을 sed 로 못 넣는다**, U-6) · kustomize `helmCharts`(`--enable-helm` 을 `argocd-cm` 에 추가해야 함, 차트 리소스는 결국 같은 위치) |
| D3 | ESO 설치 값 | 차트 `external-secrets` **2.8.0**, `helm.releaseName: external-secrets`, `namespaceOverride: external-secrets`, `installCRDs: true`, IRSA ARN 은 `valuesObject` 에 적용 시 치환 | §5 U-5. IRSA 신뢰정책이 `system:serviceaccount:external-secrets:external-secrets` 만 허용한다(`2-cluster/irsa-eso.tf`). release 이름 기본값은 **Application 이름**이라 지정 안 하면 SA 이름이 바뀌어 AssumeRole 이 거부된다. `namespaceOverride` 렌더 실측 🔴(리소스 10개 + SA 3개가 `external-secrets` 로) |
| D4 | 🔑 `ServerSideApply=true` (Application 전체) | 켠다 | 🔴 **ESO CRD 가 687KB**(`secretstores` · `clustersecretstores`, `helm template` 실측). 기존 client-side apply 는 `last-applied` annotation 한도 256KB 에 걸린다. 부수 효과: ESO 가 채우는 기본값 필드 diff 도 줄어든다(§5 U-11, 🟡) |
| D5 | 🔑 wave 배치 | 아래 §3 — ESO 차트는 번호를 못 붙이므로 **기본값 0 에 두고 우리 것을 뒤로** 민다 | 차트 리소스에 annotation 을 다는 값이 차트에 없다. 0 보다 앞이 필요한 것은 네임스페이스 하나뿐 |
| D6 | 🔑 비밀번호 동기화 | postgres 컨테이너 **postStart 훅**이 소켓(trust)으로 `ALTER USER` (2026-10-15 사용자 결정, 추천안 A) | §5 U-1 · U-2. 별도 Job 은 TCP 라 **현재 비밀번호를 알아야** 하므로 어긋났을 때 자신도 실패한다. 실측상 지금은 할 일이 없지만(동기화 전 이미 `1`), SOP 의 *"항상 한다"* 성질을 자동으로 유지한다. 비밀번호는 `\getenv` 로 **인자에 싣지 않는다**(ps 에 안 보인다, 따옴표 깨짐 없음). 기각: 단계 삭제(SOP 결정 반전) · exec Job(RBAC + 이미지 증가, sync 마다 재실행) |
| D7 | 🔑 DB 보호 | postgres 5종(StorageClass · PV · PVC · Service · StatefulSet)에 `argocd.argoproj.io/sync-options: Prune=false,Delete=false` | §5 U-8. PV 는 Retain 이라 **데이터는** 안 사라지지만, PVC 가 prune 되면 PV 가 `Released` 로 claimRef 를 들고 남아 **다음 PVC 가 영원히 Pending**(실패 6종 ④). `Delete=false` 는 Application 을 지울 때도 남긴다 |
| D8 | 자리표시자 | 매니페스트의 자리표시자는 **그대로 두고** Application 의 inline patch 로 적용 시 덮는다 | §5 U-15. 기존 손 경로(sed)와 verify 마커가 그대로 산다. Application 에 붙는 치환은 6개: 기존 2(REGISTRY · REVISION) + ESO 역할 ARN · 앱 시크릿 이름 · EBS 볼륨 ID · AZ |
| D9 | 묶음 파일 | `k8s/eso/kustomization.yaml` 신설(RDS 용 `externalsecret-db.yaml` **제외**) · `k8s/base/kustomization.yaml` 에 `postgres-static.yaml` 추가 · gitops 에 `external-secrets` Namespace | 🔴 kustomize 는 상위 폴더의 **파일**을 못 읽는다(로컬 실측: `security; file … is not in or below`). 디렉토리 단위로만 참조된다. 손 경로는 파일을 하나씩 지정하므로 영향 없음. `postgres.yaml`(3a)은 계속 제외 — 배타 쌍 |
| D10 | ExternalSecret 류 | `SkipDryRunOnMissingResource=true` | §5 U-4. 첫 sync 시점엔 CRD 가 아직 없어 dry-run 이 실패한다 |
| D11 | 재시도 | `syncPolicy.retry` (5회, 10s 부터 2배) | §5 U-4. 자동 sync 는 같은 커밋에서 실패한 sync 를 다시 하지 않는다. ESO 웹훅 인증서가 막 주입되는 순간에 걸리면 멈춘다(🟡) |
| D12 | 노드 · 기타 | 관리형 **3대**. Karpenter · Ingress 안 함. **Fluent Bit 는 손 설치**(5d 그대로, `logging` 네임스페이스) | §4. 5d 를 붙이면 이 세션의 ArgoCD 로그·이벤트가 남아 **퀴즈를 실제 로그로** 풀 수 있다(5c 의 반성). 수집기는 클러스터 범위 권한이라 Application 에 넣지 않는다(5d D12) |
| D13 | 세션 종료의 승인 대기 | 5c D15 그대로 — **사용자가 지켜보는 시간에만** | |

## 3. wave 배치

```
-1  Namespace external-secrets                         (git)
 0  ESO 차트 전부 — CRD 25 · Deployment 3 · 웹훅 2 …   (helm, 기본값)
 1  SecretStore · ExternalSecret 3종(db-incluster · app · postgres-tls)
 2  postgres 5종 — StorageClass · PV · PVC · Service · StatefulSet  ← 반드시 같은 번호
 3  core-api                                           ← Flyway 소유. 빈 DB 면 먼저 떠야 한다
 4  daily-api · ai-api · NetworkPolicy
 5  HPA
```

- **postgres 5종을 같은 번호에** — `gp3-static` 은 `WaitForFirstConsumer` 라 **파드가 스케줄돼야 PVC 가 묶인다.**
  PVC 를 앞 번호에 두면 Pending(=Progressing)에서 다음 번호가 영원히 안 온다(§5 U-9, 🟡).
- **HPA 맨 끝** — 5c 에서 지표를 못 읽는 ~30초 동안 `Degraded` 로 보였다. 앞에 두면 그 동안 진행이 멈춘다(§5 U-10).
- **core-api 와 daily-api 를 가르는 이유** — Flyway 는 core-api 만 돌린다. daily-api 는 `ddl-auto: validate` 라
  스키마가 없으면 기동 실패(`k8s/base/daily-api.yaml`). ai-api 는 DB 를 안 쓰지만 묶음을 줄이려 4 에 둔다.
- 번호는 base 매니페스트가 아니라 **`k8s/gitops/apps/kustomization.yaml` 의 패치**로 붙인다 — 손 경로 파일은 그대로.

## 4. 용량 산술

5c 세션(ArgoCD 4 + ESO 3 + 앱 3 + postgres, 관리형 3대)이 그대로 들어갔다. 이번에 더해지는 것은 Fluent Bit 뿐이다.

```
5d §3 남는 양                    ≈ 1657Mi  (Fluent Bit 포함 계산, ArgoCD 미포함)
ArgoCD requests (values.yaml)     −  512Mi
                                  ≈ 1145Mi 남음
파드 자리: 노드당 DaemonSet 4 → 7칸 × 3 = 21칸
  coredns 2 · ebs-csi-controller 2 · ESO 3 · ArgoCD 4(+applicationSet 0) · postgres 1 · 앱 3 · events 1 = 16
```

HPA 가 daily-api 를 2개로 올리면 17. 들어간다.

## 5. 계획과 실제 코드 사이의 불일치 (착수 전 조사)

Blindspot 23건(2026-10-15). 처리 방식만 적는다.

| ID | 발견 | 처리 |
|---|---|---|
| U-1 | 🔴 `ALTER USER` 가 통하는 이유는 컨테이너 **안**의 trust(소켓·127.0.0.1)다. 별도 파드는 scram 을 탄다 | D6 (postStart) |
| U-2 | 🔴 동기화는 지금 아무 일도 안 한다 — L-14 이후 비밀번호가 0-bootstrap 소유, 일지 *"동기화 전에 이미 `1`"*, 옛 볼륨도 09-13 재생성. SOP §6b 는 *"관측을 근거로 생략하지 마라"* | D6 — 생략하지 않고 자동화. SOP 결정은 유지 |
| U-3 | 🟡 wave 는 Application 하나 안에서만 순서를 정한다 | D2 |
| U-4 | 🟡 ExternalSecret 은 CRD 전 dry-run 실패 · ESO 웹훅이 준비 전이면 `failed calling webhook` · `retry` 없음 | D10 · D11 |
| U-5 | 🔴 IRSA 신뢰정책이 SA 이름 `external-secrets` 에 묶여 있다 · release 이름 기본값 = Application 이름 | D3 |
| U-6 | 🔴 자리표시자 4개 + 기존 2개, ESO ARN 은 kustomize 로 못 넣는다(helm 값) | D2 · D8 |
| U-7 | 🔴 `k8s/eso/` 에 kustomization 없음 · 배타 쌍 2개 · `postgres-tls` 는 in-cluster 모드 전용 | D9. ArgoCD 경로는 **3b(in-cluster · static PV) 전용**이다. 2(RDS) · 3a 는 손 경로로만 재현 |
| U-8 | 🔴 `application.yaml` 주석 *"DB 는 이 Application 에 넣지 않았다"* 와 prune: true | D7. 주석 갱신 |
| U-9 | 🟡 PVC 를 StatefulSet 앞 wave 에 두면 교착 | §3 |
| U-10 | 🟡 HPA 초기 `Degraded` | §3 |
| U-11 | 🟡 ExternalSecret 서버 기본값 필드 · ESO 웹훅 `caBundle` 주입으로 계속 OutOfSync 가능 | D4 로 1차 대응. 남으면 P2 에서 기록하고 `ignoreDifferences` 판단 — **5c D10(ignoreDifferences 기각)과 긴장**, 그 자리에서 사용자에게 알린다 |
| U-12 | 🟡 Sync hook Job 은 sync 마다 돈다 | D6 에서 Job 을 안 쓰므로 해당 없음 |
| U-13 | 🔴 teardown 깨짐 — `kubectl delete externalsecret --all -A` 를 selfHeal 이 1~2초 만에 되살린다. finalizer 없음 | §8 · SOP §8 개정: **Application 먼저 삭제**(리소스는 남고 관리만 끊긴다) → 기존 절차 |
| U-14 | 🔴 고칠 문서 — k8s/README §2·§2.5·§3·§5, SOP §6b·§8, 튜토리얼 3b-6 · 5c 절(2925-2931 · 2987 · 3138 · 3152 · 3158) · 5d-2(3241), 5c 계획서, `infra/aws-eks/README.md:164`, CONTEXT | 세션 성공 뒤 튜토리얼에 5e 절 신설 + 과거 절은 "5e 이후 이렇게 바뀜" 포인터 |
| U-15 | 🔴 verify 마커가 자리표시자·`selfHeal: true`·`path: /spec/replicas` 등을 단언한다 | D8 — 매니페스트 문자열을 바꾸지 않는다. 커밋 전 `check-design-integrity.sh` |
| U-16 | 🔴 CI 는 `k8s/` 를 렌더하지 않는다 | 세션 전 로컬 렌더(`kubectl kustomize` + `helm template`) 필수 — §7 |
| U-17 | 🔴 tofu output 4개가 Application 적용의 입력 | §7 의 적용 명령에 묶는다 |
| U-18 | 🔴 노드 1대로는 파드 자리 부족 | D12 · §4 — 3대 |
| U-19 | 🔴 metrics-server 는 EKS 애드온이다(5c 계획서 §6-2 의 "손 설치"는 틀림) | 범위 밖. 5c 계획서 정정 |
| U-20 | 🔴 Flyway 는 core-api 만 · daily-api 는 validate · 세 Deployment 모두 Recreate · core-api startupProbe 상한 5분 | §3. wave 3 이 최대 5분 걸릴 수 있다 |
| U-21 | 🔴 ai-api NetworkPolicy 는 ingress·`app: ai-api` 한정 — 영향 없음. ArgoCD 기본 NetworkPolicy 6개가 강제된다 | 🟡 repo-server 가 **helm 저장소**(charts.external-secrets.io)에 나갈 수 있는지는 미확인 → P1 에서 확인 |
| U-22 | 🟡 ExternalSecret · SecretStore · Job health 는 ArgoCD 3.5.4 에 내장 | P2 에서 확인 |
| U-23 | ⚪ 3a·Stage 2 경로는 ArgoCD 로 재현 불가 | U-7 과 같은 처리 |

## 6. 통과 기준

| # | 무엇을 | 어떻게 보나 | 통과 | 실패 |
|---|---|---|---|---|
| P1 | 🔑 Application 하나로 끝까지 선다 | ArgoCD helm 설치 → Application apply(치환 6개) 외 **손 명령 0** | 15분 안에 `Synced` + `Healthy`, ExternalSecret 3개 `SecretSynced`, core-api `/health` 200 | 어느 wave 에서 멈춤 / 손 단계 필요 |
| P2 | 🔑 **wave 순서대로 섰다** | 리소스별 `creationTimestamp` · Application `status.operationState.syncResult.resources` · ArgoCD controller 로그(CloudWatch) | -1 → 0 → … → 5 순서. 각 wave 가 앞 wave 의 **Healthy 뒤에** 시작. OutOfSync 반복 없음(U-11) | 순서 역전 / 같은 리소스가 계속 다시 sync |
| P3 | 🔑 **postStart 동기화가 실제로 고친다** | ① 일부러 어긋나게: 컨테이너 안에서 `ALTER USER … PASSWORD '<엉뚱한 값>'` ② 파드 IP 로 scram 검사 → `FATAL` 확인 ③ `kubectl delete pod postgres-0` ④ 새 파드에서 다시 scram 검사 | ④ 에서 `1`. core-api 가 재연결(readiness 복귀) | ④ 에서도 FATAL / postStart 실패로 컨테이너 재시작 반복 |
| P4 | 🔑 **prune 은 지우고, DB 는 안 지운다** (5c E1 이월) | 작업 브랜치에 커밋: base 목록에서 `networkpolicy-ai-api.yaml` 과 `postgres-static.yaml` 을 **둘 다** 뺀다 → 폴링(≤3분) | NetworkPolicy **삭제됨** · postgres 5종 **그대로**(Application 은 `OutOfSync` + "requires pruning" 표시) · core-api 정상. 이후 되돌림 커밋으로 복귀 | postgres 리소스 하나라도 삭제 |
| P5 | 5c 미해결 — `Synced` + `Degraded` 의 정체 | HPA wave 시점에 `status.resources[].health` | 어느 리소스가 Degraded 였는지 기록 | — |
| P6 | 뒷정리 · 고아 | 개정 SOP §8(Application 먼저 삭제) → destroy | `tofu state list` 0 · 고아 0 · EBS 영속 볼륨 `available` · 로그 그룹 2개(5d) 그대로 | 볼륨이 지워짐 / 예상 밖 리소스 |

## 7. 세션 전 준비 (과금 전, $0)

1. 매니페스트 작성 — §8 파일 목록.
2. 🔴 **로컬 렌더 검증**(CI 가 안 한다, U-16):
   - `kubectl kustomize k8s/gitops/apps` — wave annotation · Prune=false 가 붙었는지, `postgres.yaml`(3a)·`externalsecret-db.yaml`(RDS)이 **없는지**
   - `helm template external-secrets … --set namespaceOverride=external-secrets` — SA 이름·네임스페이스
   - Application 의 inline patch 경로가 실제 필드와 맞는지 — 치환 후 `kubectl apply --dry-run=client` 는 CRD 가 없어 못 하므로, patch 대상 경로를 렌더 결과에서 grep 으로 대조
   - postStart 스크립트를 로컬 `postgres:17-alpine` 컨테이너에서 실행 — 빈 볼륨(첫 initdb)과 기존 볼륨 둘 다(🟡 첫 initdb 중 임시 서버에 붙는 경우가 핵심)
3. `check-design-integrity.sh` 통과.
4. 세션 승인 요청 — 예상 **70~90분 ≈ $0.20~0.25**(5c 실측 70분 $0.18 기준).

## 8. 바꿀 파일

| 파일 | 변경 |
|---|---|
| `k8s/gitops/application.yaml` | multi-source(ESO helm + git) · 치환 4개 추가 · inline patch · `ServerSideApply` · `retry` · 주석(U-8) |
| `k8s/gitops/apps/kustomization.yaml` | `../../eso` 추가 · Namespace · wave annotation 패치 · postgres `Prune=false,Delete=false` |
| `k8s/gitops/apps/namespace-external-secrets.yaml` | 신규 |
| `k8s/eso/kustomization.yaml` | 신규 — RDS 용 제외 3+1 개, `SkipDryRunOnMissingResource` |
| `k8s/base/kustomization.yaml` | `postgres-static.yaml` 추가 · 주석 갱신 |
| `k8s/base/postgres-static.yaml` | postStart 훅 |
| `k8s/README.md` · `docs/eks-session-sop.md` §6b·§8 | ArgoCD 경로 추가 · teardown 개정(U-13) |
| 세션 후 | 튜토리얼 5e 절 · 일지 · 퀴즈 · 5c 계획서 정정(U-19) · `infra/aws-eks/README.md` |

## 9. 이번에 하지 않는 것

| 항목 | 왜 지금 못 하나 |
|---|---|
| **빈 DB 에서의 기동 순서 실측** (5c 이월) | 영속 볼륨에 스키마가 있다. 비우면 6개월치 학습 데이터를 잃는다. wave 3 → 4 순서(P2)로 **보장 장치는** 확인하지만, "core-api 가 먼저 안 뜨면 daily-api 가 죽는다"는 실패 자체는 재현하지 않는다 |
| ArgoCD 자체를 ArgoCD 로 관리 | 닭과 달걀 — 최초 설치는 어차피 손. 학습 이득 대비 위험(자기 자신을 prune) |
| Fluent Bit 를 Application 에 넣기 | 5d D12 그대로. 클러스터 범위 권한 |
| Karpenter 를 Application 에 넣기 | 5b 의 NodePool 은 IRSA·인스턴스 프로파일 등 tofu 출력이 더 많다. 별도 Stage 감 |
| 비밀번호 **회전** 실습 | 0-bootstrap 비밀번호를 바꾸면 prod 와 무관하지만 영속 볼륨 쪽 실험이 필요하다. P3 이 "어긋남 → 자가 복구"를 이미 덮는다 |

## 10. 결과

(세션 후 기록)

### 1차 세션 (2026-10-15, 중단 · ≈ $0.15)

ArgoCD 가 클러스터 openapi 를 못 읽어 **sync 0건** — P1~P5 미실행. 상세는 일지 같은 날짜.

| 확인한 것 | 확도 |
|---|---|
| 클러스터가 `/openapi/v2` 를 **protobuf 로 요청하면 503**, JSON 이면 200 | 🔴 |
| ArgoCD 3.5.4 는 protobuf 로 받고 실패 시 클러스터 캐시 전체 실패, 끄는 스위치 없음 | 🔴 (소스) |
| 5c(10-13) 와 같은 K8s 1.36 · 같은 ArgoCD 였는데 그땐 됐다 → EKS 쪽 변화 | 🟡 |
| 범인은 EKS 관리형 APIService `v1.metrics.eks.amazonaws.com` | ⚪ (지워서 확인하려다 분류기 거부) |

5e 매니페스트(wave · postStart · Prune=false)는 **한 번도 클러스터에 닿지 않았다** — 설계의 옳고 그름은 미확인이다.
