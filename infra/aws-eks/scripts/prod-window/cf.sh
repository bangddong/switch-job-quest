#!/usr/bin/env bash
# Cloudflare API — 📌D-016 8-C C-2 (방식 ⓑ Worker 스위치). common.sh **다음에** source 한다. 단독 실행하지 않는다.
#
# 🔑 무엇을 바꾸나 — 둘뿐이다
#   ① DNS 레코드 `eks.quest.dhbang.co.kr` CNAME → 그날의 ALB(**DNS only** · TTL 60). 이 이름을 쓰는 건 Worker 뿐이다.
#   ② KV `devquest-backend-switch` 의 키 `api-target` — Worker(`fe/worker/switch.ts`)가 요청마다 읽는다.
#      값 = `{"target":"eks","until":"<ISO 8601, 초·타임존 필수>"}` 일 때만 EKS, 그 밖(키 없음·만료·형식 오류) = Fly.
#   `api.quest`(Fly 직결)·존 SSL·블로그 레코드는 **건드리지 않는다**. DNS Edit 권한은 존 전체라 레코드 이름을 여기 고정한다(B8-13).
#
# 🔴 비밀·식별자
#   · 토큰 = SSM SecureString `/devquest/ops/cloudflare-token`(8-C 전용 — FE 배포 토큰과 다르다). 값을 출력하지 않는다.
#     curl 에는 **인자가 아니라 `-H @<(…)`** 로 넘긴다(프로세스 목록에 안 남게).
#   · 계정 ID·zone ID 는 **레포에 적지 않는다** — 런타임에 API 로 찾고 Actions 에선 `::add-mask::` 한다.
#   · 🔴 cf_init 을 `$( … )` 안에서 부르지 마라 — add-mask 명령이 stdout 으로 나가야 러너가 먹는다.
#
# ⚠️ 토큰 만료 2027-01-24(Cloudflare 실측 10-27). 만료되면 cf_init 이 실패한다 → off 는 switch-off.sh 의 폴백 규칙을 탄다.

CF_API="${CF_API:-https://api.cloudflare.com/client/v4}"
CF_ZONE_NAME="${CF_ZONE_NAME:-dhbang.co.kr}"
CF_RECORD="${CF_RECORD:-eks.quest.dhbang.co.kr}"
CF_KV_TITLE="${CF_KV_TITLE:-devquest-backend-switch}"
CF_KV_KEY="api-target"   # fe/worker/switch.ts SWITCH_KEY 와 같아야 한다
CF_TOKEN_PARAM="${CF_TOKEN_PARAM:-/devquest/ops/cloudflare-token}"
CF_DOH="${CF_DOH:-https://cloudflare-dns.com/dns-query}"

cf_mask() { if [ "${GITHUB_ACTIONS:-}" = true ] && [ -n "${1:-}" ]; then echo "::add-mask::$1"; fi; return 0; }

# $1 메서드 · $2 경로(CF_API 뒤) · [$3 본문] · [$4 Content-Type]. stdout = 응답 본문. 2xx 아니면(curl 실패 포함) rc≠0.
#   HTTP 코드는 **파일**에 남기고 cf_code 로 읽는다 — 호출자가 `r=$(cf_call …)` 로 부르므로 전역 변수는 서브셸에서 사라진다
#   (첫 구현이 그랬다: 404 = "키 없음" 을 못 알아봐 매번 "읽기 실패" 로 떨어졌다 — 목 회귀가 잡았다).
CF_CODE_FILE=$(mktemp)
cf_code() { cat "$CF_CODE_FILE" 2>/dev/null || echo 000; }
cf_call() {
  local method="$1" path="$2" body="${3-}" ctype="${4:-application/json}" tmp code
  tmp=$(mktemp)
  if [ -n "$body" ]; then
    code=$(curl -sS --max-time 20 -o "$tmp" -w '%{http_code}' -X "$method" \
             -H @<(printf 'Authorization: Bearer %s\n' "$CF_TOKEN") -H "Content-Type: $ctype" \
             --data-binary "$body" "$CF_API$path" 2>/dev/null) || code=000
  else
    code=$(curl -sS --max-time 20 -o "$tmp" -w '%{http_code}' -X "$method" \
             -H @<(printf 'Authorization: Bearer %s\n' "$CF_TOKEN") "$CF_API$path" 2>/dev/null) || code=000
  fi
  printf '%s' "$code" > "$CF_CODE_FILE"
  cat "$tmp"; rm -f "$tmp"
  case "$code" in 2??) return 0 ;; *) return 1 ;; esac
}

