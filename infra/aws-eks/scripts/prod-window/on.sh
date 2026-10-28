#!/usr/bin/env bash
# on 의 apply **뒤** 단계 — 📌D-016 8-B. 사용: on.sh <이미지 태그(40자 sha)> <git 리비전(40자 sha)>
# 2-cluster 를 apply 한 직후, 레포 루트에서 실행한다. 실패하면 rc≠0 → 워크플로가 같은 실행에서 off 를 돈다
# (8-B 는 **트래픽 전환이 없다** — 켠 EKS 가 망가져도 사용자 영향 0, 그래서 실패 = 즉시 끄기가 안전하다).
#
# 하는 일 (튜토리얼 5e-1~5e-4 + Stage 4 의 CI 판):
#   ① 관문: metrics-server APIService Available + `/openapi/v2` protobuf 200(5e-2 — `.12` 503 이면 ArgoCD 가 클러스터를 못 읽는다)
#   ② LBC(helm, Stage 4 인자 그대로) → ③ ArgoCD(helm, values 파일) → ④ Application(prod: apps-external·리비전 고정·이미지 태그)
#   ⑤ 판정: Application Synced+Healthy+Succeeded **동시**(win_app_verdict) · ExternalSecret 전부 Ready · `core-api-runtime` 키 수 > 0 + 로그인 키 값(B7-16 — 부재는 워크플로 사전 점검)(8-A QA F-2 — optional envFrom 은
#      Secret 이 비어도 파드를 띄운다) · Ingress 주소
#   ⑥ 스모크: ALB IP 로 `--resolve <학습 도메인>`(DNS 를 안 바꾼다 — 트래픽 0) · daily-question 200+question · core-api 보호 경로 403
#
# 🔴 계정 ID 가 들어가는 값(레지스트리·IRSA ARN·인증서 ARN)은 **파이프로만** kubectl/helm 에 넘긴다. 파일로 저장하지 않는다.
# 🔑 리비전을 **실행 커밋 sha 로 고정**한다(B8-6) — targetRevision: main 이면 창 중 main 머지가 그대로 prod EKS 에 sync 된다.
#    이미지 태그는 git 이 아니라 Application 의 kustomize.images 로 덮는다(git 커밋 없이 그날 Fly 커밋을 따른다 — pick-image-tag.sh).
set -euo pipefail
TAG="${1:?이미지 태그}"; REV="${2:?git 리비전}"
. "$(dirname "$0")/common.sh"
ROOT=$(git rev-parse --show-toplevel)
DOMAIN="${WIN_SMOKE_DOMAIN:-eks.quest.dhbang.co.kr}"   # 0-bootstrap learning_domain_name 기본값 — ACM 인증서의 이름
[[ "$TAG" =~ ^[0-9a-f]{40}$ ]] || { wlog "이미지 태그 형식 이상: $TAG"; exit 1; }
[[ "$REV" =~ ^[0-9a-f]{40}$ ]] || { wlog "리비전 형식 이상: $REV"; exit 1; }

out() { tofu -chdir="$ROOT/infra/aws-eks/2-cluster" output -raw "$1"; }
# 형태 검사 — `tofu output` 은 실패해도 `Warning: No outputs found` 를 rc 0 으로 낸다(튜토리얼 4-3·4-4). -z 로는 부족하다.
# shellcheck disable=SC2254  # $3 은 의도된 glob 패턴이다
shape() { case "$2" in $3) ;; *) wlog "🔴 $1 형태 이상 — 멈춘다"; exit 1 ;; esac; }
ESO_ROLE=$(out eso_role_arn);        shape eso_role_arn "$ESO_ROLE" 'arn:aws:iam::*:role/*'
ALB_ROLE=$(out alb_controller_role_arn); shape alb_controller_role_arn "$ALB_ROLE" 'arn:aws:iam::*:role/*'
VPC=$(out vpc_id);                   shape vpc_id "$VPC" 'vpc-*'
APP_SECRET=$(out app_secret_name);   shape app_secret_name "$APP_SECRET" 'devquest-eks/*'
CERT=$(out acm_certificate_arn);     shape acm_certificate_arn "$CERT" "arn:aws:acm:$WIN_REGION:*:certificate/*"
APPS_PATH=$(out argocd_apps_path)
[ "$APPS_PATH" = "k8s/gitops/apps-external" ] || { wlog "🔴 argocd_apps_path=$APPS_PATH — prod 는 external 이어야 한다(prod.auto.tfvars.json 이 빠졌나)"; exit 1; }
ACCT=$(aws sts get-caller-identity --query Account --output text)
[[ "$ACCT" =~ ^[0-9]{12}$ ]] || { wlog "계정 ID 조회 실패"; exit 1; }
REGISTRY="$ACCT.dkr.ecr.$WIN_REGION.amazonaws.com"

