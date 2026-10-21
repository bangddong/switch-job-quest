#!/usr/bin/env bash
# EC2 위에서 root 로 돈다 — deploy.sh 가 SSM Run Command 로 부른다. 📌D-016 6-A.
#
# 🔑 왜 user_data 가 아닌가(B6-10): user_data 는 `ignore_changes` 라 켠 뒤 고친 게 반영되지 않고,
#    다시 돌리면 Postgres 를 재시작해 Fly·EKS 가 동시에 끊긴다. 이 설치는 **파일만 바꾸고 Postgres 를 건드리지 않는다.**
# 🔁 멱등 — 몇 번 돌려도 같은 상태. 실행 중인 백업은 끊지 않는다(timer 만 다시 읽는다).
#
# 사용: install.sh --region R --bucket B --prefix P [--ssm-prefix S] [--db D] [--cutover-done]
set -euo pipefail
REGION="" BUCKET="" PREFIX="" SSM_PREFIX="/devquest/prod/db" DB="devquest" CUTOVER=0
while [ $# -gt 0 ]; do
  case "$1" in
    --region) REGION="$2"; shift 2 ;;
    --bucket) BUCKET="$2"; shift 2 ;;
    --prefix) PREFIX="$2"; shift 2 ;;
    --ssm-prefix) SSM_PREFIX="$2"; shift 2 ;;
    --db) DB="$2"; shift 2 ;;
    --cutover-done) CUTOVER=1; shift ;;
    *) echo "알 수 없는 인자: $1" >&2; exit 2 ;;
  esac
done
[ -n "$REGION" ] && [ -n "$BUCKET" ] && [ -n "$PREFIX" ] || { echo "--region --bucket --prefix 필수" >&2; exit 2; }
[[ "$PREFIX" == */ && "$PREFIX" != "postgres/" ]] || { echo "prefix 는 / 로 끝나고 학습용 postgres/ 가 아니어야 한다(B6-6)" >&2; exit 2; }
[ "$(id -u)" = 0 ] || { echo "root 로 실행" >&2; exit 2; }

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE=/etc/devquest-db.env

# 🔴 CUTOVER_DONE 은 **내려가지 않는다.** 한 번 1 이 되면(7번 전환 완료) 인자 없이 재설치해도 1 을 유지한다.
#    0 으로 되돌아가면 neon-import 가 다시 열리고, `--reset` 한 번이 prod 원본을 지운다.
if grep -q '^CUTOVER_DONE=1$' "$ENV_FILE" 2>/dev/null; then CUTOVER=1; fi
# 데이터 볼륨 표식(QA F-2) — 인스턴스를 교체해 /etc 가 사라져도 남는다. 있으면 1, --cutover-done 이면 만든다.
MARK=/var/lib/postgresql/.devquest-cutover-done
[ -e "$MARK" ] && CUTOVER=1
NEW_CUTOVER=0
if [ "$CUTOVER" = 1 ] && [ ! -e "$MARK" ]; then
  # 데이터 볼륨이 없으면 조용히 건너뛰지 않는다(QA F-9) — 그러면 /etc 에만 1 이 남아 F-2 가 막으려던 구멍 그대로다.
  [ -d /var/lib/postgresql ] || { echo "❌ /var/lib/postgresql 없음 — 데이터 볼륨이 붙은 뒤 다시 --cutover-done" >&2; exit 1; }
  date -u +%FT%TZ > "$MARK"
  NEW_CUTOVER=1
fi

install -d -m 0755 /opt/devquest-db
install -m 0755 "$SRC/common.sh" "$SRC/backup.sh" "$SRC/restore-check.sh" "$SRC/neon-import.sh" "$SRC/standby-restore.sh" "$SRC/wg-watch.sh" /opt/devquest-db/
umask 022
cat > "$ENV_FILE.tmp" <<EOF
# install.sh 가 쓴다 — 손으로 고치지 말고 deploy.sh 로 다시 설치한다. 비밀 없음.
REGION=$REGION
BUCKET=$BUCKET
PREFIX=$PREFIX
SSM_PREFIX=$SSM_PREFIX
DB=$DB
CUTOVER_DONE=$CUTOVER
EOF
mv "$ENV_FILE.tmp" "$ENV_FILE"

# ── swap 1GiB (📌D-016 7-A, B7-21) — 904MB 에 swap 이 없으면 커넥션이 몰릴 때 OOM-killer 가 Postgres 를 죽인다.
#    루트 볼륨(8GiB)에 둔다. 멱등. swappiness 10 = 정말 모자랄 때만.
#    실패해도 설치는 계속한다(컨테이너 테스트에선 swapon 이 안 된다) — 대신 크게 경고한다.
setup_swap() {
  if [ ! -e /swapfile ]; then fallocate -l 1G /swapfile && chmod 600 /swapfile && mkswap /swapfile >/dev/null; fi
  swapon --show=NAME --noheadings | grep -qx /swapfile || swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  echo 'vm.swappiness=10' > /etc/sysctl.d/90-devquest.conf && sysctl -q -p /etc/sysctl.d/90-devquest.conf
}
setup_swap 2>/dev/null || echo "[install] ⚠️ swap 설정 실패 — OOM 위험(B7-21). free -m 으로 확인"

# ── Postgres 튜닝(📌D-016 7-A) — conf.d 는 데이터 볼륨의 설정 디렉터리(심볼릭). **reload 만 한다.**
#    max_connections·shared_buffers 는 재시작이 필요하다 — 재시작은 사람이 창 밖에서(전환 뒤엔 Fly·EKS 동시 장애).
CONFD=/etc/postgresql/17/main/conf.d
if [ -d "$CONFD" ]; then
  install -m 0644 -o postgres -g postgres "$SRC/postgres/20-devquest-tuning.conf" "$CONFD/20-devquest-tuning.conf"
  pg_ctlcluster 17 main reload
  PENDING=$(runuser -u postgres -- psql -X -A -t -d postgres -c "SELECT string_agg(name, ',') FROM pg_settings WHERE pending_restart")
  [ -z "$PENDING" ] || echo "[install] ⚠️ 재시작이 필요한 설정: $PENDING — systemctl restart postgresql@17-main (런북 「패치」 — 전환 뒤엔 창 밖에서)"
fi

install -m 0644 "$SRC"/systemd/devquest-db-*.service "$SRC"/systemd/devquest-db-*.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now devquest-db-backup.timer devquest-db-restore-check.timer devquest-db-wg-watch.timer
# Neon 콜드 대기 매일 복원은 **전환 뒤에만**(📌D-016 7 — 전환 전엔 Neon 이 prod 원본이라 대기가 의미 없다).
if [ "$CUTOVER" = 1 ]; then
  systemctl enable --now devquest-db-standby.timer
else
  systemctl disable --now devquest-db-standby.timer 2>/dev/null || true
fi
# 전환 표식을 처음 남길 때 S3 헬스 키도 — 감시(watch.sh)가 이것을 보고 "대기 복원도 신선해야 한다" 로 바뀐다.
if [ "$NEW_CUTOVER" = 1 ]; then
  PATH="$PATH:/snap/bin" aws s3api put-object --region "$REGION" --bucket "$BUCKET" \
    --key "${PREFIX}health/cutover/$(date -u +%Y%m%dT%H%M%SZ)" >/dev/null && echo "[install] 전환 헬스 키 기록"
fi
echo "[install] ✅ 설치 완료 — CUTOVER_DONE=$CUTOVER"
systemctl list-timers 'devquest-db-*' --no-pager
