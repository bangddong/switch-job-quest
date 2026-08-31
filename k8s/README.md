# K8s 매니페스트 (EKS Stage 1+)

앱을 EKS에 배포하는 선언형 매니페스트. **클라우드 자원은 OpenTofu, K8s 워크로드는 여기** — 두 평면을 분리한다
(terraform kubernetes provider로 워크로드를 넣지 않는 이유: state가 뒤엉키고 `tofu destroy`가 워크로드까지
책임지게 되어 경계가 무너진다).

```
k8s/
  base/
    core-api.yaml      # Deployment + Service (ClusterIP) — :8080, 마이그레이션 실행자
    ai-api.yaml           # Stage C — :8081, 내부 전용(인증 없음 → NetworkPolicy 로 격리)
    daily-api.yaml        # Stage C — :8082, e2e 진입점(port-forward 대상)
    networkpolicy-ai-api.yaml  # Stage C — ai-api 에 core-api·daily-api 만 허용(ingress-only)
    postgres.yaml         # Stage 3a — StorageClass(동적) + Headless Service + StatefulSet(volumeClaimTemplates)
    postgres-static.yaml  # Stage 3b — StorageClass(no-provisioner) + PV + PVC + Service + StatefulSet
  eso/
    secretstore.yaml
    externalsecret-app.yaml
    externalsecret-db.yaml            # Stage 2 (RDS 모드) — ARN 치환 필요
    externalsecret-db-incluster.yaml  # Stage 3a (in-cluster 모드) — 치환 없음
    externalsecret-postgres-tls.yaml  # Stage 3a — Postgres 서버 인증서
```

> ⚠️ **배타 쌍이 둘 있다. 각각 하나만 적용한다.**
>
> | 쌍 | 충돌하는 것 | 고르는 기준 |
> |---|---|---|
> | `externalsecret-db.yaml` ↔ `-incluster.yaml` | 같은 이름의 Secret `core-api-db` | `db_mode` |
> | `postgres.yaml` ↔ `postgres-static.yaml` | 같은 이름의 StatefulSet·Service `postgres` | Stage 3a / 3b |
>
> 동시에 적용하면 두 소유자가 다퉈 값이 왔다갔다 한다.
> **3a 파일을 지우지 않는 이유**는 `rds.tf`를 안 지운 것과 같다 — 지나온 Stage가
> 코드에서 재현 불가가 되면 튜토리얼이 문서만 남고 실행이 안 되는 상태가 된다.

## 배포 절차 (과금 세션 중)

> 전체 세션 흐름은 `docs/eks-session-sop.md`가 단일 출처. 여기는 **앱 배포 부분만** 다룬다.

### 1. 이미지 준비 (클러스터 없어도 가능, $0)

GitHub Actions → **ECR Push** 워크플로 수동 실행(`workflow_dispatch`) → service 선택(`core-api`).
완료되면 Step Summary에 이미지 주소가 찍힌다:

```
<account>.dkr.ecr.ap-northeast-2.amazonaws.com/devquest/core-api:<git-sha>
```

**항상 sha 태그를 쓴다. `latest`로 배포하지 않는다** — 롤백 불가 + 지금 뭐가 도는지 추적 불가.

> ⚠️ **CLI로 태그를 뽑을 땐 `imageTags[0]`을 쓰지 마라 (07-28에 실제로 밟은 함정).**
> 한 이미지는 태그를 여러 개 갖고(`<sha>` + `latest`) **배열 순서는 보장되지 않는다** —
> 그대로 쓰면 금지된 `latest`로 배포된다. 40자리 hex만 골라야 한다:
> ```bash
> SHA=$(aws ecr describe-images --repository-name devquest/core-api --region ap-northeast-2 \
>   --query 'sort_by(imageDetails,&imagePushedAt)[-1].imageTags' --output json \
>   | ruby -rjson -e 'puts JSON.parse(STDIN.read).find{|t| t =~ /\A[0-9a-f]{40}\z/}')
> ```

### 2. Secret 주입 — External Secrets Operator (Stage 2~)

