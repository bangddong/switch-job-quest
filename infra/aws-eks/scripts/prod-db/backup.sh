#!/usr/bin/env bash
# prod DB 일일 백업 — 📌D-016 6-A. systemd timer(devquest-db-backup.timer)가 root 로 돈다.
#
#   pg_dump -Fc (스냅샷) ──스트리밍──▶ s3://<버킷>/prod-db/dumps/devquest-<UTC>.dump
#   같은 스냅샷의 행 수       ─────────▶ s3://…/prod-db/dumps/devquest-<UTC>.counts
#   성공 표식(본문 없음)      ─────────▶ s3://…/prod-db/health/backup/<UTC>_disk=<%>_bytes=<n>
#
# 🔑 디스크에 덤프 파일을 남기지 않는다(B6-16) — 데이터 볼륨 10GiB 고정, 설정·WAL 이 같은 볼륨이다.
# 🔑 `pg_dumpall` 금지(레포 규칙) — 롤·비번까지 덤프에 실린다. 롤은 user_data 가 만들고 비번 원천은 SSM 이다.
# 🔴 실패하면 헬스 키를 안 남긴다. 감시(6-D)가 *"헬스 키가 오래됐다"* 로 잡는다 — 버킷 lifecycle 이 30일이라
#    백업이 조용히 멈추면 **마지막 정상 덤프까지 사라진다**(B6-8). 감시가 유일한 방어선이다.
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$here/common.sh"

T="$(stamp)"
KEY="${PREFIX}dumps/devquest-$T.dump"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

# shellcheck disable=SC2034  # snapshot_dump 의 입력
PSQL_PREFIX=("${AS_PG[@]}")
# shellcheck disable=SC2034
PSQL_CONN=(-d "$DB")
log "백업 시작 → s3://$BUCKET/$KEY"
snapshot_dump "$TMP/counts" | aws s3 cp --region "$REGION" --only-show-errors - "s3://$BUCKET/$KEY"

# 크기 되읽기 — 스트리밍은 "끝까지 갔는가" 를 파이프 종료 코드로만 안다. 실물로 한 번 더 본다.
BYTES=$(aws s3api head-object --region "$REGION" --bucket "$BUCKET" --key "$KEY" --query ContentLength --output text)
[[ "$BYTES" =~ ^[0-9]+$ ]] && [ "$BYTES" -gt 0 ] || die "업로드된 덤프 크기 이상: ${BYTES:-없음}"
[ -s "$TMP/counts" ] || die "행 수 파일이 비었다"
grep -q '^flyway|' "$TMP/counts" || die "행 수 파일에 flyway 줄이 없다"

aws s3 cp --region "$REGION" --only-show-errors "$TMP/counts" "s3://$BUCKET/${KEY%.dump}.counts"
TABLES=$(grep -vc '^flyway|' "$TMP/counts" || true)
health backup "disk=$(disk_pct)" "bytes=$BYTES" "tables=$TABLES"
log "✅ 백업 완료 — ${BYTES} bytes, 테이블 ${TABLES}개"
