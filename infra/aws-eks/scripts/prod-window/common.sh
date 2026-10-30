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
  local hours="$1" now exp mark="" cur old
  case "$hours" in ''|*[!0-9]*) wlog "시간이 정수가 아니다: $hours"; return 1 ;; esac
  now=$(date +%s); exp=$((now + hours * 3600))
  # 창 중 재실행 — 이미 트래픽이 EKS 면 표식을 이어 간다(지우면 switch-off 폴백이 "eks 아님" 으로 오판한다, 8-C C-2)
  #   ① 조회 **오류**는 표식 있음으로 본다(QA F-1a) — 표식이 남는 쪽의 비용은 Cloudflare 불통 때 destroy 보류(상한 임대+1h)뿐이다
  #   ② 표식이 있으면 expires 를 **줄이지 않는다**(F-1b) — KV until 은 이전 임대 만료라, 더 짧은 hours 로 재 on 하면
  #      불변식 "until ≤ 임대 만료" 가 깨진다(switch-on 이 KV 를 다시 쓰기 전에 죽는 경우)
  if cur=$(aws ssm get-parameter --region "$WIN_REGION" --name "$WIN_LEASE_PARAM" \
             --query 'Parameter.Value' --output text 2>&1); then
    case " $cur " in *" traffic=eks "*)
      mark=" traffic=eks"
      old=$(printf '%s' "$cur" | sed -nE 's/.*expires=([0-9]+).*/\1/p')
      if [ -n "$old" ] && [ "$old" -gt "$exp" ]; then wlog "표식 있는 임대 — 만료를 앞당기지 않는다"; exp="$old"; fi ;;
    esac
  else
    case "$cur" in *ParameterNotFound*) ;; *) wlog "임대 조회 오류 — 트래픽 표식을 보수적으로 남긴다"; mark=" traffic=eks" ;; esac
  fi
  aws ssm put-parameter --region "$WIN_REGION" --name "$WIN_LEASE_PARAM" --type String --overwrite \
    --value "expires=$exp run=${GITHUB_RUN_ID:-local} started=$now$mark" >/dev/null || return 1
  wlog "임대: $(TZ=Asia/Seoul date -r "$exp" '+%m-%d %H:%M KST' 2>/dev/null || TZ=Asia/Seoul date -d "@$exp" '+%m-%d %H:%M KST') 까지"
}

# 임대 즉시 만료 — off 가 **착수하자마자** 쓴다. off 가 중간에 실패해도 임대가 "유효" 로 남아 있으면 watchdog 이
#   *"창 중"* 으로 읽고 만료 시각까지 재시도하지 않는다(18:00 off 실패 + 임대 20:00 = 2시간 방치). 만료로 바꿔 두면
#   다음 시간 watchdog 이 바로 다시 off 를 돈다. 끝까지 성공하면 off-verify 가 지운다.
win_lease_expire() {
  aws ssm put-parameter --region "$WIN_REGION" --name "$WIN_LEASE_PARAM" --type String --overwrite \
    --value "expires=0 run=${GITHUB_RUN_ID:-local} off_started=$(date +%s)" >/dev/null
}

# 🔑 트래픽 표식 (📌D-016 8-C C-2) — switch-on.sh 가 KV 를 eks 로 쓰기 **전에** 임대 값 끝에 ` traffic=eks` 를 붙인다.
#   switch-off.sh 가 Cloudflare 에 닿지 못할 때 *"이 임대 동안 트래픽이 EKS 였을 수 있나"* 를 이것으로 판단한다(Blindspot U-1):
#   표식이 없으면 KV 는 eks 일 수 없다(eks 를 쓰는 건 switch-on 뿐) → destroy 를 막을 이유가 없다.
#   win_lease_expire 는 표식을 지운다 — 만료된 임대는 표식과 무관하게 진행하므로(until ≤ 임대 만료) 잃어도 된다.
# 표식 있음 = rc 0 · 없음·임대 없음·조회 오류 = rc 1 (호출자가 오류를 따로 구분해야 하면 win_lease_state 를 먼저 부른다)
win_lease_has_eks() {
  local out
  out=$(aws ssm get-parameter --region "$WIN_REGION" --name "$WIN_LEASE_PARAM" \
          --query 'Parameter.Value' --output text 2>/dev/null) || return 1
  case " $out " in *" traffic=eks "*) return 0 ;; *) return 1 ;; esac
}
# 표식 붙이기 — 현재 임대 값을 읽어 끝에 붙인다. 임대가 없거나 실패하면 rc≠0(그러면 KV 를 쓰지 않는다).
win_lease_mark_eks() {
  local out
  out=$(aws ssm get-parameter --region "$WIN_REGION" --name "$WIN_LEASE_PARAM" \
          --query 'Parameter.Value' --output text 2>/dev/null) || return 1
  case " $out " in *" traffic=eks "*) return 0 ;; esac
  aws ssm put-parameter --region "$WIN_REGION" --name "$WIN_LEASE_PARAM" --type String --overwrite \
    --value "$out traffic=eks" >/dev/null
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

