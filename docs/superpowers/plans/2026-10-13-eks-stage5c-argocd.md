# EKS Stage 5c — ArgoCD 계획서

> 2026-10-13 작성 · 브랜치 `stage/eks-14-argocd` · 상태: **계획 (세션 전)**
>
> Stage 5 의 마지막이다(5a 지표·HPA ✅ → 5b Karpenter ✅ → **5c ArgoCD**).
> 세션 절차는 `docs/eks-session-sop.md` 를 따른다. 이 문서는 **그 위에 얹는 5c 고유 부분**만 적는다.
> 🟡 표시는 문서·로컬 렌더로만 확인했고 실클러스터에서 보지 않은 것이다.

## 1. 무엇을 확인하려는가

한 문장: **클러스터가 git 을 보고 스스로 배포하며, 누가 손으로 바꿔도 git 상태로 되돌아온다는 것을
실클러스터에서 시각과 함께 확인한다.**

지금까지는 세션마다 사람이 `kubectl apply` 를 순서대로 쳤다(`k8s/README.md` §2~3). 클러스터의 상태는
"마지막에 친 명령"이 정했고, 그 명령은 터미널 기록에만 남았다.

| 용어 | 하는 일 | 지금 레포의 대응물 |
|---|---|---|
| **GitOps** | 클러스터가 가져야 할 상태를 git 에 적고, 클러스터 안의 도구가 그것을 **당겨** 맞춘다 | 없음. 사람이 **밀어** 넣는다 |
| **ArgoCD** | 그 도구. git 저장소를 주기적으로 읽어 클러스터와 비교하고 다르면 맞춘다 | 없음 |
| **Application** | "이 git 경로를 이 클러스터의 이 네임스페이스에 맞춰라" 한 건의 선언 | `k8s/README.md` 의 명령 묶음 |
| **Sync** | git 의 상태를 클러스터에 적용하는 동작 | `kubectl apply` |
| **OutOfSync** | git 과 클러스터가 다르다는 판정 | 없음. 지금은 아무도 비교하지 않는다 |
| **self-heal** | 클러스터가 손으로 바뀌면 git 상태로 되돌리는 설정 | 없음 |
| **prune** | git 에서 지운 것을 클러스터에서도 지우는 설정 | 없음 |
| **Kustomize** | 매니페스트 여러 개를 묶고 일부 값을 덮어쓰는 도구. `kubectl` 에 들어 있다 | `sed` 치환 |

5a·5b 는 "얼마나 띄울 것인가"였다. 5c 는 **"무엇을 띄울지를 어디서 정하는가"** 다.

## 2. 결정 사항

