#!/usr/bin/env bash
# EKS 클러스터 소유 판정 — "이 로컬 세션 마커가 만든 클러스터인가" (📌D-016 순서표 4번, 2026-10-20).
# 리퍼(eks-reaper.sh)·하트비트 리마인더·session-status 가 source 한다. 단독 실행하지 않는다.
#
# 🔴 왜 필요한가 — 로컬 리퍼의 유일한 조치는 `tofu destroy` 다. D-016 이후 같은 이름·같은 state 의
#    클러스터를 **CI 가 평일 창 동안 prod 로** 켠다(Q-1). 그런데 로컬 마커는 **아무 레이어의
#    `tofu apply` 문자열**에나 반응하고(N-4b — 1-network DB 작업), 따옴표 안 오탐도 의도적으로
#    받아들였다(eks-session-marker.sh 「알려진 오탐」). 종전엔 *"클러스터가 없으면 리마인더가 마커를
#    자가 삭제한다"* 가 그 오탐을 무해하게 만드는 근거였는데, **창 동안엔 클러스터가 있으니 그 근거가
#    무너진다**(B4-6). 결과: 오탐 마커 하나 + 로컬 턴 2h 공백 → **리퍼가 prod 를 destroy**.
#    → 마커 정규식을 조이지 않는다(L-22 나선). 대신 **destroy 대상이 이 마커의 apply 로 생겼는지**를 본다.
#
# 규칙: 클러스터 createdAt 이 마커의 첫 apply 시각(applied_at) 이후 [−SKEW, +WINDOW] 안이면 mine.
#   · 그보다 **먼저** 생긴 클러스터 = CI 창·다른 기기·기존 클러스터 → foreign. 로컬은 손대지 않는다.
#   · 마커보다 **한참 뒤** 생긴 클러스터 = 남은 마커 뒤에 CI 가 켠 것 → foreign.
#   · 판정 재료가 없거나 조회 실패 → unknown. **destroy 하지 않는 쪽**으로 기운다(호출부 책임).
#   ⚠️ 실패 방향을 고른 이유: foreign/unknown 오판의 대가는 학습 클러스터 과금(**8번 이후** off cron 이
#      2차 백스톱 — 그 전엔 없다), mine 오판의 대가는 **prod 장애**다. 둘의 무게가 다르다.
#   🔴 남은 mine 오판 경로 (QA F-2, 원장 등재 → 8번): 마커가 생긴 뒤 Stop 훅이 한 번도 못 돌고(턴 도중 종료·
#      노트북 닫힘) **+1h 안에 CI 가 클러스터를 켜면** mine 이 된다. 8번 전엔 CI on 이 없어 성립하지 않는다.
#      8번이 on 잡에 임대(B4-5)를 남기면 "임대 있음 = foreign" 으로 닫는다. WINDOW 는 실측 전 보수값이다.
#   ⚠️ 클러스터가 여럿이면 하나라도 foreign 이면 foreign(QA F-6) — 무관한 클러스터가 같은 리전에 있으면
#      내 클러스터의 리퍼도 멈춘다. 의도된 fail-safe(어느 것을 지울지 고를 수 없다).
#   ⚠️ applied_at 은 마커 훅이 **첫 apply 시각을 보존**하므로 성립한다(같은 세션의 두 번째 apply 가
#      덮어쓰면 자기 클러스터가 foreign 이 된다 — eks-session-marker.sh 참조).

EKS_OWN_SKEW="${EKS_OWN_SKEW:-30}"       # 초. 로컬 시계와 AWS 시계 차
EKS_OWN_WINDOW="${EKS_OWN_WINDOW:-3600}" # 초. apply 시작 → CreateCluster 호출까지의 상한. 🟡 미실측 보수값 —
                                         # 승인 프롬프트 대기도 여기 들어간다(마커는 PreToolUse 에서 생긴다)

# ISO8601(aws CLI 기본 출력, 예 2026-09-13T03:05:19.561000+00:00) → epoch. 실패 시 빈 출력·rc 1.
# GNU date 먼저, 안 되면 BSD date(macOS — `-d` 가 없다).
eks_iso_to_epoch() {
  local ts="$1" norm
  [ -n "$ts" ] || return 1
  date -d "$ts" +%s 2>/dev/null && return 0
  norm=$(printf '%s' "$ts" | sed -E -e 's/\.[0-9]+//' -e 's/Z$/+0000/' -e 's/([+-][0-9]{2}):([0-9]{2})$/\1\2/')
  date -j -f '%Y-%m-%dT%H:%M:%S%z' "$norm" +%s 2>/dev/null
}

# eks_cluster_owner <cluster> <applied_at(epoch)> <region>
# stdout: "mine <created_epoch>" | "foreign <created_epoch>" | "unknown <사유>"
eks_cluster_owner() {
  local name="$1" applied="$2" region="$3" raw created
  case "$applied" in ''|*[!0-9]*) echo "unknown 마커에 applied_at 없음"; return 0 ;; esac
  raw=$(aws --cli-connect-timeout 3 --cli-read-timeout 10 eks describe-cluster --region "$region" \
          --name "$name" --query 'cluster.createdAt' --output text 2>/dev/null) || {
    echo "unknown describe-cluster 실패"; return 0; }
  created=$(eks_iso_to_epoch "$raw") || { echo "unknown createdAt 해석 실패($raw)"; return 0; }
  if [ "$created" -ge $((applied - EKS_OWN_SKEW)) ] && [ "$created" -le $((applied + EKS_OWN_WINDOW)) ]; then
    echo "mine $created"
  else
    echo "foreign $created"
  fi
}

# eks_clusters_owner "<공백 구분 클러스터들>" <applied_at> <region>
# 하나라도 foreign → foreign, 하나라도 unknown → unknown(foreign 우선), 전부 mine → mine.
# stdout 첫 단어가 판정, 나머지는 사람이 읽을 근거.
eks_clusters_owner() {
  local verdict="mine" detail="" c v
  # 빈 목록을 "전부 mine" 으로 떨어뜨리지 않는다(공허한 전칭 — 호출부가 비었는지 먼저 보지만 이중으로).
  [ -n "${1// /}" ] || { echo "unknown 클러스터 목록 비어 있음"; return 0; }
  for c in $1; do
    v=$(eks_cluster_owner "$c" "$2" "$3")
    detail="$detail $c:${v}"
    case "$v" in
      foreign*) verdict="foreign" ;;
      unknown*) [ "$verdict" = "foreign" ] || verdict="unknown" ;;
    esac
  done
  echo "$verdict$detail"
}