`application-prod.yml`이 100% 환경변수 기반이라 **코드 변경 없이** DB를 갈아끼운다.

> **Stage 1까지는 `kubectl create secret`으로 손수 만들었다. Stage 2부터는 하지 않는다.**
> 손으로 만든 Secret은 ① 누가 언제 넣었는지 기록이 없고 ② 값이 셸 히스토리에 남고
> ③ 로테이션되지 않으며 ④ 클러스터를 재생성할 때마다 사람이 다시 쳐야 한다.
> ESO가 AWS Secrets Manager를 단일 출처로 삼아 K8s Secret을 **자동 생성·동기화**한다.

```bash
# ① ESO 설치 (IRSA 역할을 ServiceAccount에 연결 — 정적 액세스키 없음)
ESO_ROLE=$(tofu -chdir=infra/aws-eks/2-cluster output -raw eso_role_arn)
helm repo add external-secrets https://charts.external-secrets.io && helm repo update
helm install external-secrets external-secrets/external-secrets \
  --version 2.8.0 --namespace external-secrets --create-namespace \
  --set installCRDs=true \
  --set "serviceAccount.annotations.eks\.amazonaws\.com/role-arn=$ESO_ROLE" \
  --wait --timeout 5m

# ② SecretStore + ExternalSecret 적용
kubectl apply -f k8s/eso/secretstore.yaml
kubectl apply -f k8s/eso/externalsecret-app.yaml

# ③ db용 — 🔴 db_mode에 따라 갈린다 (둘 중 하나만!)

#   [Stage 2 / db_mode=rds] RDS가 만든 마스터 시크릿 ARN을 치환해야 한다
#   (이름을 AWS가 rds!db-<uuid> 형태로 정하므로 코드에 못 박는다)
ARN=$(tofu -chdir=infra/aws-eks/2-cluster output -raw db_master_secret_arn)
sed "s|RDS_MASTER_SECRET_PLACEHOLDER|$ARN|" k8s/eso/externalsecret-db.yaml | kubectl apply -f -

#   [Stage 3a / db_mode=in-cluster] 치환이 필요 없다 — 시크릿 이름을 우리가 정했으므로.
#   + Postgres 서버 인증서도 함께 (tofu가 발급, 아래 §2.5 참조)
kubectl apply -f k8s/eso/externalsecret-db-incluster.yaml
kubectl apply -f k8s/eso/externalsecret-postgres-tls.yaml

# ④ 동기화 확인 — Ready=True 여야 한다
kubectl get externalsecret
```

> 🔑 **"관리형이 편한 대신 이름을 못 정한다"는 트레이드오프를 양쪽에서 겪게 된다.**
> Stage 2에서 `sed` 치환이 귀찮았던 이유가, 3a에서 그게 사라지는 것으로 증명된다.
> 반대로 잃은 것도 있다 — RDS는 비밀번호를 **자동 로테이션**했지만 우리 건 안 한다.

### 2.5 in-cluster Postgres 배포

**Stage 3a — 동적 PVC (세션 휘발)**

```bash
kubectl apply -f k8s/base/postgres.yaml   # StorageClass + Headless Service + StatefulSet
kubectl get pvc                            # 처음엔 Pending이 정상 (WaitForFirstConsumer)
kubectl get pvc -w                         # Bound 까지 (~11초)
```

**Stage 3b — static PV (영속)** · `infra/aws-eks/2-cluster`에서 실행

```bash
sed -e "s|EBS_VOLUME_ID_PLACEHOLDER|$(tofu output -raw postgres_data_volume_id)|" \
    -e "s|PERSISTENT_AZ_PLACEHOLDER|$(tofu output -raw persistent_az)|" \
    ../../../k8s/base/postgres-static.yaml | kubectl apply -f -

kubectl get pv postgres-data          # STATUS: Available → Bound
kubectl get pvc postgres-data         # VOLUME 이 postgres-data 인지 확인
```