# 로그인 키 판정 (📌D-016 B7-16 + JWT 통일, 2026-10-28) — $1 = core-api-runtime 키 이름(쉼표) · $2 = 그 Secret 의 GITHUB_CLIENT_ID 값 ·
#   $3 = FE 번들에 박히는 client id(`fe-cd.yml` 의 VITE_GITHUB_CLIENT_ID). stdout ok | "<사유>" (rc 1).
#   🔑 C-4 1차(10-28)는 **파드가 뜨고 스모크(200/403)도 통과했는데 로그인만 죽었다** — 키 개수 > 0 만 보던 판정이 공허했다.
#   FE 가 GitHub 에 보내는 client id 와 EKS 가 토큰 교환에 쓰는 client id 가 다르면 code 교환이 실패하므로 값까지 맞춘다
#   (client id 는 공개 값 — 비교·로그 무해. secret 은 **이름만** 본다).
# FE 번들에 박히는 client id — `fe-cd.yml` 의 VITE_GITHUB_CLIENT_ID (on.sh · 워크플로 사전 점검 · 테스트가 **같은 함수**를 쓴다, QA F-3)
win_fe_client_id() {
  sed -nE 's/^[[:space:]]*VITE_GITHUB_CLIENT_ID:[[:space:]]*([A-Za-z0-9._-]+).*/\1/p' "$1" | head -1
}

win_login_keys_verdict() {
  local keys=",$1," cid="$2" fe="$3" k
  for k in GITHUB_CLIENT_ID GITHUB_CLIENT_SECRET JWT_SECRET; do   # JWT_SECRET — JWT 통일(10-28): Fly 와 같은 키, 없으면 기동 실패
    case "$keys" in *",$k,"*) ;; *) echo "로그인 키 $k 없음 — SSM /devquest/prod/app/$k"; return 1 ;; esac
  done
  [ -n "$fe" ] || { echo "fe-cd.yml 에서 VITE_GITHUB_CLIENT_ID 를 못 읽었다"; return 1; }
  [ "$cid" = "$fe" ] || { echo "client id 불일치 — EKS '$cid' ≠ FE '$fe'"; return 1; }
  echo ok
}

# 리전의 로드밸런서 수 — 이 계정엔 **영속 LB 가 없다**(SOP §9: 0 이 합격). 오류는 rc≠0.
win_lb_count() {
  aws elbv2 describe-load-balancers --region "$WIN_REGION" --query 'length(LoadBalancers)' --output text
}

# 실행·대기 중인 워크플로 run 수 — 📌D-016 9번(B8-20). $1 = owner/repo · $2 = 워크플로 파일 · $3 = jq 필터(배열 → 수).
#   stdout 정수. 조회 오류는 rc 2 — "0건" 으로 떨어뜨리지 않는다(B8-16 과 같은 이유: 모르면 막는다).
#   상태 5종은 jwt-rotate·edge-auth-rotate 의 be-cd 가드와 같다.
win_active_runs() {
  local s n total=0
  for s in in_progress queued waiting pending requested; do
    n=$(gh run list -R "$1" --workflow "$2" --status "$s" --limit 50 --json displayTitle --jq "$3" 2>/dev/null) \
      || { wlog "$2 실행 조회 오류(status=$s)"; return 2; }
    [[ "$n" =~ ^[0-9]+$ ]] || { wlog "$2 실행 조회 결과가 정수가 아니다: ${n:0:80}"; return 2; }
    total=$((total + n))
  done
  echo "$total"
}

# eks-window 의 on 실행만 고르는 jq — run-name 이 `EKS Window / <action>` 이다(eks-window.yml `run-name`).
#   watchdog(매시 :17·:47, 클러스터 없으면 1분 안에 끝남)까지 세면 be-cd 가 시간당 몇 % 확률로 이유 없이 막힌다.
#   off·watchdog 이 **클러스터가 있을 때** 도는 동안은 아래 클러스터 판정이 막는다.
WIN_ON_RUN_JQ='[.[] | select(.displayTitle | endswith("/ on"))] | length'
