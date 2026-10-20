#!/usr/bin/env bash
# prod DB 백업 감시 — 📌D-016 6-D. GitHub Actions(prod-db-backup-watch.yml)가 매일, 로컬에서도 손으로 돈다.
#
# 🔴 왜 이것이 "유일한 방어선"인가(B6-8): 백업 버킷 lifecycle 은 버킷 전체 30일이다. 백업이 **조용히 멈추면**
#    30일 뒤 마지막 정상 덤프까지 사라진다. 백업 스크립트는 실패하면 헬스 키를 안 남길 뿐 아무에게도 알리지 않는다.
#    → 여기서 *"최근 헬스 키가 있는가"* 를 본다. 없으면 빨강 = GitHub 실패 메일.
#
# 🔑 **본문을 읽지 않는다 — 키 이름만**(B6-31). 감시는 좁힌 읽기 전용 역할로 돈다: `ListObjectsV2` 는 되고
#    `GetObject`·`HeadObject` 는 Deny 다(백업 = prod 데이터). 그래서 backup.sh 가 값을 키 이름에 넣는다:
#      prod-db/health/backup/<UTC>_disk=<%>_bytes=<n>_tables=<n>
#      prod-db/health/restore/<UTC>_secs=<n>_tables=<n>
#
# 판정 (하나라도 어기면 exit 1):
#   ① 최신 backup 헬스 키 나이 ≤ MAX_BACKUP_AGE_H(26h — 매일 03:3x KST + 지연·랜덤 10분 여유)
#   ② 그 키의 disk ≤ MAX_DISK_PCT(80 — 데이터 볼륨 10GiB 고정·자동 확장 없음, N-19)
#   ③ tables ≥ MIN_TABLES(1 — 0 이면 DB 가 비었다는 뜻: 백업은 "성공" 해도 지킬 게 없다)
#   ④ 최신 restore 헬스 키 나이 ≤ MAX_RESTORE_AGE_H(194h = 8일 — 주 1회 + 여유)
#   (restore·backup 의 tables 비교는 하지 않는다 — 주 1회 복원 뒤 마이그레이션이 테이블을 더하면 정상인데도 빨갛다)
# ⚠️ 조회 실패·키 형식 불일치는 **실패로** 친다(fail-closed) — "못 봤다" 를 "괜찮다" 로 읽지 않는다.
set -euo pipefail
BUCKET="${BUCKET:-devquest-eks-backups-seoul}"
PREFIX="${PREFIX:-prod-db/}"
REGION="${AWS_REGION:-ap-northeast-2}"
MAX_BACKUP_AGE_H="${MAX_BACKUP_AGE_H:-26}"
MAX_DISK_PCT="${MAX_DISK_PCT:-80}"
MIN_TABLES="${MIN_TABLES:-1}"
MAX_RESTORE_AGE_H="${MAX_RESTORE_AGE_H:-194}"
NOW="${WATCH_NOW:-$(date -u +%s)}"   # 테스트용 시각 주입

FAIL=0
bad()  { echo "❌ $*"; FAIL=1; }
good() { echo "✅ $*"; }
summary() { [ -n "${GITHUB_STEP_SUMMARY:-}" ] && echo "$*" >> "$GITHUB_STEP_SUMMARY" || true; }

# <UTC 스탬프 YYYYmmddTHHMMSSZ> → epoch (GNU 먼저, BSD 폴백)
stamp_epoch() {
  local s="$1" iso
  iso="${s:0:4}-${s:4:2}-${s:6:2}T${s:9:2}:${s:11:2}:${s:13:2}Z"
  date -u -d "$iso" +%s 2>/dev/null || date -u -j -f '%Y%m%dT%H%M%SZ' "$s" +%s 2>/dev/null
}

latest() {  # $1 = kind → 최신 키의 basename (없으면 빈 문자열, 조회 실패면 exit 2)
  local k
  k=$(aws s3api list-objects-v2 --region "$REGION" --bucket "$BUCKET" --prefix "${PREFIX}health/$1/" \
        --query 'sort_by(Contents || `[]`, &Key)[-1].Key' --output text) || { echo "조회 실패: health/$1" >&2; exit 2; }
  [ "$k" = "None" ] && k=""
  echo "${k##*/}"
}

