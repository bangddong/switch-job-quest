#!/usr/bin/env bash
# prod 창(📌D-016) on/off 스크립트 공용 — source 해서 쓴다. 단독 실행하지 않는다.
#
# 🔑 임대(lease) — 📌D-016 8-B (Blindspot B4-5)
#   on 이 **apply 전에** SSM String 하나를 쓰고, off 가 destroy·검증을 끝낸 뒤 지운다.
#   값 = `expires=<epoch> run=<GitHub run id> started=<epoch>`.
#   매시 watchdog(eks-window.yml)이 *"클러스터가 있는데 임대가 없거나 만료됐다"* 를 보고 off 를 돈다
#   = CI 가 켠 클러스터의 dead man's switch. 로컬 리퍼(맥 launchd)는 원격·CI 클러스터를 못 본다(B4-22).
#   ⚠️ 임대는 **비밀이 아니다**(시각·실행 번호뿐) — SecureString 이 아니라 String 이다.
#   ⚠️ 경로는 `/devquest/ops/` — `/devquest/prod/db/` 는 EC2 역할이 읽는 곳, `/devquest/prod/app/` 은 ESO 가 읽는 곳이다.

WIN_REGION="${AWS_REGION:-ap-northeast-2}"
WIN_CLUSTER="${EKS_CLUSTER_NAME:-devquest-eks}"
WIN_LEASE_PARAM="${WIN_LEASE_PARAM:-/devquest/ops/eks-window/lease}"

wlog() { echo "[eks-window] $*" >&2; }
# 12자리 숫자(계정 ID)를 가린다 — CI 는 ::add-mask:: 도 걸지만 Step Summary 는 마스킹되지 않는다.
wmask() { sed -E 's/[0-9]{12}/<account>/g'; }
wsummary() { [ -n "${GITHUB_STEP_SUMMARY:-}" ] && printf '%s\n' "$*" | wmask >> "$GITHUB_STEP_SUMMARY"; return 0; }

# 클러스터 존재 — stdout "yes" | "no". 조회 오류(권한·네트워크)는 rc 2 — "없음" 으로 떨어뜨리지 않는다(B8-16).
win_cluster_exists() {
  local err
  if err=$(aws eks describe-cluster --region "$WIN_REGION" --name "$WIN_CLUSTER" \
             --query 'cluster.status' --output text 2>&1); then
    echo yes; return 0
  fi
  case "$err" in *ResourceNotFoundException*) echo no; return 0 ;; esac
  wlog "describe-cluster 오류: ${err:0:200}"; return 2
}

# 임대 읽기 — stdout "none" | "valid <expires>" | "expired <expires>". 조회 오류는 rc 2.
# 값이 형식에 안 맞으면 "expired 0" — 깨진 임대가 창을 영원히 열어 두지 않게(fail-safe = 끄는 쪽).
win_lease_state() {
  local out exp now="${WIN_NOW:-$(date +%s)}"
  if ! out=$(aws ssm get-parameter --region "$WIN_REGION" --name "$WIN_LEASE_PARAM" \
               --query 'Parameter.Value' --output text 2>&1); then
    case "$out" in *ParameterNotFound*) echo none; return 0 ;; esac
    wlog "임대 조회 오류: ${out:0:200}"; return 2
  fi
  exp=$(printf '%s' "$out" | sed -nE 's/.*expires=([0-9]+).*/\1/p')
  [ -n "$exp" ] || { wlog "임대 값 형식 이상 — 만료로 취급"; echo "expired 0"; return 0; }
  if [ "$exp" -gt "$now" ]; then echo "valid $exp"; else echo "expired $exp"; fi
}

# 임대 쓰기 — $1 = 시간(정수). 실패는 rc≠0 (on 은 여기서 멈춘다 — 임대 없이 켜면 watchdog 이 곧 끈다).
win_lease_put() {
  local hours="$1" now exp
  case "$hours" in ''|*[!0-9]*) wlog "시간이 정수가 아니다: $hours"; return 1 ;; esac
  now=$(date +%s); exp=$((now + hours * 3600))
  aws ssm put-parameter --region "$WIN_REGION" --name "$WIN_LEASE_PARAM" --type String --overwrite \
    --value "expires=$exp run=${GITHUB_RUN_ID:-local} started=$now" >/dev/null || return 1
  wlog "임대: $(TZ=Asia/Seoul date -r "$exp" '+%m-%d %H:%M KST' 2>/dev/null || TZ=Asia/Seoul date -d "@$exp" '+%m-%d %H:%M KST') 까지"
}

# 임대 즉시 만료 — off 가 **착수하자마자** 쓴다. off 가 중간에 실패해도 임대가 "유효" 로 남아 있으면 watchdog 이
#   *"창 중"* 으로 읽고 만료 시각까지 재시도하지 않는다(18:00 off 실패 + 임대 20:00 = 2시간 방치). 만료로 바꿔 두면
#   다음 시간 watchdog 이 바로 다시 off 를 돈다. 끝까지 성공하면 off-verify 가 지운다.
win_lease_expire() {
  aws ssm put-parameter --region "$WIN_REGION" --name "$WIN_LEASE_PARAM" --type String --overwrite \
    --value "expires=0 run=${GITHUB_RUN_ID:-local} off_started=$(date +%s)" >/dev/null
}

# 임대 지우기 — 없으면 성공. 그 밖의 오류는 rc≠0.
win_lease_delete() {
  local err
  err=$(aws ssm delete-parameter --region "$WIN_REGION" --name "$WIN_LEASE_PARAM" 2>&1) && return 0
  case "$err" in *ParameterNotFound*) return 0 ;; esac
  wlog "임대 삭제 오류: ${err:0:200}"; return 1
}

# ArgoCD Application 판정 — $1 = "<sync> <health> <operation phase> <condition types(,)>" (on.sh ⑤ 가 jq 로 만든다, 빈 값은 "-").
#   stdout ok | wait | fail.
#   🔑 **Healthy 만으로 ok 가 아니다** — 2026-10-22 첫 실행에서 health=Healthy 대기가 6초 만에 통과했다. 리소스가 아직 없는(또는
#      렌더가 실패해 비교 대상이 0개인) Application 도 Healthy 다. 그래서 세 값이 **동시에** 맞아야 ok.
#   fail 은 *"기다려도 안 풀린다"* 쪽만: operation Failed/Error · ComparisonError(렌더 실패)·InvalidSpecError·SyncError.
#      ⚠️ ComparisonError 는 repo 첫 fetch 중 잠깐 뜰 수 있다 — 호출자가 **3분 연속**일 때만 멈춘다(on.sh ⑤).
win_app_verdict() {
  local sync health phase conds
  read -r sync health phase conds <<<"$1"
  case "$phase" in Failed|Error) echo fail; return 0 ;; esac
  case ",${conds:-}," in *,ComparisonError,*|*,InvalidSpecError,*|*,SyncError,*) echo fail; return 0 ;; esac
  if [ "$sync" = Synced ] && [ "$health" = Healthy ] && [ "$phase" = Succeeded ]; then echo ok; else echo wait; fi
}

# 리전의 로드밸런서 수 — 이 계정엔 **영속 LB 가 없다**(SOP §9: 0 이 합격). 오류는 rc≠0.
win_lb_count() {
  aws elbv2 describe-load-balancers --region "$WIN_REGION" --query 'length(LoadBalancers)' --output text
}
