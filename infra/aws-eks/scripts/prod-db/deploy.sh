#!/usr/bin/env bash
# 로컬(운영자)에서 돈다 — 이 디렉터리를 prod DB EC2 에 설치한다. 📌D-016 6-A.
#
#   ① 이 디렉터리를 tar → s3://<버킷>/prod-db/tooling/<커밋 SHA>.tgz   (EC2 역할은 prod-db/ 만 Get 가능)
#   ② SSM Run Command(AWS-RunShellScript) → EC2 가 받아서 install.sh 실행   (Postgres 재시작 없음)
#
# 🔑 **main 에 머지된 커밋만** 설치한다 — 리뷰 안 된 스크립트가 prod DB 위에서 root 로 도는 것을 막는다.
#    작업 트리가 깨끗하고 HEAD 가 origin/main 과 같아야 한다.
# 출력은 로컬 터미널로만 — CI 에서 돌리지 않는다(퍼블릭 로그, B6-30). 비밀은 다루지 않는다.
#
# 사용: deploy.sh [--cutover-done]       (7번 전환 완료 뒤 한 번 --cutover-done 으로 재설치)
set -euo pipefail
ROOT="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
DIR="$ROOT/infra/aws-eks/scripts/prod-db"
REGION="${AWS_REGION:-ap-northeast-2}"
EXTRA=""
case "${1:-}" in --cutover-done) EXTRA="--cutover-done" ;; "") ;; *) echo "알 수 없는 인자: $1" >&2; exit 2 ;; esac

git -C "$ROOT" fetch -q origin main
[ -z "$(git -C "$ROOT" status --porcelain -- "$DIR")" ] || { echo "❌ $DIR 에 커밋 안 된 변경이 있다" >&2; exit 1; }
SHA=$(git -C "$ROOT" rev-parse HEAD)
[ "$SHA" = "$(git -C "$ROOT" rev-parse origin/main)" ] || { echo "❌ HEAD 가 origin/main 이 아니다 — 머지된 것만 설치한다" >&2; exit 1; }

OUT() { tofu -chdir="$ROOT/infra/aws-eks/1-network" output -raw "$1"; }
INSTANCE=$(OUT db_instance_id) ; BUCKET=$(OUT db_backup_bucket) ; PREFIX=$(OUT db_backup_prefix)
[[ "$INSTANCE" == i-* ]] || { echo "❌ db_instance_id 없음 — db_enabled=false?" >&2; exit 1; }

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
tar -C "$DIR" --exclude tests -czf "$TMP/tooling.tgz" .
KEY="${PREFIX}tooling/$SHA.tgz"
aws s3 cp --region "$REGION" --only-show-errors "$TMP/tooling.tgz" "s3://$BUCKET/$KEY"
echo "[deploy] 올림: s3://$BUCKET/$KEY"

# ⚠️ AWS-RunShellScript 는 명령을 **sh** 로 돌린다(Ubuntu = dash) — bash 전용 문법(`pipefail` 등)을 쓰지 않는다(QA F-3).
#    install.sh 는 명시적으로 bash 로 부른다.
CMDS=$(jq -cn --arg b "$BUCKET" --arg k "$KEY" --arg r "$REGION" --arg p "$PREFIX" --arg x "$EXTRA" '[
  "set -eu",
  "export PATH=$PATH:/snap/bin",
  "d=$(mktemp -d); trap \"rm -rf $d\" EXIT",
  ("aws s3 cp --region " + $r + " --only-show-errors s3://" + $b + "/" + $k + " $d/t.tgz"),
  "tar -xzf $d/t.tgz -C $d",
  ("bash $d/install.sh --region " + $r + " --bucket " + $b + " --prefix " + $p + " " + $x)
]')
CID=$(aws ssm send-command --region "$REGION" --instance-ids "$INSTANCE" --document-name AWS-RunShellScript \
        --comment "devquest-db tooling $SHA" --parameters "{\"commands\":$CMDS}" --query Command.CommandId --output text)
echo "[deploy] SSM 명령 $CID — 완료 대기"
aws ssm wait command-executed --region "$REGION" --command-id "$CID" --instance-id "$INSTANCE" || true
aws ssm get-command-invocation --region "$REGION" --command-id "$CID" --instance-id "$INSTANCE" \
  --query '[Status, StandardOutputContent, StandardErrorContent]' --output text
[ "$(aws ssm get-command-invocation --region "$REGION" --command-id "$CID" --instance-id "$INSTANCE" --query Status --output text)" = Success ]