| # | 결정 | 선택 | 근거 · 기각한 대안 |
|:-:|---|---|---|
| D1 | ArgoCD 버전 | **v3.5.4** (helm 차트 `argo/argo-cd` **10.10.2**) | 호환표상 K8s 1.36 을 지원하는 것은 **3.5 계열뿐**이다(2026-10-13 조회: 3.5 → 1.33~1.36, 3.4·3.3 → ~1.35). 3.5.5 는 어제(10-12) 나왔다 — 기각(5b D1 과 같은 이유). 3.5.4(10-06)에는 Kustomize 관련 보안 수정이 들어 있다 |
| D2 | 설치 방법 | **helm**, 네임스페이스 `argocd` | 레포 관례(ESO·Karpenter 가 helm). 기각: 공식 `install.yaml` (requests 를 주거나 부품을 끄려면 패치를 따로 얹어야 한다) · core 설치 (API 서버가 없어 화면으로 상태를 볼 수 없다) |
| D3 | 띄울 부품 | controller · repo-server · server · redis **4개**. `dex` · `notifications` · `applicationSet` 은 끈다 | 차트 기본은 7개다(§5 U-5). 끄는 셋은 로그인 연동 · 알림 · Application 대량 생성용이고 이번에 쓰지 않는다 |
| D4 | 부품 크기 | requests: controller `100m/256Mi` · repo-server `50m/128Mi` · server `50m/96Mi` · redis `10m/32Mi` = **512Mi** 🟡 | 차트 기본은 requests **없음**이다. 안 주면 스케줄러가 0 으로 계산해 §3 산술이 틀어진다(5b U-3 과 같은 함정). 값은 추정이라 세션에서 실측한다 |
| D5 | 🔑 ArgoCD 가 맡는 범위 | **앱 3종**(Deployment · Service) + `networkpolicy-ai-api` + `daily-api` HPA | §5 U-7. ESO · 시크릿 · postgres 는 지금처럼 손으로 올린다. 중간에 사람이 치는 단계(DB 비밀번호 동기화)가 끼어 있어 ArgoCD 의 순서 제어만으로는 못 넘는다 |
| D6 | git 의 어디를 읽게 하나 | 신규 `k8s/gitops/apps/` (Kustomize). `k8s/base/` · `k8s/hpa/` 에 `kustomization.yaml` 을 하나씩 두고 이를 묶는다 | §5 U-1·U-3. `k8s/base/` 를 통째로 읽히면 안 된다. 기존 파일 내용과 `sed` 절차는 건드리지 않는다 |
| D7 | 🔑 이미지 주소 | **태그는 git 에, 레지스트리 주소는 클러스터의 Application 에** 🟡 | §5 U-2. ECR 주소에 계정 ID 가 들어 있어 공개 레포에 적을 수 없다. git 에는 `devquest/core-api:<태그>` 까지만 적고, 앞의 레지스트리는 Application 의 Kustomize 덮어쓰기로 붙인다. 로컬 렌더로 먼저 확인하고, 안 되면 주소 전체를 Application 에 둔다(그러면 "커밋으로 이미지 배포"는 포기) |
| D8 | 어느 브랜치를 보게 하나 | 세션 중에는 **이 작업 브랜치**. 파일의 기본값은 `main` | §5 U-6. 세션에서 커밋을 3번 넣어야 하는데 main 에는 직접 push 할 수 없다 |
| D9 | 동기화 정책 | 자동 + `selfHeal` + `prune` | 손으로 바꾼 것이 되돌아오는 것(P3)과 HPA 와의 충돌(P5)을 보려면 켜야 한다 |
| D10 | HPA 충돌을 푸는 방법 | `daily-api` 의 `spec.replicas` 를 **git 에서 뺀다**(Kustomize 패치) | Deployment 3개 모두 `replicas: 1` 이 적혀 있다(§5 U-4). 기각: Application 의 `ignoreDifferences` (해결책이 git 이 아니라 클러스터 쪽에 남는다) |
| D11 | Application 등록 방법 | `k8s/gitops/application.yaml` 을 `sed` 치환 후 `kubectl apply` 한 번 | 레포 관례. 이 한 번이 사람이 미는 마지막 단계다. 기각: app-of-apps (Application 이 하나뿐이라 얻는 것이 없다) |
| D12 | git 변경 감지 | 기본 주기(약 3분) 그대로 | 웹훅은 공개 주소가 필요하다(ALB 없음). 주기를 기다리는 시간 자체가 측정 대상이다(P4) |
| D13 | 화면 접근 | `kubectl port-forward`. **ALB · LoadBalancer 없음** | 5a D8 과 같다. 확인은 전부 `kubectl get application` 으로 한다 |
| D14 | 노드 | 관리형 **3대**, Karpenter 설치 안 함, metrics-server 는 설치 | §3. 부하를 걸지 않으므로 k6 전용 노드가 필요 없다. HPA 가 있어야 P5 를 본다 |
| D15 | 세션 종료의 승인 대기 | **규칙 변경 없음. 사용자가 지켜보는 시간에만 진행한다** (2026-10-13 사용자 결정) | 5b 에서 유휴 $0.405. 남은 선택지 셋 중 정책 · 설정 변경 둘을 기각. 내가 지킬 것: apply 전에 destroy 예상 시각을 확답받고, **측정이 일찍 끝나면 그 자리에서 알린다**(5b 는 31분 일찍 끝났는데 알림 한 번으로 그쳤다) |

## 3. 용량 산술

실측값(일지 08-31·09-08·10-08·10-12): 노드당 allocatable 1365Mi, 노드당 DaemonSet 104Mi.

```
기본 앱 requests   core 480 + daily 512 + ai 320 + postgres 256        = 1568Mi
시스템 Deployment  ebs-csi-controller 232 + coredns 70                 =  302Mi
metrics-server     200Mi
ArgoCD             512Mi 🟡 (D4)
P5 에서 늘어나는 daily-api 1개                                          =  512Mi
                                                                 합계  = 3094Mi

3대 × (1365−104) = 3783Mi  →  남는 양 689Mi
```