# 실패 응답에서 errors[].code·message 만 뽑는다 — 식별자를 로그에 흘리지 않게 본문 전체를 찍지 않는다.
cf_err() { jq -r '[.errors[]? | "\(.code) \(.message)"] | join("; ")' 2>/dev/null | cut -c1-200; }

# 토큰·계정·존·KV 네임스페이스를 찾는다. 실패 = rc 1. 🔴 `$( )` 안에서 부르지 마라(위 머리말).
cf_init() {
  local r
  CF_TOKEN=$(aws ssm get-parameter --region "$WIN_REGION" --name "$CF_TOKEN_PARAM" --with-decryption \
               --query 'Parameter.Value' --output text 2>/dev/null) || { wlog "Cloudflare 토큰 조회 실패(SSM)"; return 1; }
  [ -n "$CF_TOKEN" ] && [ "$CF_TOKEN" != None ] || { wlog "Cloudflare 토큰이 비었다"; return 1; }
  cf_mask "$CF_TOKEN"
  r=$(cf_call GET /accounts) || { wlog "계정 조회 실패($(cf_code)): $(printf '%s' "$r" | cf_err)"; return 1; }
  CF_ACCOUNT=$(printf '%s' "$r" | jq -r 'if (.result | length) == 1 then .result[0].id else empty end')
  [ -n "$CF_ACCOUNT" ] || { wlog "계정이 정확히 1개가 아니다 — 토큰 범위 확인"; return 1; }
  cf_mask "$CF_ACCOUNT"
  r=$(cf_call GET "/zones?name=$CF_ZONE_NAME") || { wlog "존 조회 실패($(cf_code)): $(printf '%s' "$r" | cf_err)"; return 1; }
  CF_ZONE=$(printf '%s' "$r" | jq -r 'if (.result | length) == 1 then .result[0].id else empty end')
  [ -n "$CF_ZONE" ] || { wlog "존 $CF_ZONE_NAME 을 못 찾았다"; return 1; }
  cf_mask "$CF_ZONE"
  r=$(cf_call GET "/accounts/$CF_ACCOUNT/storage/kv/namespaces?per_page=100") \
    || { wlog "KV 네임스페이스 조회 실패($(cf_code)): $(printf '%s' "$r" | cf_err)"; return 1; }
  CF_KV_NS=$(printf '%s' "$r" | jq -r --arg t "$CF_KV_TITLE" '[.result[]? | select(.title == $t) | .id] | if length == 1 then .[0] else empty end')
  [ -n "$CF_KV_NS" ] || { wlog "KV 네임스페이스 $CF_KV_TITLE 을 못 찾았다"; return 1; }
  return 0
}

# 스위치 읽기 — stdout = 원문 값 | "none"(키 없음). 그 밖의 실패는 rc 1.
cf_kv_get() {
  local r
  if r=$(cf_call GET "/accounts/$CF_ACCOUNT/storage/kv/namespaces/$CF_KV_NS/values/$CF_KV_KEY"); then
    printf '%s' "$r"; return 0
  fi
  [ "$(cf_code)" = 404 ] && { echo none; return 0; }
  wlog "KV 읽기 실패($(cf_code)): $(printf '%s' "$r" | cf_err)"; return 1
}

# 스위치 쓰기 — $1 = 값(JSON 문자열). 실패 rc 1.
cf_kv_put() {
  local r
  r=$(cf_call PUT "/accounts/$CF_ACCOUNT/storage/kv/namespaces/$CF_KV_NS/values/$CF_KV_KEY" "$1" text/plain) \
    || { wlog "KV 쓰기 실패($(cf_code)): $(printf '%s' "$r" | cf_err)"; return 1; }
  printf '%s' "$r" | jq -e '.success == true' >/dev/null 2>&1 || { wlog "KV 쓰기 응답 success 아님"; return 1; }
}