> 🔑 **3a와 달리 치환이 필요하다.** 볼륨 ID는 세션이 바뀌어도 같지만(영속) 계정마다 다르고,
> 퍼블릭 레포에 리소스 ID를 박아두지 않는다. `core-api.yaml`의 `IMAGE_PLACEHOLDER`와 같은 패턴.
>
> 🔴 **PV/PVC가 파드보다 먼저 있어야 한다.** PVC가 Bound가 아니면 파드는 스케줄조차 되지 않는다.
> 순서: ESO 동기화 → **PV·PVC** → postgres → core-api.
>
> 🔎 **PVC가 Pending에서 안 넘어가면** 셋 중 하나다:
> | 증상 | 원인 | 확인 |
> |---|---|---|
> | `no persistent volumes available` | PV·PVC의 `storageClassName`·용량 불일치 | `kubectl get pv,pvc -o wide` |
> | PV가 `Released` | 이전 PVC의 `claimRef`가 남음 (실패 6종 ④) | `kubectl patch pv postgres-data -p '{"spec":{"claimRef":null}}'` |
> | 파드만 Pending | 노드 AZ ≠ 볼륨 AZ (실패 6종 ①) | `kubectl describe pod postgres-0` → `node(s) had volume node affinity conflict` |

> 🔴 **앱보다 먼저 띄워야 한다.** StatefulSet이 `core-api-db` Secret에서 비밀번호를 읽으므로
> ESO 동기화(위 ③)가 끝난 뒤여야 하고, core-api는 이 DB에 붙으므로 그 뒤여야 한다.
>
> 🔴 **TLS가 필요하다.** `application-prod.yml`의 jdbc-url이 `?sslmode=require`로 **하드코딩**돼
> 있는데 맨 postgres 이미지는 TLS를 안 켠다 → `PSQLException: The server does not support SSL.`
> RDS는 켜진 채로 오기 때문에 Stage 2에서는 드러나지 않았다. **관리형이 공짜로 주던 것.**

> ⚠️ **Secret 값을 레포/일지/PR 어디에도 쓰지 않는다.** K8s Secret은 base64일 뿐 암호화가 아니다.
>
> 🔎 **ExternalSecret이 `SecretSyncedError`면 두 층을 구분해서 본다** (07-28 실측):
> - `Not authorized to perform sts:AssumeRoleWithWebIdentity` → **인증** 실패.
>   신뢰정책의 `sub`가 `system:serviceaccount:<ns>:<sa>`와 다르다. IRSA 최다 실패 지점.
> - `AccessDeniedException ... no identity-based policy allows` → **인가** 실패.
>   assume는 됐고 권한 정책이 없거나 리소스 ARN이 안 맞는다.

### 3. 배포

**Stage C 부터 3서비스다.** `sed` 치환을 서비스마다 한 번씩 한다(kustomize 를 쓰지 않는 이유는 이 레포가
생 yaml + 치환으로 일관돼 있기 때문 — 도구를 하나 더 들이는 것보다 절차가 눈에 보이는 편을 택했다).

```bash
REG=<account>.dkr.ecr.ap-northeast-2.amazonaws.com

# ① core-api — **마이그레이션 실행자다. 반드시 먼저.**
#    daily-api 는 Flyway 를 안 돌리므로(그 이유는 daily-api.yaml 주석 참고)
#    스키마가 없는 상태로 뜨면 ddl-auto: validate 에 걸려 기동 실패한다.
sed "s|IMAGE_PLACEHOLDER|$REG/devquest/core-api:<sha>|"  k8s/base/core-api.yaml  | kubectl apply -f -
kubectl rollout status deploy/core-api --timeout=5m

# ② ai-api / daily-api
sed "s|IMAGE_PLACEHOLDER|$REG/devquest/ai-api:<sha>|"    k8s/base/ai-api.yaml    | kubectl apply -f -
sed "s|IMAGE_PLACEHOLDER|$REG/devquest/daily-api:<sha>|" k8s/base/daily-api.yaml | kubectl apply -f -
```