총량은 들어간다. 다만 5a 에서 본 것처럼 **총량이 맞아도 배치가 안 맞을 수 있다**(조각화). 파드 하나가
`Pending` 이면 §6 의 분기를 따른다.

## 4. 통과 기준

각 항목은 **틀렸을 때 다른 값이 나오는** 검사로 잡는다.

| # | 주장 | 검사 | 통과 | 실패하면 보이는 것 |
|:-:|---|---|---|---|
| P1 | ArgoCD 가 떴다 | `kubectl -n argocd get pods` · `kubectl top pods -n argocd` | 4개 `Running`. **부품별 메모리 실측 기록** | Pending / CrashLoop |
| P2 | 🔑 **앱이 `kubectl apply` 없이 떴다** | Application 등록 뒤 `kubectl get application -n argocd` · `kubectl get deploy` | `Synced` · `Healthy`, 앱 3종 `1/1`. 리소스에 ArgoCD 추적 표시가 있다 | `OutOfSync` / `Degraded` / 이미지 주소 오류 |
| P3 | 손으로 바꾸면 되돌아온다 | ① `kubectl scale deploy core-api --replicas=2` ② `kubectl delete svc ai-api` | ① 다시 1 ② 다시 생김. **되돌아오기까지의 시각 기록** | 바뀐 채로 남음 |
| P4 | 🔑 **커밋 한 번이 배포다** | 커밋 push → `kubectl get application -w` | 클러스터가 바뀐다. **시각 3개**: push · 감지 · 적용 완료 | 3분 넘게 변화 없음 |
| P5 | 🔴 **HPA 와 부딪힌다** (예상) | git 의 HPA `minReplicas` 를 2 로 올리는 커밋 → `daily-api` 의 복제본 수 관찰 | 부딪히는 모습을 **그대로 기록**한다: 1 ↔ 2 를 오가는가, 한쪽이 이기는가, Application 판정은 무엇인가 | (예상과 다르면 그것이 결과다) |
| P6 | git 에서 `replicas` 를 빼면 멎는다 | D10 의 패치 커밋 → 5분 관찰 | 복제본 2 로 안정, Application `Synced` | 계속 오간다 |
| P7 | 이미지 태그 커밋이 실제 교체를 일으킨다 | `daily-api` 태그를 다른 것으로 바꾸는 커밋 | 새 ReplicaSet · 새 이미지로 `Ready` | 이미지 주소 오류 (D7 이 틀렸다는 뜻) |
| P8 | 뒷정리 | SOP 종료 절차 (8b 완료 게이트 포함) | `tofu state list` 0건, 로드밸런서 0, 고아 0 | — |

덤으로 볼 것(통과 기준 아님):

- **E1 — prune**: git 에서 HPA 를 뺀 커밋이 클러스터의 HPA 를 지우는가.
- **E2 — 되돌리기**: `git revert` 한 번으로 이전 이미지로 돌아가는가.
- **E3 — ArgoCD 가 죽어 있을 때**: controller 를 0 으로 줄이면 앱은 그대로 도는가(배포 도구와 실행 중인 앱은 별개).

## 5. 계획과 실제 코드 사이의 불일치 (착수 전 조사)

