#!/usr/bin/env bash
# off 를 실제로 돌지 정한다 — 📌D-016 8-B. 사용: decide-off.sh <force|watchdog>
# stdout 마지막 줄: "act" | "skip". 조회 오류는 rc 2(워크플로가 빨개진다 = 메일).
#
#   force    — 18:00 KST off(EventBridge dispatch, B4-21) · 수동 off. **언제나 act**(Q-3: 끄기는 자동 + 수동 언제든). 꺼져 있으면 destroy 가 no-op 이다.
#   watchdog — 매시(EventBridge :17 · GitHub cron :47 보조). CI 클러스터의 dead man's switch(B4-1·B4-5):
#
#     클러스터 \ 임대 │ 유효        │ 만료·없음
#     ───────────────┼─────────────┼──────────────────────────────
#     있음           │ skip(창 중) │ act  ← on 이 죽었거나 off 가 실패했다
#     없음           │ skip        │ 임대가 남아 있으면 act(정리) · 없으면 skip
#
#   + 클러스터 없음에서 skip 할 때 **LB 가 남아 있으면 rc 1** — 클러스터 없는 ALB 는 고아다(월 ≈ $24).
#     매시간 이 검사가 돌기 때문에, ALB 고아는 길어야 한 시간 안에 메일이 된다.
#
# ⚠️ watchdog 은 **클러스터를 누가 켰는지 묻지 않는다** — 임대 없는 클러스터는 로컬 학습 세션이어도 끈다.
#    Q-1(2026-10-16)로 이 클러스터는 prod 전용이고 간헐 학습 세션은 끝났다. 로컬에서 켜야 하면
#    이 워크플로를 disable 하거나 임대를 직접 쓴다(scripts/prod-window/README 참조).
set -uo pipefail
. "$(dirname "$0")/common.sh"
MODE="${1:?force|watchdog}"

# force 는 조회하지 않고 act — 일시 오류 한 번이 끄기를 막지 않게(QA 8-B F-1). 꺼져 있으면 destroy 가 no-op 이다
if [ "$MODE" = force ]; then
  wlog "force → act"; echo act; exit 0
fi
C=$(win_cluster_exists) || exit 2
[ "$MODE" = watchdog ] || { wlog "알 수 없는 모드: $MODE"; exit 2; }

L=$(win_lease_state) || exit 2
wlog "watchdog — 클러스터 $C · 임대 $L"
case "$C:$L" in
  yes:valid*)   echo skip ;;
  yes:*)        echo act ;;
  no:none)
    N=$(win_lb_count) || { wlog "LB 조회 실패"; exit 2; }
    if [ "$N" != "0" ]; then
      wlog "🔴 클러스터가 없는데 로드밸런서 ${N}개 — 고아 ALB(월 ≈ \$24/대). SOP §8 ② 수동 삭제 명령 참조"
      echo skip; exit 1
    fi
    echo skip ;;
  no:valid*)    echo skip ;;   # on 이 apply 전에 실패한 직후 등 — 만료되면 다음 시간에 정리된다
  no:expired*)  echo act ;;    # 임대만 남았다 — destroy(no-op)·검증·임대 삭제
  *)            wlog "판정 불가: $C:$L"; exit 2 ;;
esac