> ⚠️ **서비스마다 sha 가 다를 수 있다.** 세 이미지를 각각 다른 시점에 푸시했다면 태그도 다르다.
> §1 의 태그 추출 스니펫을 `--repository-name devquest/<service>` 로 바꿔 서비스별로 뽑아라.

### 3.5 NetworkPolicy — 🔴 **순서를 틀리면 조용히 무효가 된다**

```bash
kubectl apply -f k8s/base/networkpolicy-ai-api.yaml
```

**전제**: `2-cluster/addons.tf` 의 vpc-cni 에 `enableNetworkPolicy = "true"` 가 켜져 있어야 한다.
`NetworkPolicy` 는 K8s **코어 API** 라 강제하는 CNI 가 없어도 API 서버가 **정상 수용**하고
`... created` 를 출력한다 — **에러도 경고도 없다.** 즉 *"걸었다고 믿는데 안 걸린 상태"* 가 만들어진다.

```bash
# 플래그가 실제로 살아있는지 — tofu apply 출력으로 대신하지 마라(그건 "보냈다"만 말한다)
aws eks describe-addon --cluster-name devquest-eks --addon-name vpc-cni \
  --region ap-northeast-2 --query 'addon.configurationValues'
kubectl get ds aws-node -n kube-system \
  -o jsonpath='{.spec.template.spec.containers[*].name}'   # 두 번째 컨테이너 = 노드에이전트
```

### 4. 검증

```bash
kubectl get pods -w                     # 전부 Running + READY 1/1 (startupProbe 로 최대 5분)
kubectl logs deploy/core-api --tail=50  # Flyway 마이그레이션
kubectl describe pod -l app=ai-api      # Pending·ImagePullBackOff 등 진단
```

#### 4.1 NetworkPolicy — **차단/허용 쌍**으로만 증명된다

*"created 됐다"* 는 증명이 아니다. 그리고 **파드 슬롯을 1칸도 쓰지 않고** 검증할 수 있다 —
임시 파드를 띄우지 말고 **이미 뜬 `postgres-0` 에 exec** 한다(`kubectl run` 선례는 이 레포에 0건).

```bash
# ⚠️ **URL 을 변수로 둔다. 호스트·포트·경로를 한 줄에 붙여 쓰지 말 것.**
#    붙여 쓰면 gitleaks 의 generic-api-key 가 오탐한다 — 규칙이 `api` 를 키워드로 보고
#    콜론 뒤 10자 이상을 값으로 잡는다(entropy 3.55). 2026-08-31 에 실제로 CI 가 막혔다.
#    🔴 되돌리지 말 것. 그리고 **유발 형태를 여기에 그대로 인용하지도 말 것** —
#    인용 자체가 재검출된다(.gitleaksignore 가 같은 사고를 이미 기록해뒀고, 나도 한 번 밟았다).
#    지문 등록은 해법이 아니다: squash merge 로 SHA 가 바뀌면 무효가 된다.
AI_URL=http://ai-api:8081

# ① 🔑 정책 적용 **전** — 성공해야 한다
kubectl exec postgres-0 -- wget -qO- --timeout=5 "$AI_URL/actuator/health"

# ② 정책 적용
kubectl apply -f k8s/base/networkpolicy-ai-api.yaml

# ③ 같은 명령 → **타임아웃/행** 이어야 한다 (VPC CNI 의 거부는 Connection refused 가 아니다)
kubectl exec postgres-0 -- wget -qO- --timeout=5 "$AI_URL/actuator/health"

# ④ 양성 대조 — 허용된 호출자는 **여전히 성공**해야 한다
POD=$(kubectl get pod -l app=daily-api -o jsonpath='{.items[0].metadata.name}')
kubectl exec "$POD" -- wget -qO- --timeout=5 "$AI_URL/actuator/health"

# ⑤ probe 가 막히지 않았는지 — RESTARTS 가 0 인지 최소 1 주기 관찰
kubectl get pod -l app=ai-api -w
```