| # | 계획이 가정한 것 | 실제 | 이번에 |
|:-:|---|---|---|
| U-1 | `k8s/base/` 를 ArgoCD 에 가리키면 된다 | 🔴 **안 된다.** 이 디렉토리에는 둘 중 하나만 적용해야 하는 쌍(`postgres.yaml` ↔ `postgres-static.yaml`)과, 적용하면 **ALB 를 만드는** `ingress.yaml` 이 같이 있다. 통째로 읽히면 StatefulSet 두 개가 같은 이름을 두고 다투고, tofu 장부 밖에 로드밸런서가 생긴다 | D6 — 읽힐 파일을 `kustomization.yaml` 에 명시한다 |
| U-2 | 매니페스트에 이미지 주소가 있다 | 셋 다 `image: IMAGE_PLACEHOLDER` 이고 **이름이 같다.** Kustomize 는 이름으로 이미지를 찾으므로 셋을 구분할 수 없다. 그리고 실제 주소에는 계정 ID 가 들어 있어 공개 레포에 적을 수 없다 | D7 — Deployment 별 패치로 `devquest/<앱>:<태그>` 를 넣고, 레지스트리는 Application 에서 붙인다 🟡 |
| U-3 | 다른 디렉토리의 파일을 골라 묶을 수 있다 | Kustomize 는 자기 디렉토리 **밖의 파일**을 직접 못 읽는다(디렉토리 단위로만 참조). | D6 — `k8s/base/` 와 `k8s/hpa/` 안에 각각 `kustomization.yaml` 을 둔다. 기존 `kubectl apply -f <파일>` 절차에는 영향이 없다 |
| U-4 | HPA 는 ArgoCD 와 무관하다 | Deployment 3개 모두 `replicas: 1` 이 적혀 있다. HPA 가 2 로 올리면 ArgoCD 는 "git 과 다르다"고 본다 | P5 에서 그대로 관찰한 뒤 D10 으로 푼다 |
| U-5 | 차트 기본값으로 뜬다 | 워크로드 **7개**, requests **없음**(10.10.2 를 로컬에서 렌더해 확인). NetworkPolicy 6개도 기본으로 만든다 🟡 | D3·D4. 이 클러스터는 NetworkPolicy 가 실제로 강제된다 — 포트 포워딩이 막히면 §6 분기 |
| U-6 | ArgoCD 는 main 을 본다 | 세션에서 커밋 3~4개를 넣어야 하는데 main 은 직접 push 금지다 | D8. 🔴 그 커밋들은 PR 에 그대로 남는다 — **마지막 커밋의 상태가 레포에 남길 상태여야 한다**(§6) |
| U-7 | 전부 ArgoCD 에 맡긴다 | 올리는 순서 중간에 사람이 치는 단계가 있다: ESO helm 설치(CRD) → 시크릿 → postgres → **DB 비밀번호 동기화(`ALTER USER`)** → 앱. postgres 매니페스트에는 볼륨 ID · AZ 치환도 있다 | D5 — 앱 3종만. 앞 단계는 그대로 손으로 한다 |
| U-8 | 커밋하면 바로 반영된다 | ArgoCD 는 git 을 기본 약 3분마다 본다 🟡 | D12. P4 의 시각 3개로 실제 지연을 잰다 |
| U-9 | ArgoCD 가 만든 것은 destroy 때 문제가 없다 | 범위(D5) 안에는 AWS 리소스를 만드는 것이 없다. `ingress.yaml` 을 뺐고 Service 는 전부 ClusterIP 다 | P8 에서 로드밸런서 0 을 조회로 확인 |
| U-10 | 화면 로그인 | 초기 관리자 비밀번호가 클러스터의 Secret 에 생긴다 | 🔴 **출력하지 않는다.** 화면이 필요하면 사용자가 직접 조회한다. 일지 · PR 에 쓰지 않는다 |
| U-11 | 앱 기동은 빠르다 | Spring 기동이 한 번에 약 24초~3분이고, 커밋마다 롤링 교체가 돈다 | 커밋 3번 = 대기 3번. §7 시간표에 반영 |
| U-12 | `daily-api` 의 다른 이미지가 있다 | ECR 에 `daily-api` 이미지가 2개 있다고 적혀 있다(09-28) 🟡 | $0 준비에서 태그 2개를 조회로 확정한다. 1개뿐이면 P7 을 `ai-api` 로 바꾸거나 뺀다 |

### 이번에 하지 않는 것 (다음 작업으로 남긴다)

