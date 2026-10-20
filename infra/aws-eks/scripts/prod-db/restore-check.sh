#!/usr/bin/env bash
# 복원 리허설 — 📌D-016 6-A. 최신 S3 덤프를 **같은 인스턴스의 스크래치 DB** 에 복원하고 데이터로 판정한다.
# systemd timer(devquest-db-restore-check.timer, 주 1회)가 root 로 돈다. 손으로도 돌린다(6-C).
#
#   판정 = 덤프와 **같은 스냅샷**에서 센 행 수(.counts) ↔ 복원본의 행 수 + Flyway 이력 지문. 하나라도 다르면 실패.
#   성공 표식 → s3://…/prod-db/health/restore/<UTC>_secs=<복원 초>_tables=<n>   (RTO 실측값이 키에 남는다)
#
# 🔑 스크래치 DB 는 판정 직후 **반드시** 지운다(trap) — 같은 10GiB 볼륨에서 데이터를 두 배로 쓴다(B6-16).
# 🔑 앱 기동이 아니라 데이터로 판정한다 — external 모드는 앱 기동 Flyway 가 꺼져 있고(5-B),
#    `app.jar validate` 는 빈 DB 도 통과시킨다(B6-3). EC2 엔 JVM 도 없다(B6-14).
# ⚠️ CPU 크레딧 `standard` — 인덱스 재생성이 크레딧을 태우면 prod 도 기준선에 묶인다(B6-15). 주기는 6-C 실측으로 정한다.
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$here/common.sh"

SCRATCH="${DB}_restore_check"
[ "$SCRATCH" != "$DB" ] || die "스크래치 DB 이름이 원본과 같다"
TMP="$(mktemp -d)"
drop_scratch() { "${AS_PG[@]}" psql -X -q -v ON_ERROR_STOP=1 -d postgres -c "DROP DATABASE IF EXISTS \"$SCRATCH\" WITH (FORCE)" >/dev/null 2>&1 || log "⚠️ 스크래치 DB 삭제 실패 — 손으로: DROP DATABASE \"$SCRATCH\""; }
trap 'drop_scratch; rm -rf "$TMP"' EXIT

# 🔑 `.counts` 가 있는 것 중 최신(QA F-1) — `.counts` 는 덤프 업로드가 **끝난 뒤** 올라가므로 커밋 표식이다.
#    백업이 중간에 죽으면 잘린 `.dump` 만 남는다(인스턴스 역할엔 Delete 가 없다). `.dump` 로 고르면 그 잘린 것을 골라
#    거짓 실패하고, 최신 정상 덤프는 검증되지 않는다. 사람이 DR 때 덤프를 고를 때도 같은 규칙: **짝이 있는 것**.
CKEY=$(aws s3api list-objects-v2 --region "$REGION" --bucket "$BUCKET" --prefix "${PREFIX}dumps/" \
        --query "sort_by(Contents[?ends_with(Key, '.counts')], &Key)[-1].Key" --output text)
[[ "$CKEY" == "${PREFIX}dumps/devquest-"*.counts ]] || die "완결된 덤프(.counts 짝)가 없다(${CKEY:-없음})"
KEY="${CKEY%.counts}.dump"
aws s3 cp --region "$REGION" --only-show-errors "s3://$BUCKET/$CKEY" "$TMP/expected" \
  || die "행 수 파일을 못 받았다: $CKEY"
log "복원 대상: $KEY"

# 여유 공간 — 스크래치는 prod PGDATA·WAL 과 **같은 10GiB 볼륨**에 생긴다. 모자라면 점검이 prod 를 ENOSPC 로 죽인다(QA F-6).
#   필요량 ≈ 원본 크기 × 2(테이블+인덱스 재생성 여유) + 1GiB(WAL·여유). 모자라면 헬스 키 없이 실패 → 감시가 잡는다.
SIZE=$("${AS_PG[@]}" psql -X -q -A -t -v ON_ERROR_STOP=1 -d postgres -c "SELECT pg_database_size('$DB')")
FREE=$(( $(df --output=avail -B1 /var/lib/postgresql | tail -1 | tr -dc '0-9') ))
NEED=$(( SIZE * 2 + 1073741824 ))
[ "$FREE" -gt "$NEED" ] || die "여유 공간 부족 — free=${FREE} need=${NEED}(원본 ${SIZE}×2+1GiB). 스크래치 복원이 prod 볼륨을 채울 수 있다"

drop_scratch
# 소유자 devquest + 복원도 --role=devquest — neon-import·실복원과 **같은 권한 경로**를 검증한다(QA F-7).
"${AS_PG[@]}" psql -X -q -v ON_ERROR_STOP=1 -d postgres -c "CREATE DATABASE \"$SCRATCH\" OWNER devquest" >/dev/null
START=$(date +%s)
# 스트리밍 복원 — 임시 파일 없음. stdin 이라 `-j` 는 못 쓴다(작은 DB 라 무관).
aws s3 cp --region "$REGION" --only-show-errors "s3://$BUCKET/$KEY" - \
  | "${AS_PG[@]}" pg_restore --no-owner --no-acl --role=devquest --single-transaction --exit-on-error -d "$SCRATCH"
SECS=$(( $(date +%s) - START ))

local_counts "$SCRATCH" > "$TMP/actual"
if ! diff -u "$TMP/expected" "$TMP/actual" >&2; then
  die "복원본이 덤프 시점과 다르다(위 diff: - 덤프 시점, + 복원본)"
fi
TABLES=$(grep -vc '^flyway|' "$TMP/actual" || true)
health restore "secs=$SECS" "tables=$TABLES"
log "✅ 복원 판정 통과 — ${SECS}s, 테이블 ${TABLES}개, Flyway 이력 일치"
