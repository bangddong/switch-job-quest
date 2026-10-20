#!/usr/bin/env bash
# PreToolUse(Bash) — `tofu apply` 감지 시 EKS 세션 활성 마커를 남긴다.
# 이 마커는 "AWS에 과금 리소스가 떠 있을 수 있음"을 리퍼·리마인더에 알리는 값싼 플래그다.
# 실제 클러스터 존재 여부는 항상 AWS로 확인한다(마커는 최적화용, 진실의 원천 아님).
#
# 절대 apply를 막지 않는다(항상 exit 0). 마커 생성만.

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null || echo "")

# `tofu apply`만 대상 (plan/destroy 제외). 명령 위치로 앵커링해 문자열 오탐 방지
# (예: git commit -m "tofu apply 관련" 은 매칭 안 됨 — 명령 시작/구분자 뒤만 인정).
#
# 🔴 **래퍼를 허용한다 (2026-09-04, 유료 세션에서 실제로 뚫렸다).**
#   종전 패턴은 구분자 뒤에 `tofu` 가 **바로** 와야 했다. 그래서
#     … && nohup tofu apply -auto-approve plan.tfplan > log 2>&1 &
#   가 매칭되지 않아 **마커 없이 apply 가 돌았다.** 실패 방향이 나쁘다 —
#   apply 는 정상 진행하고 경고도 없이 **dead man's switch 만 조용히 꺼진다**
#   (`eks-reaper.sh:55` 가 `[ -f "$MARKER" ] || exit 0` 이라 AWS 조회조차 안 한다).
#   우회를 일으키는 접두사는 전부 흔한 관용구다: nohup · time · timeout N · env X=1 · stdbuf -oL.
#   종전 회귀 테스트는 `cd x && tofu apply` 만 확인했고 **래퍼는 한 건도 없었다**(그래서 아래 케이스 추가).
#
#   허용 토큰(반복 가능): 래퍼 명령 · `timeout <N>` · `VAR=값` · `-옵션`
#   ⚠️ 앵커(`^` 또는 구분자)는 **그대로 유지**한다 — 이걸 풀면 문자열 오탐이 대폭 돌아온다.
#
# 🟡 **알려진 오탐 — 의도적으로 받아들인다 (2026-09-04).**
#   래퍼를 허용한 대가로, **따옴표 안의** `… && nohup tofu apply …` 도 매칭된다.
#   실제로 이 수정을 커밋하는 `git commit -m "… && nohup tofu apply …"` 가 마커를 만들었다.
#   (종전 앵커는 구분자 뒤에 `tofu` 가 바로 와야 해서 이 문자열을 안 물었다 —
#    즉 **내 수정이 새 오탐을 열었다.** 원장 L-22 가 기록한 *"조일 때마다 새 우회가 열린다"* 그대로다.)
#
#   **그래도 받아들이는 이유는 실패 방향이 반대이기 때문이다:**
#     · 오탐(마커가 더 생김)  = 과잉 감시. `eks-heartbeat-reminder.sh:34` 가 과금 리소스가
#       하나도 없으면 마커를 **자가 삭제**한다 → 5분 스로틀 안에 저절로 사라진다.
#     · 미탐(마커가 안 생김) = **dead man's switch 가 꺼진다.** 오늘 실제로 발생했고,
#       그대로 사라졌으면 클러스터가 계속 과금됐다.
#   따옴표 문맥을 정규식으로 가르려 하지 않는다 — 그 길이 L-22 의 4라운드 나선이다.
WRAP='((nohup|time|stdbuf|command|exec|env)[[:space:]]+|timeout[[:space:]]+[0-9]+[smhd]?[[:space:]]+|[A-Za-z_][A-Za-z0-9_]*=[^[:space:]]*[[:space:]]+|-[^[:space:]]+[[:space:]]+)*'
echo "$COMMAND" | grep -qE "(^|[;&|(]|&&|\|\|)[[:space:]]*${WRAP}tofu[[:space:]]+apply" || exit 0

ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
DIR="$ROOT/.claude/eks-session"
mkdir -p "$DIR" 2>/dev/null