| 항목 | 왜 지금 아닌가 |
|---|---|
| ESO · postgres 까지 ArgoCD 에 맡기기 (순서 제어 = sync wave) | U-7. 사람이 치는 단계를 먼저 없애야 한다(비밀번호 동기화를 Job 으로 바꾸는 일). 별개 작업이다 |
| app-of-apps · ApplicationSet | Application 이 하나뿐이다 |
| ArgoCD 가 ArgoCD 자신을 관리 | 처음 한 번은 어차피 손으로 설치해야 하고, 세션이 1시간이다 |
| 이미지 태그 자동 갱신(Image Updater) · CI 가 태그 커밋을 넣는 흐름 | `ecr-push.yml` 을 고쳐야 한다. 지금 CI 는 이미지만 올린다 |
| 웹훅으로 즉시 반영 | 공개 주소(ALB)가 필요하다 |
| 로그인 연동(SSO) · 권한 · 프로젝트 분리 · 알림 | 혼자 쓰는 1시간짜리 클러스터다 |
| 비공개 레포 인증 | 이 레포는 공개라 인증 없이 읽힌다. 실무와 다른 점으로 튜토리얼에 적는다 |
| 점진 배포(Argo Rollouts) | 별개 도구다 |
| Karpenter 와 함께 돌리기 · 5b 에서 미룬 정리 스크립트 EC2 경로 확인 | 이번 세션에 Karpenter 를 올리지 않는다(D14). 주제를 섞으면 실패 원인을 가르기 어렵다. 5b 계획서에 그대로 남아 있다 |
| `core-api` · `ai-api` 의 `replicas` 정리 | HPA 가 붙은 것은 `daily-api` 뿐이다 |
| (세션 뒤 추가) `prune` 을 실물로 확인 (E1) | 측정을 32분에 끝내고 바로 내렸다. 커밋 하나 + 폴링 대기 약 3분이면 된다. 시크릿을 넣는 다음 단계에서 같이 본다 |
| (세션 뒤 추가) 빈 DB 에서의 기동 순서 — `core-api` 가 먼저 떠야 한다 | 영속 볼륨에 스키마가 이미 있어 드러나지 않았다. ArgoCD 에 순서를 알리는 것은 sync wave 로, 시크릿 단계와 같은 도구다 |
| (세션 뒤 추가) Application 이 `Synced` + `Degraded` 로 30초쯤 보인 이유 | 미확인(추정: HPA 가 지표를 못 읽는 동안). 다음 세션에서 `kubectl get application -o yaml` 의 `status.resources[].health` 를 그 시점에 찍는다 |
| (세션 뒤 추가) apply 뒤 세션 마커가 한 번 안 생긴 이유 | 미확인. 하네스 동결 규칙상 훅은 고치지 않는다. **apply 뒤 마커 육안 확인(SOP §5)이 실제로 걸러냈다** |
| (퀴즈 중 추가, 사용자 지적) **로그 수집 단계가 트랙에 없다** — 파드 로그 · K8s 이벤트 · 감사 로그 | 클러스터를 내리자 ArgoCD 컨트롤러 로그와 이벤트가 전부 사라져, 퀴즈를 실제 로그로 풀 수 없었다(남은 것은 CloudTrail 의 AWS API 호출과 일지뿐). Stage 0~5c 어디에도 수집이 없다. 별도 Stage 감이다 — 저장소(CloudWatch Logs 면 영속 리소스 원장 · 고아 검사 수정, Loki 면 학습 클러스터에 Grafana 자격증명을 넣지 않는 규칙과 충돌) · 수집기 DaemonSet 의 파드 슬롯(t4g.small 11개 상한)을 먼저 정해야 한다. **5c 머지 뒤 다음 Stage 를 고를 때 시크릿 단계와 함께 후보로 올린다.** 그 전까지의 최소 조치: 세션 중 `kubectl logs` · `kubectl get events` 를 파일로 저장($0) |

## 6. 세션 절차 (5c 고유 부분)

SOP 의 시작·종료 체크리스트는 그대로 따른다. 🔴 **과금 구간에서는 질문하지 않는다.**

apply 전에 사용자에게 알리고 확답받을 것(D15): 승인 창 횟수(apply 2 · destroy 2), destroy 승인이 필요한
**예상 시각**, 늦어질 때의 비용(§7).

```
1. apply (-var node_desired_size=3)                      ← 과금 시작
2. 손으로 올린다: ESO → 시크릿 → postgres → 비밀번호 동기화 → metrics-server
   (앱 3종은 올리지 않는다 — ArgoCD 가 올리는 것을 보려는 것이다)
3. ArgoCD 설치 (helm, k8s/argocd/values.yaml)             → P1
4. Application 등록 (sed 치환 후 kubectl apply 한 번)       → P2
5. 손으로 바꿔 본다                                       → P3
6. 커밋 ① HPA minReplicas 1 → 2                          → P4(시각) · P5(충돌 관찰)
7. 커밋 ② daily-api 의 replicas 를 git 에서 뺀다           → P6
8. 커밋 ③ daily-api 이미지 태그 변경                       → P7
9. (시간이 남으면) E1 · E2 · E3
10. 커밋 ④ 레포에 남길 상태로 되돌린다 (minReplicas 1, 태그는 처음 것)
11. destroy                                              → P8
```

미리 정해 둔 분기:

