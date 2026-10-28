#!/usr/bin/env bash
# off 의 **맨 앞** 단계 — 📌D-016 8-C C-2. 임대 만료 표시·off-pre·destroy **전에** 돈다. rc 0 = 다음 단계로, rc≠0 = destroy 하지 않는다.
#
# 순서가 전부다 (계획서 「C-1 QA 에서 C-2 로 넘긴 것」 ①~③):
#   ① KV → `{"target":"fly"}` — 사용자 트래픽을 먼저 돌린다
#   ② 직전 값이 eks(또는 읽지 못함)였으면 **배수 대기 ≥ 210s**(기본 240s) = KV 전파 ≥ 60s + 진행 중 요청(AI read-timeout 150s).
#      🔴 근거 없이 줄이지 마라. `until` 을 즉시 만료로 덮어써도 stale 엣지엔 같은 전파라 대기를 대체하지 못한다.
#   ③ `eks.quest` 레코드 삭제 — 실패해도 rc 0(경고). Worker 는 KV 가 fly 라 이 이름을 안 쓴다. 다음 on 이 덮어쓴다.
#   ④ 그 뒤에야 워크플로가 Ingress·ALB 를 지운다(off-pre) — 대기 전에 지우면 아직 eks 를 보는 엣지의 요청이 502 다(자동 전환 없음).
#
# 🔑 Cloudflare 에 닿지 못할 때 (토큰 만료 2027-01-24·API 장애)
#   KV 가 eks 일 **가능성**이 있으면 = 임대를 본다. 불변식 *"KV until ≤ 임대 만료"*(switch-on.sh) 때문에
#     · 임대 유효 + 표식 `traffic=eks` → Worker 가 아직 EKS 로 보내고 있을 수 있다 → **rc 1, destroy 하지 않는다**. 임대 만료 뒤 watchdog 이 다시 돈다.
#     · 임대 유효 + 표식 없음(traffic=fly 창 · on 실패 직후) → KV 는 eks 가 된 적이 없다 → rc 0. on 실패 = 즉시 끄기(8-B)를 지킨다(U-1).
#     · 임대 만료·없음 → Worker 는 이미 스스로 Fly 다 → 만료 뒤 WAIT 가 안 지났으면 채워 기다리고 rc 0(비용을 무한정 쓰지 않게).
#     · 임대 조회 오류 → rc 1(판정 불가 = 안 끈다. watchdog 이 재시도).
#   KV 를 **읽었고 eks 가 아니면** 쓰기 실패여도 진행한다 — 트래픽이 EKS 에 있을 수 없다(C-4 전 모든 창이 여기).
#   ⇒ 비용 상한 = 임대 만료 + 1h(watchdog). 사용자 영향 = 0.
set -uo pipefail
HERE="$(dirname "$0")"
. "$HERE/common.sh"
. "$HERE/cf.sh"
WAIT="${WIN_DRAIN_WAIT:-240}"

NOW="${WIN_NOW:-$(date +%s)}"
fallback() {  # $1 = 이유
  local L exp since
  L=$(win_lease_state) || { wlog "🔴 $1 · 임대 조회도 실패 — destroy 하지 않는다"; wsummary "- 🔴 $1 · 임대 조회 실패 → destroy 보류"; exit 1; }
  case "$L" in
    valid*)
      # 표식이 없으면 이 임대 동안 KV 는 eks 가 된 적이 없다(eks 를 쓰는 건 switch-on 뿐, 표식이 먼저) — 막을 이유가 없다(U-1)
      if ! win_lease_has_eks; then
        wlog "⚠️ $1 — 임대 유효지만 트래픽 표식 없음(이 창은 traffic=fly) → 트래픽은 Fly. 진행한다"
        wsummary "- ⚠️ $1 — traffic=fly 창이라 진행. \`$CF_RECORD\` 레코드 잔존 가능(무해 — 다음 on 이 덮어쓴다)"
        exit 0
      fi
      wlog "🔴 $1 — 임대 유효 + traffic=eks: Worker 가 아직 EKS 로 보낸다. destroy 하지 않는다(임대 만료 뒤 watchdog 재시도)"
      wsummary "- 🔴 $1 — traffic=eks 창이라 destroy 보류. 임대 만료(= until) 뒤 Worker 는 스스로 Fly, watchdog 이 끈다(비용 상한 = 임대 + 1h)"
      exit 1 ;;
    *)
      # until ≤ 임대 만료 — 만료 직후라면 진행 중 요청(AI 150s)이 아직 EKS 에 있을 수 있다. 만료 뒤 WAIT 가 지나기를 기다린다
      case "$L" in expired*) exp="${L#expired }" ;; *) exp=0 ;; esac
      since=$(( NOW - exp ))
      if [ "$exp" -gt 0 ] && [ "$since" -lt "$WAIT" ]; then
        wlog "임대 만료 ${since}s 전 — 배수를 위해 $((WAIT - since))s 더 기다린다"; sleep $((WAIT - since))
      fi
      wlog "⚠️ $1 — 임대 $L: until ≤ 임대 만료라 Worker 는 이미 Fly. 진행한다(eks.quest 레코드가 남을 수 있다)"
      wsummary "- ⚠️ $1 — 임대 만료라 진행. \`$CF_RECORD\` 레코드 잔존 가능(무해 — 다음 on 이 덮어쓴다)"
      exit 0 ;;
  esac
}

cf_init || fallback "Cloudflare 초기화 실패"

# ── ① KV → fly ───────────────────────────────────────────────────────────
PREV=""; MAYBE_EKS=1
if PREV=$(cf_kv_get); then
  [ "$(cf_kv_target "$PREV")" = eks ] || MAYBE_EKS=0
  wlog "KV 직전 값: $([ "$PREV" = none ] && echo 없음 || printf '%s' "$PREV" | cut -c1-120)"
else
  wlog "KV 읽기 실패 — eks 일 수 있다고 본다"
fi
if cf_kv_put '{"target":"fly"}'; then
  wsummary "- 🚦 트래픽: **Fly** (KV api-target = fly)"
else
  [ "$MAYBE_EKS" = 0 ] || fallback "KV fly 쓰기 실패"
  wlog "⚠️ KV 쓰기 실패지만 직전 값이 eks 가 아니었다 — 트래픽은 이미 Fly. 진행"
  # 초록으로 끝나도 쓰기 경로가 죽어 있다는 사실은 남긴다(QA F-3) — C-3 완료 기준이 이 줄을 본다
  wsummary "- ⚠️ 트래픽: Fly(직전 값 $([ "$PREV" = none ] && echo 없음 || echo 'eks 아님')) — **KV 쓰기 실패**. 다음 traffic=eks 전에 토큰·권한 확인"
fi

# ── ② 배수 대기 ──────────────────────────────────────────────────────────
if [ "$MAYBE_EKS" = 1 ]; then
  wlog "배수 대기 ${WAIT}s — KV 전파 + 진행 중 요청(AI 150s)"
  sleep "$WAIT"
  wsummary "- ⏳ 배수 대기 ${WAIT}s (직전 값 eks 또는 미확인)"
fi

# ── ③ 레코드 삭제 ────────────────────────────────────────────────────────
if cf_dns_delete "$CF_RECORD"; then
  wlog "✅ $CF_RECORD 삭제(또는 없음)"
else
  wlog "⚠️ $CF_RECORD 삭제 실패 — 계속한다(Worker 는 KV=fly 라 이 이름을 안 쓴다)"
  wsummary "- ⚠️ \`$CF_RECORD\` 삭제 실패 — 무해, 다음 on 이 덮어쓴다"
fi
exit 0