> ⚠️ **①이 없으면 ③은 아무것도 증명하지 않는다** — 파드 미기동·Service 셀렉터 오류·DNS 실패가
> 전부 "막혔다"와 똑같이 생겼다.
> ⚠️ **④가 없으면** `podSelector: {}` + 빈 ingress(전면 차단)도 ③을 "통과" 한다.
> ⚠️ 정책이 **0개 파드를 매칭**해도 생성은 성공한다. `kubectl describe netpol` 은 매칭 수를 알려주지 않는다:
> ```bash
> kubectl get netpol -o wide          # POD-SELECTOR 확인
> kubectl get pods -l app=ai-api      # 그 셀렉터가 실제로 잡는 파드
> ```
> ⚠️ [미확인] `postgres:17-alpine` 에 `wget` 이 있는지 레포 기록 0건 — 이 레포는 정확히 이 가정으로 데인 적이 있다
> (실측 `sh: openssl: not found`). ①이 그 확인을 겸한다.

#### 4.2 e2e — 무로그인 오늘의 질문 → 설명 (Stage C 완료 기준)

```bash
kubectl port-forward svc/daily-api 8082:8082 &

curl -s localhost:8082/api/v1/daily-question              # 200 + question (AI 호출 없음, 뱅크에서)
curl -s -X POST localhost:8082/api/v1/daily-question/explain \
  -H 'Content-Type: application/json' \
  -d '{"question":"q","answer":"a","feedback":"f","userQuestion":"왜?"}'
```

두 번째 호출이 **daily-api → `http://ai-api:8081` → 스텁** 을 거쳐 돌아온다.
🔑 응답에 **`[STUB]` 표식**이 있어야 한다 — 없으면 스텁이 아니라 실제 AI 를 부른 것이거나(키가 없으니 실패했을 것)
전혀 다른 경로를 탄 것이다. **증명하는 것은 토폴로지이지 AI 품질이 아니다**(결정 D-008).

### 5. 정리 (destroy 전 필수)

```bash
# ① ESO가 소유한 것부터 — 순서가 중요하다
kubectl delete externalsecret --all -A
kubectl delete secretstore --all -A

# ② 워크로드
kubectl delete -f k8s/base/ --ignore-not-found

# ③ 🔴 PVC — Stage 3a에서만 필수. **3b는 다르다(아래 표)**
#    StatefulSet을 지워도 volumeClaimTemplates가 만든 PVC는 **의도적으로 남는다**(데이터 보호).
#    그리고 그 볼륨은 tofu state 밖이라 destroy가 못 지운다. 07-30 실측:
#      StatefulSet 삭제 직후  → vol-0c327...  in-use   ← 살아있다
#      PVC 삭제 후 7초        → 볼륨 없음              ← reclaimPolicy:Delete가 회수
kubectl delete pvc --all -A
aws ec2 describe-volumes --region ap-northeast-2 \
  --filters "Name=tag:ebs.csi.aws.com/cluster,Values=true" \
  --query 'Volumes[].[VolumeId,State]' --output text     # 비어야 한다
```

> 🔴 **3a와 3b는 teardown이 다르다. 감각으로 옮기면 사고가 난다.**
>
> | | Stage 3a (동적) | Stage 3b (static) |
> |---|---|---|
> | PVC 삭제 | **필수** — 안 하면 EBS 고아 과금 | 해도 되고 안 해도 된다 |
> | 삭제 시 EBS | **함께 사라짐** (`reclaimPolicy: Delete`) | **남는다** (`Retain` + IAM이 CSI 삭제 거부) |
> | 남은 볼륨 | 고아 = 사고 | **정상** — 다음 세션에 다시 붙는다 |
> | 고아 검사 | `available` 전부가 고아 | `Persistent=true`는 **제외**해야 함 (SOP §9) |
>
> 3b에서 PVC를 지우면 PV가 `Released` + `claimRef` 잔존 상태가 된다. 클러스터를 통째로
> 부술 거라면 무해하지만, **세션 중에 다시 적용하면 바로 실패 6종 ④를 밟는다.**
> 그래서 3b의 권장 순서는 **PVC를 남긴 채 클러스터를 destroy**하는 것이다.