# 값에서 target 만 — "eks" | "fly" | ""(해석 불가). Worker 와 같은 판정을 흉내 내지 않는다: off 는 eks 일 **가능성**만 본다.
cf_kv_target() { printf '%s' "$1" | jq -r '.target // empty' 2>/dev/null || true; }

# $1 이름의 레코드 목록(JSON 배열 [{id,type,content}]). 실패 rc 1.
cf_dns_list() {
  local r
  r=$(cf_call GET "/zones/$CF_ZONE/dns_records?name=$1&per_page=100") \
    || { wlog "DNS 조회 실패($(cf_code)): $(printf '%s' "$r" | cf_err)"; return 1; }
  # 이름을 **클라이언트에서도** 정확 일치로 거른다(QA F-2) — 삭제 경로가 API 의 `name=` 의미에만 기대지 않게.
  #   어긋나면 `_…eks.quest` ACM 검증 CNAME 이 지워져 인증서 갱신이 실패한다(acm.tf TASK-10).
  printf '%s' "$r" | jq -c --arg n "$1" '[.result[]? | select((.name | ascii_downcase) == ($n | ascii_downcase)) | {id, type, content}]'
}

# CNAME upsert — $1 이름 · $2 대상. 같은 이름에 CNAME 아닌 레코드가 있으면 손대지 않고 rc 1.
cf_dns_upsert() {
  local name="$1" target="$2" list n id body r
  list=$(cf_dns_list "$name") || return 1
  n=$(printf '%s' "$list" | jq '[.[] | select(.type != "CNAME")] | length')
  [ "$n" = 0 ] || { wlog "🔴 $name 에 CNAME 이 아닌 레코드 ${n}개 — 손대지 않는다"; return 1; }
  [ "$(printf '%s' "$list" | jq length)" -le 1 ] || { wlog "🔴 $name CNAME 이 여러 개 — 손대지 않는다"; return 1; }
  id=$(printf '%s' "$list" | jq -r '.[0].id // empty')
  body=$(jq -cn --arg n "$name" --arg c "$target" '{type: "CNAME", name: $n, content: $c, ttl: 60, proxied: false,
           comment: "eks-window(📌D-016 8-C) — off 가 지운다. Worker 만 쓴다"}')
  if [ -n "$id" ]; then
    r=$(cf_call PUT "/zones/$CF_ZONE/dns_records/$id" "$body") || { wlog "DNS 수정 실패($(cf_code)): $(printf '%s' "$r" | cf_err)"; return 1; }
  else
    r=$(cf_call POST "/zones/$CF_ZONE/dns_records" "$body") || { wlog "DNS 생성 실패($(cf_code)): $(printf '%s' "$r" | cf_err)"; return 1; }
  fi
  wlog "DNS $name → $target (DNS only · TTL 60)"
}

# CNAME 삭제 — $1 이름. 없으면 성공. CNAME 아닌 레코드는 지우지 않는다.
cf_dns_delete() {
  local name="$1" list id fail=0
  list=$(cf_dns_list "$name") || return 1
  for id in $(printf '%s' "$list" | jq -r '.[] | select(.type == "CNAME") | .id'); do
    cf_call DELETE "/zones/$CF_ZONE/dns_records/$id" >/dev/null || { wlog "DNS 삭제 실패($(cf_code))"; fail=1; }
  done
  return "$fail"
}

# 공개 DNS(DoH)가 $1 의 CNAME 을 $2 로 답하는가. 러너 리졸버의 음성 캐시를 피하려고 DoH 로 묻는다.
cf_doh_cname_is() {
  local got
  got=$(curl -sS --max-time 10 -H 'accept: application/dns-json' "$CF_DOH?name=$1&type=CNAME" 2>/dev/null \
          | jq -r '[.Answer[]? | select(.type == 5) | .data | rtrimstr(".")] | first // empty' 2>/dev/null) || got=""
  [ "$got" = "$2" ]
}

# epoch → `2026-10-28T09:00:00Z` (Worker 값 계약: 초·타임존 필수 — switch.ts ISO_WITH_ZONE). GNU·BSD date 둘 다.
win_epoch_iso() { date -u -d "@$1" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -r "$1" +%Y-%m-%dT%H:%M:%SZ; }