aws eks update-kubeconfig --region "$WIN_REGION" --name "$WIN_CLUSTER" >/dev/null

# 실패하면 그 시점의 상태를 로그에 남긴다 — off 잡이 곧 클러스터를 지우므로 **이것이 원인을 볼 유일한 기회**다.
#   2026-10-22 첫 실행은 이게 없어서 *"Synced 120초 초과"* 한 줄만 남았다(원인 미확정, 일지). 출력은 워크플로의 sed 가 계정 ID 를 가린다.
diag() {
  wlog "── 진단(실패 시점 — off 가 곧 지운다)"
  kubectl -n argocd get application devquest-apps -o json --request-timeout=20s 2>/dev/null | jq -c '{
      sync: .status.sync.status, health: .status.health.status, phase: .status.operationState.phase,
      opmsg: ((.status.operationState.message // "") | .[0:600]),
      conditions: [.status.conditions[]? | {type, message: ((.message // "") | .[0:600])}],
      notReady: [.status.resources[]? | select(.status != "Synced" or ((.health.status // "Healthy") != "Healthy"))
                 | {kind, name, status, health: .health.status, msg: ((.health.message // "") | .[0:200])}]}' >&2 || true
  kubectl get pods -A --request-timeout=20s >&2 || true
  # 기동 실패 원인(예: `${GITHUB_CLIENT_ID}` 미해결 PlaceholderResolutionException)은 파드 로그에만 남는다 — off 가 지우기 전에(QA F-2)
  kubectl logs deploy/core-api -n default --tail=40 --request-timeout=20s 2>&1 | cut -c1-400 >&2 || true
  kubectl get events -A --sort-by=.lastTimestamp --request-timeout=20s 2>/dev/null | tail -25 >&2 || true
  kubectl top nodes --request-timeout=20s >&2 || true
}
# 트랩은 ① **앞에** 건다 — 관문 실패(metrics-server 300s·openapi)도 진단을 남긴다(QA F-2). proxy 정리도 여기서 한다.
PROXY=""
on_exit() {
  local rc=$?
  if [ -n "$PROXY" ]; then kill "$PROXY" 2>/dev/null || true; fi
  [ "$rc" = 0 ] || diag
  exit "$rc"
}
trap on_exit EXIT

# ── ① 관문 ────────────────────────────────────────────────────────────────
wlog "① metrics-server · openapi 관문"
kubectl wait --for=condition=Available apiservice/v1beta1.metrics.k8s.io --timeout=300s
kubectl proxy --port=8771 >/dev/null 2>&1 & PROXY=$!
CODE=000
for _ in $(seq 1 30); do
  CODE=$(curl -s -o /dev/null -w '%{http_code}' \
           -H 'Accept: application/com.github.proto-openapi.spec.v2@v1.0+protobuf' localhost:8771/openapi/v2) || CODE=000
  [ "$CODE" = 200 ] && break; sleep 2
done
kill "$PROXY" 2>/dev/null || true; PROXY=""
[ "$CODE" = 200 ] || { wlog "🔴 /openapi/v2 protobuf = $CODE — ArgoCD 가 클러스터를 못 읽는다(5e-0). 끈다"; exit 1; }

# ── ② LBC ─────────────────────────────────────────────────────────────────
wlog "② AWS Load Balancer Controller"
helm repo add eks https://aws.github.io/eks-charts >/dev/null
helm repo add argo https://argoproj.github.io/argo-helm >/dev/null
helm repo update >/dev/null
# 인자 근거는 튜토리얼 4-3 표(region·vpcId 생략 시 IMDS CrashLoop · replicaCount 기본 2 · requests 없으면 BestEffort)
helm upgrade --install aws-load-balancer-controller eks/aws-load-balancer-controller \
  --version 3.5.0 --namespace kube-system \
  --set clusterName="$WIN_CLUSTER" --set region="$WIN_REGION" --set vpcId="$VPC" \
  --set replicaCount=1 \
  --set resources.requests.cpu=50m --set resources.requests.memory=96Mi --set resources.limits.memory=192Mi \
  --set "serviceAccount.annotations.eks\.amazonaws\.com/role-arn=$ALB_ROLE" \
  --wait --timeout 5m >/dev/null

# ── ③ ArgoCD ──────────────────────────────────────────────────────────────
wlog "③ ArgoCD"
helm upgrade --install argocd argo/argo-cd --version 10.10.2 -n argocd --create-namespace \
  -f "$ROOT/k8s/argocd/values.yaml" --wait --timeout 5m >/dev/null

# ── ④ Application ─────────────────────────────────────────────────────────
# 원본 application.yaml(학습·in-cluster)을 그대로 두고 prod 차이만 yq 로 덮는다 — 사본 파일을 만들지 않는다(두 벌이 어긋나지 않게).
#   path → apps-external · targetRevision → 실행 커밋 · images → 그날 태그 · Ingress 인증서 패치 추가
#   PV 패치(볼륨·AZ)는 external 에서 대상이 없어 아무것도 안 바꾼다(apps-external 머리말) — 자리표시만 채운다.
wlog "④ Application (리비전 ${REV:0:8} · 이미지 ${TAG:0:8})"
sed -e "s|REGISTRY_PLACEHOLDER|$REGISTRY|g" -e "s|REVISION_PLACEHOLDER|$REV|" \
    -e "s|ESO_ROLE_ARN_PLACEHOLDER|$ESO_ROLE|" -e "s|APP_SECRET_NAME_PLACEHOLDER|$APP_SECRET|" \
    -e "s|EBS_VOLUME_ID_PLACEHOLDER|unused-external|" -e "s|PERSISTENT_AZ_PLACEHOLDER|unused-external|" \
    "$ROOT/k8s/gitops/application.yaml" \
| TAG="$TAG" REGISTRY="$REGISTRY" CERT="$CERT" yq '
    (.spec.sources[] | select(has("path"))) |= (
      .path = "k8s/gitops/apps-external"
      | .kustomize.images = [
          "devquest/core-api="  + strenv(REGISTRY) + "/devquest/core-api:"  + strenv(TAG),
          "devquest/ai-api="    + strenv(REGISTRY) + "/devquest/ai-api:"    + strenv(TAG),
          "devquest/daily-api=" + strenv(REGISTRY) + "/devquest/daily-api:" + strenv(TAG)]
      | .kustomize.patches += [{
          "target": {"kind": "Ingress", "name": "devquest"},
          "patch": "- op: replace\n  path: /metadata/annotations/alb.ingress.kubernetes.io~1certificate-arn\n  value: " + strenv(CERT)}]
    )' \
| kubectl apply -f - >/dev/null
# 남은 자리표시자가 없는지 — 빠지면 글자 그대로 들어가 조용히 틀린다
LEFT=$(kubectl -n argocd get application devquest-apps -o yaml | grep -c 'PLACEHOLDER' || true)
[ "$LEFT" = 0 ] || { wlog "🔴 Application 에 자리표시자 ${LEFT}개가 남았다"; exit 1; }

# ── ⑤ 판정 ────────────────────────────────────────────────────────────────
# 🔴 `kubectl wait --for=jsonpath=…Healthy` 를 따로 쓰지 않는다 — 2026-10-22 첫 실행에서 **6초 만에 통과**했다(리소스가 아직 없거나
#    렌더가 실패한 Application 도 Healthy). 세 값을 **한 번에** 읽어 win_app_verdict(common.sh)로 판정한다. 회귀는 window-test.sh.
wlog "⑤ Application 동기화 완료 대기(최대 15분 — Synced·Healthy·Succeeded 동시)"
ST="- - - -"; VERDICT="wait"; FAILS=0; PREV=""; T0=$(date +%s)
for _ in $(seq 1 90); do
  ST=$(kubectl -n argocd get application devquest-apps -o json --request-timeout=20s 2>/dev/null | jq -r '[
         .status.sync.status // "-", .status.health.status // "-", .status.operationState.phase // "-",
         ((.status.conditions // []) | map(.type) | join(",") | if . == "" then "-" else . end)] | join(" ")') || ST="- - - -"
  [ -n "$ST" ] || ST="- - - -"
  VERDICT=$(win_app_verdict "$ST")
  # 상태가 바뀔 때만 경과 초와 함께 남긴다 — 성공해도 웨이브가 몇 분 걸렸는지가 timeout 튜닝 재료다(QA F-3)
  [ "$ST" = "$PREV" ] || { wlog "  +$(( $(date +%s) - T0 ))s  $ST"; PREV="$ST"; }
  case "$VERDICT" in
    ok)   break ;;
    # 3분 연속일 때만 — repo-server 콜드 fetch(차트+git)의 일시 ComparisonError 를 실패로 오판하지 않게(QA F-1).
    #   일찍 멈춰 아끼는 건 많아야 십여 분(≈ $0.1)이라 오판 비용이 더 크다
    fail) FAILS=$((FAILS + 1)); [ "$FAILS" -ge 18 ] && break ;;
    *)    FAILS=0 ;;
  esac
  sleep 10
done
wlog "Application: $ST → $VERDICT (+$(( $(date +%s) - T0 ))s)"
[ "$VERDICT" = ok ] || { wlog "🔴 Application 이 Synced+Healthy+Succeeded 가 되지 않았다 — 아래 진단"; exit 1; }
kubectl wait externalsecret --all -n default --for=condition=Ready --timeout=120s
# 키 **이름**만 센다 — 값은 읽지 않는다
KEYS=$(kubectl get secret core-api-runtime -n default -o json | jq -r '.data // {} | keys | join(",")')
[ -n "$KEYS" ] || { wlog "🔴 core-api-runtime 이 비었다 — SSM /devquest/prod/app/ 에 키가 없거나 이름이 허용 목록과 다르다(TASK-17)"; exit 1; }
wlog "앱 외부 키: $KEYS"
wsummary "- 앱 외부 키(이름): $KEYS"
# 로그인 키(B7-16) — 없거나 FE 와 client id 가 다르면 창을 열지 않는다(C-4 1차: 파드·스모크는 통과, 로그인만 죽었다)
#   ⚠️ 키 **부재**는 여기까지 오지 않는다 — external 모드에선 core-api-app 에 GITHUB_* 가 없어 기동이 먼저 실패하고 ⑤ 가
#      15분 뒤 멈춘다(QA F-2). 그래서 부재는 워크플로 「로그인 키 사전 점검」(apply **전**, 과금 0)이 잡고, 여기는 값(FE 와 같은가 ·
#      끝 개행)을 본다. `$(…)` 는 끝 개행을 지우므로 바이트 수로 따로 본다(QA F-5).
CID=$(kubectl get secret core-api-runtime -n default -o jsonpath='{.data.GITHUB_CLIENT_ID}' 2>/dev/null | base64 -d 2>/dev/null || true)
CID_BYTES=$(kubectl get secret core-api-runtime -n default -o jsonpath='{.data.GITHUB_CLIENT_ID}' 2>/dev/null | base64 -d 2>/dev/null | wc -c | tr -d ' ' || true)
[ "${CID_BYTES:-0}" = "${#CID}" ] || { wlog "🔴 로그인 키: GITHUB_CLIENT_ID 끝에 개행이 있다(${CID_BYTES}B ≠ ${#CID}자) — SSM 값을 다시 넣는다"; exit 1; }
FE_CID=$(win_fe_client_id "$ROOT/.github/workflows/fe-cd.yml")
LV=$(win_login_keys_verdict "$KEYS" "$CID" "$FE_CID") || { wlog "🔴 로그인 키: $LV"; exit 1; }
wlog "✅ 로그인 키: GITHUB_CLIENT_ID(= FE 번들) · GITHUB_CLIENT_SECRET 있음"
kubectl rollout status deploy/core-api -n default --timeout=300s
# 3노드 메모리(ArgoCD + LBC + 앱) — 첫 실측 대상(계획서 8-B). 실패해도 판정에 쓰지 않는다
TOP=$(kubectl top nodes --no-headers --request-timeout=20s 2>/dev/null | awk '{printf "%s %s(%s) · ", $1, $4, $5}' || true)
wlog "노드 메모리: ${TOP:-조회 실패}"; wsummary "- 노드 메모리: ${TOP:-조회 실패}"

HOST=""
for _ in $(seq 1 60); do
  HOST=$(kubectl get ingress devquest -n default -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)
  [ -n "$HOST" ] && break; sleep 5
done
[ -n "$HOST" ] || { wlog "🔴 Ingress 에 ALB 주소가 5분 안에 안 붙었다 — kubectl describe ingress devquest 의 Events"; exit 1; }
# 렌더된 Ingress 의 인증서 — Application 검사는 패치 **값**만 본다. 패치가 대상을 못 찾으면 글자가 그대로 남는다(QA F-6)
ING_CERT=$(kubectl get ingress devquest -n default -o jsonpath='{.metadata.annotations.alb\.ingress\.kubernetes\.io/certificate-arn}')
case "$ING_CERT" in "arn:aws:acm:$WIN_REGION:"*":certificate/"*) ;; *) wlog "🔴 Ingress 인증서 annotation 형태 이상 — Application 패치가 안 먹었다"; exit 1 ;; esac

# ── ⑥ 스모크 (DNS 안 바꿈) ────────────────────────────────────────────────
IP=""
for _ in $(seq 1 60); do
  IP=$(getent ahostsv4 "$HOST" 2>/dev/null | awk 'NR==1{print $1}' || true)
  [ -n "$IP" ] && break; sleep 5
done
[ -n "$IP" ] || { wlog "🔴 ALB 이름이 5분 안에 안 풀렸다"; exit 1; }
wlog "⑥ 스모크 — $DOMAIN → ALB($IP)"
smoke() {  # $1 = 경로, $2 = 기대 코드. 타깃 등록·헬스체크가 끝날 때까지 재시도(최대 5분)
  local code=000
  for _ in $(seq 1 30); do
    code=$(curl -sS -o /tmp/eks-window-smoke.json -w '%{http_code}' --max-time 15 \
             --resolve "$DOMAIN:443:$IP" "https://$DOMAIN$1" 2>/dev/null) || code=000
    [ "$code" = "$2" ] && { wlog "✅ $1 → $code"; wsummary "- ✅ \`$1\` → $code (ALB 직접, DNS 미변경)"; return 0; }
    sleep 10
  done
  wlog "🔴 $1 → $code (기대 $2)"; return 1
}
smoke /api/v1/daily-question 200
# 200 이어도 본문이 비면 의미 없다(prod-smoke-daily.yml 과 같은 판정)
jq -e '(.data.question // "") | length > 0' /tmp/eks-window-smoke.json >/dev/null \
  || { wlog "🔴 daily-question 200 이지만 question 이 비었다"; exit 1; }
# core-api 경로 — 인증 없이 **403** 이면 ALB → core-api(Spring Security)까지 닿은 것이다. 5xx·502 면 타깃이 죽었다.
#   403 인 이유: SecurityConfig 에 authenticationEntryPoint 가 없어 기본(Http403ForbiddenEntryPoint)이다.
#   Fly prod 실측(2026-10-22): `curl https://api.quest.dhbang.co.kr/api/v1/progress` → 403. 같은 이미지라 같아야 한다.
smoke /api/v1/progress 403
wlog "✅ on 완료(ALB 직접 스모크) — 트래픽 전환은 다음 단계 switch-on.sh(traffic 입력)"