| 상황 | 행동 |
|---|---|
| ArgoCD 파드가 `Pending` | requests 를 절반으로 낮춰 재설치. 그래도 안 되면 server 를 끈다(화면 포기) |
| P2 에서 이미지 주소 오류 | D7 의 대안으로 전환: 주소 전체를 Application 에 둔다. P7 은 건너뛴다 |
| 포트 포워딩이 막힌다 | 화면을 포기하고 `kubectl` 로만 진행. 차트의 NetworkPolicy 는 건드리지 않고 기록만 |
| P4 에서 5분 넘게 감지 안 됨 | `kubectl annotate application … argocd.argoproj.io/refresh=normal` 로 강제하고 그 사실을 기록 |
| P5 에서 파드가 계속 죽고 뜬다 | 3분만 관찰하고 커밋 ② 로 넘어간다 |
| 60분 경과 | 남은 항목을 버리고 10 → 11 로 간다 |

## 7. 비용

| 구간 | 시간 | 단가 | 금액 |
|---|---|---|---|
| apply | 9분 | 컨트롤플레인 $0.10/h (노드는 뒤쪽 3분) | $0.02 |
| 작업 (2~10) | 45분 | 3대 $0.1865/h | $0.14 |
| destroy | 9분 | 평균 $0.14/h | $0.02 |
| **예상** | **약 63분** | | **$0.18** |
| **상한** | 90분 | | **$0.30** |

destroy 승인이 늦어지면 분당 약 $0.003(시간당 $0.19)이 더 든다. 5b 는 여기서 $0.405 를 썼다.
누적 지출은 $6.903 / $200 이다(2026-10-13 조회).

## 8. 만들 파일

| 파일 | 내용 |
|---|---|
| `k8s/argocd/values.yaml` | D3·D4 — 끌 부품, requests |
| `k8s/base/kustomization.yaml` | 앱 3종 + `networkpolicy-ai-api` 만 나열 |
| `k8s/hpa/kustomization.yaml` | `daily-api-hpa.yaml` |
| `k8s/gitops/apps/kustomization.yaml` | 위 둘을 묶고, Deployment 별 이미지 패치 |
| `k8s/gitops/application.yaml` | Application 한 건. 레지스트리 · 브랜치는 자리표시자 |
| `docs/eks-tutorial-steps.md` Stage 5c 절 | 세션 뒤 |

AWS 쪽(OpenTofu) 변경은 **없다.** ArgoCD 는 AWS API 를 부르지 않으므로 IAM 역할도 필요 없다.

## 9. $0 준비에서 확인할 것

- `kubectl kustomize k8s/gitops/apps` 가 렌더되는가. 결과에 `postgres` · `Ingress` 가 **없는가**(U-1).
- D7: 레지스트리 덮어쓰기를 흉내 낸 렌더에서 이미지가 `<레지스트리>/devquest/<앱>:<태그>` 로 나오는가.
- `helm template` 에 `k8s/argocd/values.yaml` 을 넣었을 때 워크로드가 4개이고 requests 가 들어갔는가.
- ECR 의 `daily-api` 태그 2개(U-12). 조회는 무료다.
- 기존 절차가 그대로 도는가: `sed … k8s/base/core-api.yaml` 의 출력이 이전과 같은가.

### 결과 (2026-10-13 14:53 KST, 전부 로컬 · $0)

| 확인 | 결과 | 확도 |
|---|---|---|
| `kubectl kustomize k8s/gitops/apps` | 렌더됨. Deployment 3 · Service 3 · HPA 1 · NetworkPolicy 1. `postgres` · `Ingress` · 자리표시자 **0건** | 🔴 |
| D7 레지스트리 덮어쓰기 | 사본에 `images: [{name: devquest/<앱>, newName: <가짜 레지스트리>/devquest/<앱>}]` 를 붙여 렌더 → 셋 다 `<레지스트리>/devquest/<앱>:<태그>`. **패치가 먼저, 이미지 치환이 나중**이라는 순서가 맞았다 | 🟡 아래 |
| `helm template` + `values.yaml` | 파드가 뜨는 것 4개(controller · repo-server · server · redis), 넷 다 requests 들어감. 합 512Mi | 🔴 |
| ECR 태그 | `daily-api` · `ai-api` 각 2개(`bd51e18…` 08-31, `14cb335…` 09-03). `core-api` 최신 `1a8a9aa…` 10-08 | 🔴 |
| 기존 sed 절차 | `core-api.yaml` 을 건드리지 않았으므로 출력 동일(구성상 당연) | 🔴 |

