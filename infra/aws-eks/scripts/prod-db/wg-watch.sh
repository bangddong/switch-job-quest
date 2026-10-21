#!/usr/bin/env bash
# Fly 6PN WireGuard 감시 — 📌D-016 7-A (B7-23). systemd timer 가 3분마다 root 로.
#
# 🔴 왜: `wg-quick` 은 `Endpoint` 호스트명을 **기동할 때 한 번만** 해석한다. Fly 게이트웨이 IP 가 바뀌면
#    재시작 전까지 터널이 죽고 — 전환 뒤엔 그것이 곧 **Fly prod 의 DB 장애**다. 감시도 재해석도 없었다.
# 하는 일: ① Endpoint 를 다시 해석해 `wg set … endpoint` (IP 가 같으면 무해) ② 최근 핸드셰이크가 STALE 초보다
#    오래됐으면 `wg-quick@fly0` 재기동. Fly 설정에는 PersistentKeepalive 가 있어 정상이면 ≈2분마다 핸드셰이크한다.
# 비밀 없음 — 설정 파일의 Endpoint·피어 공개키만 읽는다(개인키를 출력하지 않는다).
set -euo pipefail
IF=fly0; CONF=/etc/wireguard/$IF.conf; STALE="${WG_STALE_SECS:-600}"
log() { echo "[wg-watch] $*"; }
[ -s "$CONF" ] || { log "설정 없음: $CONF"; exit 1; }
EP=$(awk -F' *= *' '/^Endpoint/{print $2; exit}' "$CONF")
[ -n "$EP" ] || { log "Endpoint 없음"; exit 1; }
if ! wg show "$IF" >/dev/null 2>&1; then
  log "인터페이스 $IF 없음 — 기동"; systemctl restart "wg-quick@$IF"; exit 0
fi
PK=$(wg show "$IF" peers | head -1)
wg set "$IF" peer "$PK" endpoint "$EP" 2>/dev/null || log "endpoint 재해석 실패($EP) — 다음 주기"
LAST=$(wg show "$IF" latest-handshakes | awk '{print $2}' | head -1)
AGE=$(( $(date +%s) - ${LAST:-0} ))
if [ "${LAST:-0}" -eq 0 ] || [ "$AGE" -gt "$STALE" ]; then
  log "핸드셰이크 ${AGE}s 전(기준 ${STALE}s) — wg-quick@$IF 재기동"
  systemctl restart "wg-quick@$IF"
fi