> 🔴 **`kubectl delete secret core-api-db`로 지우면 안 된다.** ExternalSecret이 `creationPolicy: Owner`라
> **Secret이 즉시 되살아난다.** 07-28 실측 — 삭제 8초 뒤 같은 이름의 **다른 객체**로 부활했다:
> ```
> 삭제 전 UID: 94fe931e-42d4-4a3f-9715-8337b3266142
> 8초 후 UID : db35e78b-2fa5-44e6-9b13-d2eda253580e
> ```
> 반대로 **소유자인 ExternalSecret을 지우면 K8s Secret도 함께 GC된다**(`core-api-db`·`core-api-app` 둘 다).
> ESO 자체(Helm 릴리스)는 클러스터와 함께 사라지므로 따로 지울 필요 없다.

이번 Stage는 **ClusterIP만 쓰므로 AWS 고아 리소스가 생기지 않는다**(LoadBalancer/Ingress를 쓰면 NLB/ALB가
tofu state 밖에 생겨 destroy 후에도 과금된다 — Stage 4에서 다룬다). 그래도 습관화를 위해 위 정리를 먼저 하고
`tofu destroy`로 넘어간다.

> 전체 세션의 teardown 순서·고아 전수 검증은 `docs/eks-session-sop.md` §8~§9가 단일 출처다.

## 자주 만나는 실패

| 증상 | 원인 | 확인 |
|------|------|------|
| `ImagePullBackOff` | ECR 권한/주소 오타/아키텍처 불일치 | `kubectl describe pod` 이벤트. 노드는 arm64 — arm64 이미지여야 함 |
| `Pending` | 노드 자원 부족(requests 합 > 노드 여유) | `kubectl describe pod` → `Insufficient cpu/memory` |
| `CrashLoopBackOff` | 환경변수 누락 (부팅 순서대로 터진다) | `kubectl logs` — 아래 순서로 원인이 드러남 |
| 무한 재시작(기동 중) | startupProbe 없이 liveness가 먼저 때림 | 본 매니페스트는 startupProbe로 방어 |

> ⚠️ **CrashLoop 원인은 DB가 아닐 수 있다 (실측, 2026-07-27).** prod 프로파일은 부팅 순서상
> **로깅 → 빈 생성 → DB** 로 초기화되므로, 누락된 환경변수가 **DB보다 먼저** 앱을 죽인다:
> 1. **Loki 로깅** — `${GRAFANA_LOKI_URL}` 등 미설정 → `URI with undefined scheme` (로깅 단계, 제일 먼저)
> 2. **`JWT_SECRET`** — `Could not resolve placeholder 'JWT_SECRET'` (빈 생성 단계)
> 3. **DB** — `HikariPool → entityManagerFactory` (마지막)
>
> prod 필수 환경변수(기본값 없음): `DB_HOST/NAME/USERNAME/PASSWORD` · `JWT_SECRET` ·
> `GITHUB_CLIENT_ID/SECRET`, 그리고 logback이 요구하는 `GRAFANA_LOKI_URL`·`GRAFANA_LOKI_INSTANCE_ID`·`GRAFANA_API_KEY`.
> **미리 전수 파악**하는 편이 하나씩 재배포하는 두더지잡기보다 빠르다. 단, 변수는 **두 가지 형식**으로
> 참조된다 — yml은 `${ENV_VAR}`, logback은 `<springProperty source="ENV_VAR">`(참조는 camelCase `${grafanaLokiUrl}`).
> **대문자 `${...}`만 훑으면 Loki 변수를 통째로 놓친다**(실제 인시던트 1번 원인이었음). 두 패턴을 함께:
> ```bash
> grep -rhoE '\$\{[A-Z_][A-Z0-9_]*|source="[A-Z_][A-Z0-9_]*"' be \
>   --include='application*.yml' --include='logback*.xml' \
>   | grep -oE '[A-Z_][A-Z0-9_]+' | sort -u
> ```
> (검증: GRAFANA_LOKI_URL/INSTANCE_ID/API_KEY 포함 20개 매치. `ENV`는 무해한 오탐.)