field() { echo "$1" | tr '_' '\n' | sed -n "s/^$2=//p"; }   # $1=basename, $2=키

summary "### prod DB 백업 감시"
summary ""
summary "| 판정 | 값 | 기준 |"
summary "|---|---|---|"

B=$(latest backup)
if [[ ! "$B" =~ ^[0-9]{8}T[0-9]{6}Z_ ]]; then
  bad "backup 헬스 키가 없거나 형식이 다르다(${B:-없음}) — 백업이 한 번도 성공하지 않았거나 멈췄다"
  summary "| ❌ backup | ${B:-없음} | 헬스 키 존재 |"
else
  BT=$(stamp_epoch "${B%%_*}") || { bad "backup 스탬프 해석 실패: $B"; BT=0; }
  AGE_H=$(( (NOW - BT) / 3600 ))
  DISK=$(field "$B" disk); BYTES=$(field "$B" bytes); BTAB=$(field "$B" tables)
  if [ "$AGE_H" -le "$MAX_BACKUP_AGE_H" ]; then good "backup ${AGE_H}h 전"; summary "| ✅ backup 나이 | ${AGE_H}h | ≤ ${MAX_BACKUP_AGE_H}h |"
  else bad "backup 이 ${AGE_H}h 전이 마지막 — 멈췄다(기준 ${MAX_BACKUP_AGE_H}h). 30일이 지나면 마지막 덤프도 lifecycle 로 사라진다"; summary "| ❌ backup 나이 | ${AGE_H}h | ≤ ${MAX_BACKUP_AGE_H}h |"; fi
  if [[ "$DISK" =~ ^[0-9]+$ ]] && [ "$DISK" -le "$MAX_DISK_PCT" ]; then good "disk ${DISK}%"; summary "| ✅ 데이터 볼륨 | ${DISK}% | ≤ ${MAX_DISK_PCT}% |"
  else bad "데이터 볼륨 ${DISK:-?}% — 10GiB 고정, 차면 DB 쓰기 실패(기준 ${MAX_DISK_PCT}%)"; summary "| ❌ 데이터 볼륨 | ${DISK:-?}% | ≤ ${MAX_DISK_PCT}% |"; fi
  if [[ "$BTAB" =~ ^[0-9]+$ ]] && [ "$BTAB" -ge "$MIN_TABLES" ] && [[ "$BYTES" =~ ^[0-9]+$ ]] && [ "$BYTES" -gt 0 ]; then
    good "tables ${BTAB}, ${BYTES} bytes"; summary "| ✅ 덤프 | ${BTAB} tables · ${BYTES} B | ≥ ${MIN_TABLES} tables |"
  else bad "덤프가 비었다 — tables=${BTAB:-?} bytes=${BYTES:-?}"; summary "| ❌ 덤프 | ${BTAB:-?} tables · ${BYTES:-?} B | ≥ ${MIN_TABLES} tables |"; fi
fi

Rk=$(latest restore)
if [[ ! "$Rk" =~ ^[0-9]{8}T[0-9]{6}Z_ ]]; then
  bad "restore 헬스 키가 없다(${Rk:-없음}) — 복원 판정이 한 번도 통과하지 않았다"
  summary "| ❌ restore | ${Rk:-없음} | 헬스 키 존재 |"
else
  RT=$(stamp_epoch "${Rk%%_*}") || { bad "restore 스탬프 해석 실패: $Rk"; RT=0; }
  RAGE_H=$(( (NOW - RT) / 3600 )); RSECS=$(field "$Rk" secs)
  if [ "$RAGE_H" -le "$MAX_RESTORE_AGE_H" ]; then good "restore ${RAGE_H}h 전 (${RSECS}s)"; summary "| ✅ 복원 판정 | ${RAGE_H}h 전 · ${RSECS}s | ≤ ${MAX_RESTORE_AGE_H}h |"
  else bad "복원 판정이 ${RAGE_H}h 전이 마지막(기준 ${MAX_RESTORE_AGE_H}h) — 백업이 복원되는지 모른다"; summary "| ❌ 복원 판정 | ${RAGE_H}h 전 | ≤ ${MAX_RESTORE_AGE_H}h |"; fi
fi

[ "$FAIL" -eq 0 ] && echo "── 백업 감시 통과" || echo "── 🔴 백업 감시 실패"
exit "$FAIL"
