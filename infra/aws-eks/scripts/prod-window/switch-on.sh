#!/usr/bin/env bash
# on 의 마지막 단계 — 📌D-016 8-C C-2. 사용: switch-on.sh <fly|eks>. on.sh 가 성공한 **뒤**, 레포 루트에서 실행한다.
# 실패하면 rc≠0 → 워크플로가 같은 실행에서 off 를 돈다(off 는 맨 먼저 KV 를 fly 로 되돌린다 — switch-off.sh).
#
#   ① `eks.quest` CNAME → 이 창의 ALB(DNS only) — **트래픽과 무관**하다. 이 이름은 Worker 만 쓰고, Worker 는 KV 가 eks 일 때만 쓴다.
#   ② 공개 DNS(DoH)가 새 ALB 를 답하는지 · `https://eks.quest/…` 가 실제 이름으로 200 인지(on.sh ⑥ 은 `--resolve` 였다)
#   ③ **`eks` 일 때만** KV = `{"target":"eks","until":<임대 만료>}` → 공개 URL 응답 헤더 `x-devquest-backend: eks` 확인
#
# 🔑 `until` = **임대 만료 시각** — 불변식 *"KV until ≤ 임대 만료"*. 그래서 off 가 Cloudflare 에 닿지 못해도 임대 만료 뒤엔
#    Worker 가 스스로 Fly 로 돌아가 있다(switch-off.sh 폴백이 이 불변식에 기댄다). 임대를 쓰는 건 on 하나뿐이다.
# 🔴 트래픽 기본값은 fly — 사용자 결정 없이 prod 트래픽을 옮기지 않는다(C-4 = 첫 트래픽 창, 🔴 사용자 승인).
set -euo pipefail
TRAFFIC="${1:?fly|eks}"
case "$TRAFFIC" in fly|eks) ;; *) echo "[eks-window] traffic 은 fly|eks ($TRAFFIC)" >&2; exit 1 ;; esac
HERE="$(dirname "$0")"
. "$HERE/common.sh"
. "$HERE/cf.sh"
PUBLIC="${WIN_PUBLIC_URL:-https://quest.dhbang.co.kr}"
TRIES="${WIN_SWITCH_TRIES:-18}"; POLL="${WIN_POLL:-10}"   # 기본 3분 — 테스트가 줄인다

cf_init || { wlog "🔴 Cloudflare 초기화 실패 — eks.quest 를 못 만든다"; exit 1; }

# ── ① DNS ────────────────────────────────────────────────────────────────
HOST=$(kubectl get ingress devquest -n default -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
case "$HOST" in *.elb.amazonaws.com) ;; *) wlog "🔴 Ingress ALB 주소 형태 이상: '${HOST:0:80}'"; exit 1 ;; esac
cf_dns_upsert "$CF_RECORD" "$HOST" || exit 1
wsummary "- 🌐 \`$CF_RECORD\` → 이 창의 ALB (DNS only)"

# ── ② 실제 이름으로 닿는가 ────────────────────────────────────────────────
ok=0
for _ in $(seq 1 "$TRIES"); do cf_doh_cname_is "$CF_RECORD" "$HOST" && { ok=1; break; }; sleep "$POLL"; done
[ "$ok" = 1 ] || { wlog "🔴 공개 DNS 가 $CF_RECORD → ALB 를 답하지 않는다"; exit 1; }
code=000
for _ in $(seq 1 "$TRIES"); do
  code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 "https://$CF_RECORD/api/v1/daily-question" 2>/dev/null) || code=000
  [ "$code" = 200 ] && break; sleep "$POLL"
done
[ "$code" = 200 ] || { wlog "🔴 https://$CF_RECORD/api/v1/daily-question → $code"; exit 1; }
wlog "✅ $CF_RECORD 실제 이름으로 200"; wsummary "- ✅ \`https://$CF_RECORD/api/v1/daily-question\` → 200 (실제 DNS)"

if [ "$TRAFFIC" = fly ]; then
  # 창 중 traffic=fly 재실행 — 이전 실행이 eks 를 써 뒀으면 되돌린다(Blindspot U-5). 평소엔 키가 없어 읽기 1회로 끝난다.
  #   클러스터는 계속 켜 있으므로 배수 대기는 필요 없다(EKS 로 가던 요청도 정상 완료된다).
  if ! CUR=$(cf_kv_get) || [ "$(cf_kv_target "$CUR")" = eks ]; then
    cf_kv_put '{"target":"fly"}' || { wlog "🔴 KV 를 fly 로 되돌리지 못했다"; exit 1; }
    wlog "KV eks(또는 미확인) → fly 로 되돌렸다"
  fi
  wlog "traffic=fly — 사용자 트래픽은 Fly 그대로"
  wsummary "- 🚦 트래픽: **Fly 유지** (traffic=fly)"
  exit 0
fi

# ── ③ 트래픽 → EKS ───────────────────────────────────────────────────────
# 🔴 until 은 **SSM 임대의 expires** 에서 만든다 — 지금 + hours 로 다시 계산하면 apply·설치 ~20분만큼 임대보다 늦어져
#    불변식 "KV until ≤ 임대 만료" 가 깨진다(Blindspot U-2).
L=$(win_lease_state) || { wlog "🔴 임대 조회 실패 — until 을 못 정한다"; exit 1; }
case "$L" in valid*) ;; *) wlog "🔴 임대가 유효하지 않다($L) — 트래픽을 옮기지 않는다"; exit 1 ;; esac
EXP="${L#valid }"; UNTIL=$(win_epoch_iso "$EXP")
[[ "$UNTIL" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] || { wlog "🔴 until 형식 이상: $UNTIL"; exit 1; }
# 표식을 KV **보다 먼저** — 표식 없이 KV 만 eks 인 순간을 만들지 않는다(switch-off 폴백이 표식을 믿는다)
win_lease_mark_eks || { wlog "🔴 임대에 트래픽 표식을 못 붙였다 — KV 를 쓰지 않는다"; exit 1; }
cf_kv_put "{\"target\":\"eks\",\"until\":\"$UNTIL\"}" || exit 1
wlog "KV → eks (until $UNTIL = 임대 만료)"
# KV 전파 ≤ 60s(B8-12) — 러너가 닿는 엣지 기준이다. 다른 엣지는 조금 더 늦을 수 있다.
hdr=""
for _ in $(seq 1 "$TRIES"); do
  hdr=$(curl -sS -o /dev/null -D - --max-time 15 "$PUBLIC/api/v1/daily-question" 2>/dev/null \
          | tr -d '\r' | awk 'tolower($1)=="x-devquest-backend:"{print $2} NR==1{split($0,a," "); c=a[2]} END{print "code=" c}' \
          | tr '\n' ' ') || hdr=""
  case "$hdr" in *"eks code=200"*) break ;; esac
  sleep "$POLL"
done
case "$hdr" in *"eks code=200"*) ;; *) wlog "🔴 공개 URL 이 EKS 로 안 간다($hdr) — off 가 KV 를 fly 로 되돌린다"; exit 1 ;; esac
wlog "✅ 트래픽 → EKS ($PUBLIC 응답 헤더 x-devquest-backend: eks)"
wsummary "- 🚦 트래픽: **EKS** — until \`$UNTIL\` (임대 만료 · 지나면 Worker 가 스스로 Fly)"