# 마커에 리퍼가 tofu destroy를 돌릴 위치와 시작 시각을 기록 (머신 독립)
#
# 🔴 applied_at 은 **세션의 첫 apply 시각을 보존**한다 (📌D-016 4번, 2026-10-20).
#   리퍼는 이제 *"클러스터 createdAt 이 applied_at 직후인가"* 로 소유를 판정한다(lib/eks-cluster-owner.sh).
#   같은 세션의 두 번째 apply 가 시각을 덮어쓰면 **자기가 만든 클러스터가 foreign 이 되어** dead man's
#   switch 가 조용히 꺼진다. 마커가 이미 있으면 시각만 그대로 두고 나머지는 다시 쓴다.
#   ⚠️ 단 **하트비트가 살아 있을 때만** 보존한다. 지난 세션이 정리되지 못하고 남긴 마커(맥 잠듦 등)의
#   옛 시각을 이어받으면, 이번 세션이 만든 클러스터가 창(+1h) 밖이라 foreign 이 된다 — 같은 결말.
#   하트비트 TTL 은 리퍼와 같은 값(EKS_REAPER_TTL, 기본 7200)이다: 그보다 오래 조용했으면 다른 세션이다.
PREV_AT=$(grep '^applied_at=' "$DIR/active" 2>/dev/null | cut -d= -f2-)
PREV_AT_H=$(grep '^applied_at_h=' "$DIR/active" 2>/dev/null | cut -d= -f2-)
# GNU 먼저 — GNU `stat -f` 는 파일시스템 모드라 실패하면서도 stdout 에 쓰레기를 남긴다. BSD `stat -c` 는 조용히 실패.
HB_M=$(stat -c %Y "$DIR/heartbeat" 2>/dev/null || stat -f %m "$DIR/heartbeat" 2>/dev/null || echo 0)
case "$PREV_AT" in ''|*[!0-9]*) PREV_AT=""; PREV_AT_H="" ;; esac
case "$HB_M" in ''|*[!0-9]*) HB_M=0 ;; esac
[ $(( $(date +%s) - ${HB_M:-0} )) -lt "${EKS_REAPER_TTL:-7200}" ] || { PREV_AT=""; PREV_AT_H=""; }
#   ⚠️ 그리고 **지금 클러스터가 떠 있을 때만** 보존한다 (QA F-1, 2026-10-20). 보존의 목적은 *"이미 떠 있는 내
#   클러스터"* 를 지키는 것이다 — 클러스터가 없으면 이번 apply 가 새로 만들 것이므로 지금 시각이 맞다.
#   하트비트만 보면 **한 턴 안의 destroy → 재apply**(Stop 훅이 그 사이에 안 돈다)에서 옛 시각이 남아
#   새 클러스터가 foreign 이 되고 감시가 조용히 꺼진다. 조회 실패(오프라인 등)면 하트비트 규칙만 쓴다.
if [ -n "$PREV_AT" ]; then
  LIVE_C=$(aws --cli-connect-timeout 3 --cli-read-timeout 5 --region "${EKS_REGION:-ap-northeast-2}" \
             eks list-clusters --query 'clusters' --output text 2>/dev/null) && {
    case "$LIVE_C" in ''|None) PREV_AT=""; PREV_AT_H="" ;; esac
  }
fi
{
  echo "repo_root=$ROOT"
  echo "cluster_dir=$ROOT/infra/aws-eks/2-cluster"
  echo "applied_at=${PREV_AT:-$(date +%s)}"
  echo "applied_at_h=${PREV_AT_H:-$(date '+%Y-%m-%d %H:%M:%S %Z')}"
} > "$DIR/active" 2>/dev/null

touch "$DIR/heartbeat" 2>/dev/null
rm -f "$DIR/lastcheck" "$DIR/state.cache" 2>/dev/null  # 새 세션 — 캐시 초기화

echo "🟢 EKS 세션 마커 생성 — 과금 추적 시작. destroy 전까지 리퍼가 감시합니다." >&2

# ── 영속 리소스 배너 ────────────────────────────────────────────────
# 왜: destroy로 사라지지 않는 리소스는 `kubectl`에도 `tofu state`에도 안 나온다.
#     보이는 층과 과금되는 층이 분리돼 있어 "까먹는 것"이 부주의가 아니라 기본값이다.
#     → 세션을 여는 순간, 묻지 않아도 눈에 들어오게 한다.
#
# 규칙 3가지:
#   ① 절대 apply를 막지 않는다 — 조회 실패·자격증명 없음·오프라인 전부 조용히 넘어간다.
#   ② 타임아웃을 짧게 — 훅이 느리면 사람이 훅을 끈다.
#   ③ 기대값은 여기 하드코딩하지 않는다 — 단일 출처는 PERSISTENT-RESOURCES.md다.
REGION="${EKS_REGION:-ap-northeast-2}"
AWSQ=(aws --cli-connect-timeout 3 --cli-read-timeout 5 --region "$REGION")

VOLS=$("${AWSQ[@]}" ec2 describe-volumes \
  --filters Name=tag:Persistent,Values=true \
  --query 'Volumes[].[VolumeId,Size,AvailabilityZone,State]' \
  --output text 2>/dev/null)
AWS_RC=$?   # ⚠️ 반드시 대입 **직후**에 잡는다. if 안에서 $?를 읽으면 aws가 아니라
            #    바로 앞 test의 결과가 잡혀, "조회 실패"와 "볼륨 0개"가 구분되지 않는다.

if [ "$AWS_RC" -ne 0 ]; then
  : # 조회 실패(오프라인·자격증명 없음·권한 없음) — 조용히 넘어간다. apply를 막지 않는다.
elif [ -n "$VOLS" ]; then
  # EBS gp3 $0.0912/GB-Mo (ap-northeast-2 실측, Pricing API)
  TOTAL_GB=$(echo "$VOLS" | awk '{s+=$2} END{print s+0}')
  MONTHLY=$(awk -v g="$TOTAL_GB" 'BEGIN{printf "%.2f", g*0.0912}')
  {
    echo "━━ 영속 리소스 — destroy해도 사라지지 않습니다 ━━"
    echo "$VOLS" | awk '{printf "   EBS  %-22s %3s GiB  %-18s %s\n", $1, $2, $3, $4}'
    echo "   └ 합계 ${TOTAL_GB} GiB ≈ \$${MONTHLY}/월  (+ ECR 등은 원장 참조)"
    echo "   📖 infra/aws-eks/PERSISTENT-RESOURCES.md 와 대조하세요 — 개수가 다르면 즉시 확인."
  } >&2
else
  echo "   영속 EBS 볼륨 0개 — 이번 세션의 스토리지는 전부 destroy로 회수됩니다." >&2
fi

exit 0
