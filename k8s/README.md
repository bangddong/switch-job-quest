# K8s 매니페스트 (EKS Stage 1+)

앱을 EKS에 배포하는 선언형 매니페스트. **클라우드 자원은 OpenTofu, K8s 워크로드는 여기** — 두 평면을 분리한다
(terraform kubernetes provider로 워크로드를 넣지 않는 이유: state가 뒤엉키고 `tofu destroy`가 워크로드까지
책임지게 되어 경계가 무너진다).

```
k8s/
  base/
    core-api.yaml      # Deployment + Service (ClusterIP)
```

## 배포 절차 (과금 세션 중)

> 전체 세션 흐름은 `docs/eks-session-sop.md`가 단일 출처. 여기는 **앱 배포 부분만** 다룬다.

### 1. 이미지 준비 (클러스터 없어도 가능, $0)

GitHub Actions → **ECR Push** 워크플로 수동 실행(`workflow_dispatch`) → service 선택(`core-api`).
완료되면 Step Summary에 이미지 주소가 찍힌다:

```
<account>.dkr.ecr.ap-northeast-2.amazonaws.com/devquest/core-api:<git-sha>
```

**항상 sha 태그를 쓴다. `latest`로 배포하지 않는다** — 롤백 불가 + 지금 뭐가 도는지 추적 불가.

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

# ③ db용은 RDS가 만든 마스터 시크릿 ARN을 치환해야 한다(이름을 AWS가 정하므로 코드에 못 박음)
ARN=$(tofu -chdir=infra/aws-eks/2-cluster output -raw db_master_secret_arn)
sed "s|RDS_MASTER_SECRET_PLACEHOLDER|$ARN|" k8s/eso/externalsecret-db.yaml | kubectl apply -f -

# ④ 동기화 확인 — Ready=True 여야 한다
kubectl get externalsecret
```

> ⚠️ **Secret 값을 레포/일지/PR 어디에도 쓰지 않는다.** K8s Secret은 base64일 뿐 암호화가 아니다.
>
> 🔎 **ExternalSecret이 `SecretSyncedError`면 두 층을 구분해서 본다** (07-28 실측):
> - `Not authorized to perform sts:AssumeRoleWithWebIdentity` → **인증** 실패.
>   신뢰정책의 `sub`가 `system:serviceaccount:<ns>:<sa>`와 다르다. IRSA 최다 실패 지점.
> - `AccessDeniedException ... no identity-based policy allows` → **인가** 실패.
>   assume는 됐고 권한 정책이 없거나 리소스 ARN이 안 맞는다.

### 3. 배포

```bash
IMAGE=<account>.dkr.ecr.ap-northeast-2.amazonaws.com/devquest/core-api:<sha>
sed "s|IMAGE_PLACEHOLDER|$IMAGE|" k8s/base/core-api.yaml | kubectl apply -f -
```

### 4. 검증

```bash
kubectl get pods -w                    # Running + READY 1/1 까지 (startupProbe로 최대 5분 허용)
kubectl logs deploy/core-api --tail=50 # Spring 기동 로그 / Flyway 마이그레이션
kubectl describe pod -l app=core-api   # Pending·ImagePullBackOff 등 진단
kubectl port-forward svc/core-api 8080:8080 &
curl -s localhost:8080/health          # 200 기대
```

### 5. 정리 (destroy 전 필수)

```bash
kubectl delete -f k8s/base/ --ignore-not-found
kubectl delete secret core-api-db --ignore-not-found
```

이번 Stage는 **ClusterIP만 쓰므로 AWS 고아 리소스가 생기지 않는다**(LoadBalancer/Ingress를 쓰면 NLB/ALB가
tofu state 밖에 생겨 destroy 후에도 과금된다 — Stage 4에서 다룬다). 그래도 습관화를 위해 위 정리를 먼저 하고
`tofu destroy`로 넘어간다.

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