계획과 달랐던 것:

- **applicationSet 은 끌 수 없고 0 으로 줄일 수만 있다.** 이 차트 버전에 `enabled` 스위치가 없다.
  Deployment 는 남고 파드만 0 이다. "워크로드 4개"가 아니라 **"뜨는 파드 4개"** 가 정확하다.
- **설치 때 Job 파드가 하나 잠깐 뜬다**(`argocd-redis-secret-init`, 설치 전 훅). requests 없음. 끝나면 사라진다.
- **repo-server 에 초기화 컨테이너(`copyutil`)가 있고 같은 requests 를 받는다.** 초기화 컨테이너는 본 컨테이너와
  동시에 돌지 않으므로 파드의 요청량은 합이 아니라 큰 쪽(128Mi)이다. 용량 산술(§3)은 그대로다.
- 차트의 NetworkPolicy 는 값 적용 뒤 **4개**(기본 6개에서 끈 부품 몫이 빠짐).

아직 🟡 인 것 — 세션에서만 확인된다:

- D7 은 **Kustomize 의 동작**만 확인했다. ArgoCD 의 `spec.source.kustomize.images` 가 같은 `images:` 항목을
  만들어 낸다는 것은 문서 기준이고 실물 미확인이다. 틀리면 P2 에서 `ImagePullBackOff` 로 드러난다(§6 분기 있음).
- P7 의 태그 교체는 `14cb335…` → `bd51e18…`(더 오래된 이미지)로 한다. 그 이미지가 지금 DB 스키마로 뜨는지 미확인.
  안 뜨면 "잘못된 커밋을 되돌리면 복구된다"(E2)를 대신 확인한 것으로 기록한다.

## 10. 세션 결과 (2026-10-13 16:27 ~ 17:37 KST)

| 기준 | 결과 | 실측 |
|---|---|---|
| P1 ArgoCD 가 뜬다 · 메모리 | ✅ | 설치 54초. 4개 합 215Mi(2분 뒤) → 272Mi(18분 뒤). 요청 512Mi |
| P2 `kubectl apply` 없이 앱이 뜬다 | ✅ | 등록 15초 뒤 `Synced`, 47초 뒤 3개 1/1 |
| P3 손으로 바꾸면 되돌아온다 | ✅ | `scale` 1초 · `delete svc` 2초(UID 바뀜) |
| P4 커밋 → 배포 시각 | ✅ | 1분 8초 · 1분 46초 · 2분 15초 · 2분 51초 |
| P5 HPA 충돌 관측 | ✅ | 3분에 파드 4번 재생성. 재시도 간격 41초 → 2분 41초 |
| P6 `replicas` 를 빼면 멈춘다 | ✅ | 반영 순간 1 로 한 번 떨어진 뒤 `Synced` + 2 유지 |
| P7 태그 커밋으로 이미지 교체 | ✅ | 반영 26초 뒤 새 파드 1/1. 8월 31일자 이미지도 기동 |
| P8 종료 · 고아 0 | ✅ | 42 destroyed. EKS 0 · EC2 0 · 고아 0 · 영속 EBS 1 |
| E1 prune | ⬜ 안 함 | §5 표로 넘김 |
| E2 되돌리기 | 🟡 | 커밋 ④ 가 같은 경로(태그 · minReplicas 복원). 단독으로는 안 봄 |
| E3 컨트롤러 0 | ✅ | 앱은 그대로, 되돌림 없음, Application 은 `Synced` 로 남음 |

🟡 였던 결정의 결과:

- **D4 (requests 512Mi)** → 실측 272Mi. 넉넉하다. 줄일 수 있지만 관리 대상이 늘면 controller 가 커지므로 그대로 둔다.
- **D7 (태그는 git, 레지스트리는 Application)** → 🔴 확인. `kustomize.images` 가 로컬 흉내와 같은 결과를 냈다.
- **D12 (폴링 약 3분)** → 4회 모두 3분 안.
- **차트 NetworkPolicy 와 포트 포워딩** → 확인하지 않았다. 화면 없이 `kubectl` 로만 진행했다.

비용: 과금 70분, 약 **$0.18**(예상 $0.18 · 상한 $0.30). 측정 32분, destroy 승인 대기 27분.
운영에서 틀린 것은 일지 2026-10-13 「Stage 5c 유료 세션」에 있다(apply 병렬 호출, destroy 시각 오안내, 마커 누락).
