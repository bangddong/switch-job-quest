# prod DB 운영 런북 — EC2 Postgres 17 (📌D-016)

> prod DB 는 `1-network` 의 EC2 t4g.micro 한 대다(`Role=prod-db`). 7번 전환 **뒤에는** Fly(밤·주말)와 EKS(평일 창)가 **같은 DB** 를 쓴다.
> 🔴 **2026-10-20 현재는 전환 전**이다 — 앱은 아직 Neon 을 본다. EC2 에는 6-C 리허설로 옮겨 온 사본이 있다.
> 결정·근거: `infra/aws-eks/README.md` D-016 · 계획서 `docs/superpowers/plans/2026-10-16-prod-eks-weekday-window.md` 「5번」「6번」.
> 🔴 이 디렉터리는 **prod 전용**이다. 학습 클러스터용 `../db-backup.sh`·`../db-restore.sh` 와 섞지 마라(N-17).

## 지금 무엇이 자동으로 도는가

| 무엇 | 언제 | 어디서 | 결과 |
|---|---|---|---|
| 백업 `backup.sh` | **6시간마다** 03:30 · 09:30 · 15:30 · 21:30 KST(±5분) — RPO 6h | EC2 systemd `devquest-db-backup.timer` | `s3://devquest-eks-backups-seoul/prod-db/dumps/devquest-<UTC>.{dump,counts}` + `health/backup/<UTC>_disk=_bytes=_tables=` |
| 복원 판정 `restore-check.sh` | 매주 일 04:30 KST | EC2 systemd `devquest-db-restore-check.timer` | 최신 덤프 → 스크래치 DB → 행 수·Flyway 이력 **값 비교** → `health/restore/<UTC>_secs=_tables=` |
| Neon 대기 복원 `standby-restore.sh` | 매일 04:30 KST — **전환 뒤에만**(install 이 `CUTOVER_DONE=1` 일 때 켠다) | EC2 systemd `devquest-db-standby.timer` | 최신 덤프 → Neon 전용 대기 브랜치 → 값·시퀀스 판정 → `health/standby/` |
| WireGuard 감시 `wg-watch.sh` | 3분마다 | EC2 systemd `devquest-db-wg-watch.timer` | Endpoint 재해석 + 핸드셰이크 600s 넘게 끊기면 `wg-quick@fly0` 재기동(B7-23) |
| 감시 `watch.sh` | 매일 11:00 KST | GitHub Actions `Prod DB Backup Watch`(읽기 전용 역할) | 헬스 키 신선도(백업 **8h** · 복원 160h · **전환 뒤 대기 30h**)·디스크 80%·테이블 ≥1. **빨강 = 실패 메일** — 도달 확인: 수동 실행 `inject_failure` |

> 튜닝(📌D-016 7-A): `postgres/20-devquest-tuning.conf` — `max_connections=50`(커넥션 예산: Fly 10 + release 10, EKS 는 10번에서 25 이하로), keepalive 60s, swap 1GiB(`/swapfile`). install 은 **reload 만** 하고 재시작 필요 항목을 출력한다.

> ⚠️ **실패 메일은 네이버에서 스팸함으로 갔다**(2026-10-20 실측). 제목 `[bangddong/switch-job-quest] Run failed: Prod DB Backup Watch …`, 발신 `notifications@github.com` — 수신 허용에 등록해 둔다.
> 메일 주소를 바꾸거나 새 기기·계정에서 다시 확인할 땐 Actions 화면 *Run workflow* → `inject_failure` 체크로 일부러 빨갛게 만든다.

보존: 버킷 lifecycle **30일**(현행·이전 버전 모두). 🔴 백업이 멈추면 30일 뒤 **마지막 정상 덤프까지 사라진다** — 감시 메일을 무시하지 마라.

## 접속

SSH 포트는 없다. SSM 으로만 들어간다.

```bash
ID=$(tofu -chdir=infra/aws-eks/1-network output -raw db_instance_id)
aws ssm start-session --region ap-northeast-2 --target "$ID"
sudo -i
runuser -u postgres -- psql -d devquest
```

비밀번호 원천은 SSM `/devquest/prod/db/password`(EC2 가 첫 부팅 때 만든다). 로컬 접속엔 필요 없다(peer 인증).

## 도구 설치·갱신

스크립트를 고쳤으면 **머지한 뒤** 로컬에서:

```bash
bash infra/aws-eks/scripts/prod-db/deploy.sh      # HEAD == origin/main 이어야 한다
```

S3 `prod-db/tooling/<sha>.tgz` 로 올리고 SSM Run Command 로 `install.sh` 를 돌린다. **Postgres 를 재시작하지 않는다.**
🔴 user_data 를 다시 돌리지 마라(`/var/lib/cloud/instance/scripts/part-001`) — Postgres 를 재시작해 Fly·EKS 가 함께 끊긴다.

## 손으로 돌리기

```bash
systemctl start devquest-db-backup.service        && journalctl -u devquest-db-backup -n 5 -o cat
systemctl start devquest-db-restore-check.service && journalctl -u devquest-db-restore-check -n 5 -o cat
bash infra/aws-eks/scripts/prod-db/watch.sh        # 로컬(자격증명 필요) — 헬스 키 판정
```

## 🔴 복구 — DB 가 망가졌을 때 (RTO 실측: 8.7MB 기준 복원 1초)

**고르는 덤프 규칙: `.counts` 짝이 있는 최신.** 백업이 중간에 죽으면 잘린 `.dump` 만 남는다(인스턴스 역할에 Delete 가 없어 지우지 못한다).

> 🔴 **전환(7번) 전에는 앱이 Neon 을 본다** — EC2 DB 가 망가져도 prod 트래픽은 영향이 없다. Fly·EKS 를 건드리지 말고
> 아래 1~5단계를 그대로 한다. **전환 후**에는 2단계의 `ALLOW_CONNECTIONS false` 가 곧 쓰기 차단이다 — 복원하는 동안 앱은
> DB 에러(5xx)를 낸다. Fly 머신을 줄이지 않는다(`fly scale count 0` 은 머신을 **파괴**하고 복귀 절차가 검증되지 않았다).

아래 블록은 2026-10-20 실 EC2 에서 **테스트 DB 이름으로 그대로** 돌려 검증했다(접속 중 세션 종료 → 이름 변경 → 복원 → 판정 일치). `sudo -i` 한 root 셸에서:

1. 마지막 정상 덤프를 고른다:
   ```bash
   B=devquest-eks-backups-seoul
   C=$(aws s3 ls s3://$B/prod-db/dumps/ | awk '{print $4}' | grep '\.counts$' | sort | tail -1); echo "$C"
   ```
2. 현재 DB 를 지우지 말고 **이름을 바꿔 남긴다**(되돌릴 길). 🔴 반드시 `-d postgres` 로 — 자기 DB 는 이름을 못 바꾸고
   (`current database cannot be renamed`), 다른 세션이 붙어 있어도 실패한다 → 새 접속을 막고 남은 세션을 끊는다:
   ```bash
   DB=devquest; OLD=${DB}_broken_$(date -u +%Y%m%d%H%M)
   runuser -u postgres -- psql -X -v ON_ERROR_STOP=1 -d postgres <<SQL
   ALTER DATABASE $DB ALLOW_CONNECTIONS false;
   SELECT count(pg_terminate_backend(pid)) FROM pg_stat_activity WHERE datname = '$DB' AND pid <> pg_backend_pid();
   ALTER DATABASE $DB RENAME TO $OLD;
   CREATE DATABASE $DB OWNER devquest;
   SQL
   ```
3. 복원(소유자 devquest — 실복원·restore-check·neon-import 와 같은 권한 경로):
   ```bash
   aws s3 cp s3://$B/prod-db/dumps/${C%.counts}.dump - \
     | runuser -u postgres -- pg_restore --no-owner --no-acl --role=devquest --single-transaction --exit-on-error -d $DB
   ```
4. **판정** — 덤프 시점 행 수와 비교한다(`app.jar validate` 는 빈 DB 도 통과시키므로 판정 도구가 아니다, B6-3).
   `common.sh` 는 `set -e` 를 켜므로 대화형 셸에 source 하지 말고 서브 bash 로:
   ```bash
   aws s3 cp s3://$B/prod-db/dumps/$C /tmp/expected
   bash -c ". /opt/devquest-db/common.sh; local_counts $DB" | diff -u /tmp/expected - && echo 일치
   ```
   ⚠️ **2단계 뒤 어디서든 실패하면 되돌린다**(QA F-9·F-10·F-11). 🔴 **남겨 둔 `$OLD` 가 실제로 있을 때만** 새 `$DB` 를 지운다 —
   2단계의 RENAME 이 실패했으면 원본은 아직 `$DB` 이름 그대로라, 확인 없이 DROP 하면 **원본을 지운다**(문장별 autocommit 이라
   `ON_ERROR_STOP` 이 이미 끝난 DROP 을 되돌리지 못한다). 셸을 잃어 `$OLD` 가 비었으면 먼저 찾는다 — 🔴 **여러 개면 멈춘다**(옛 시도의 잔여를 고르면 원본을 지우고 옛 데이터로 되돌린다, 원장 L-75).
   이름 끝의 시각(UTC `YYYYmmddHHMM`)이 **이번 사고 시각**인 하나만 쓴다:
   `runuser -u postgres -- psql -d postgres -Atc "SELECT datname FROM pg_database WHERE datname LIKE 'devquest\_broken\_%' ORDER BY datname DESC"`
   ```bash
   if [ -n "${OLD:-}" ] && [ -n "${DB:-}" ] && \
      [ "$(runuser -u postgres -- psql -X -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname = '$OLD'")" = 1 ]; then
     runuser -u postgres -- psql -X -v ON_ERROR_STOP=1 -d postgres <<SQL
   DROP DATABASE IF EXISTS $DB WITH (FORCE);
   ALTER DATABASE $OLD RENAME TO $DB;
   ALTER DATABASE $DB ALLOW_CONNECTIONS true;
   SQL
   else
     echo "남겨 둔 DB '${OLD:-?}' 를 찾지 못했다(없거나 조회 실패) — RENAME 이 안 됐을 가능성이 크다. 원본은 '${DB:-devquest}' 일 것이다."
     echo "  먼저 확인: runuser -u postgres -- psql -d postgres -Atc \"SELECT datname FROM pg_database\"  — 확인 뒤 접속만 다시 연다:"
     echo "  runuser -u postgres -- psql -d postgres -c 'ALTER DATABASE ${DB:-devquest} ALLOW_CONNECTIONS true'"
   fi
   ```
5. 확인 뒤 남겨 둔 것을 지운다(10GiB 볼륨 — 오래 두지 마라): `runuser -u postgres -- psql -d postgres -c "DROP DATABASE $OLD"`

**EC2 자체가 죽었을 때**: 데이터 볼륨은 별도 EBS(`prevent_destroy`)다. 인스턴스를 교체하면 user_data 가 볼륨을 다시 붙이고 **설정도 볼륨에 있어** 그대로 뜬다(5-A 리허설 통과). 볼륨까지 잃었으면 위 복원 절차를 새 볼륨에서.

**계정이 닫혔을 때**: Neon 콜드 대기(📌D-016 Q-2) — 7번 전환 뒤 매일 복원이 켜지면 Fly 의 DB 연결만 Neon 으로 돌린다. 그 전엔 Neon 이 prod 원본이다.

## Neon 콜드 대기 복원 `standby-restore.sh` (📌D-016 Q-2)

최신 덤프(`.counts` 짝)를 SQL 로 **끝까지 변환한 뒤**, Neon 대기 DB 의 `public` 스키마를 **한 트랜잭션으로 통째 교체**하고 값으로 판정한다
(변환이 실패하면 대기 DB 를 건드리지 않는다 · 적재가 실패하면 이전 복원본이 남는다 · 옛 스키마 잔여 객체도 지워진다). 헬스 키 `health/standby/<UTC>_secs=_tables=`.
- 6-C: 브랜치 `standby-rehearsal` 에 손으로 1회. **전환 뒤: 매일 timer**(install 이 `CUTOVER_DONE=1` 일 때만 켠다). 대기 = **전용 브랜치**(📌D-016 7 결정) — `production` 은 전환 시점 사본으로 동결.
- prod 좌표: 전환 전엔 `neon-source-url`, 그 뒤엔 비밀이 아닌 SSM **`neon-prod-coords`**(String, `호스트/DB`) — source-url 을 지워도 가드가 산다(B7-8).
- 판정에 **시퀀스**도 본다 — 소유 시퀀스의 `last_value ≥ max(컬럼)`(B7-24). 행 수만 맞고 setval 이 틀리면 복귀 후 첫 INSERT 가 충돌한다.
- 🔴 가드: role 이 `devquest_standby`(브랜치에서 **SQL 로** 만든 것) · 접속해서 `neon_superuser` **상속까지** 비소속·superuser 계열 속성 없음 · 대기 엔드포인트 ≠ prod 엔드포인트(대소문자·pooler 정규화) · 대기 DB ≠ prod DB · **대기 자격증명으로 prod 엔드포인트에 붙어 거부되는지 직접 확인**(접속되면 = role 이 prod 에도 있음 → 중단, 거부 사유가 모호해도 중단) · **prod 좌표를 모르면 덮어쓰지 않는다**(SSM `neon-source-url` 과 `NEON_PROD_HOST`·`NEON_PROD_DB` 를 **모두** 대조).
- 접속 정보: SSM `/devquest/prod/db/neon-standby-url` — 사용자가 콘솔로 넣는다(TASKS 의 TASK-15).

## 패치 (B5-8)

Fly·EKS 가 **한 DB** 를 쓰므로 재시작 = 두 경로 동시 장애. 창 **밖**(밤·주말 = Fly 만 붙어 있을 때), 트래픽이 적은 시간에.

```bash
apt-get update && apt-get -s upgrade | grep -i postgres      # 무엇이 바뀌는지 먼저 본다
systemctl start devquest-db-backup.service                   # 직전 백업
apt-get upgrade -y                                           # 마이너 패치(17.x). 메이저 업그레이드는 별도 계획
systemctl restart postgresql@17-main && systemctl is-active postgresql@17-main
```

커널 패치로 재부팅이 필요하면(`/var/run/reboot-required`) 같은 창에서 `reboot` — 부팅 후 WireGuard·Postgres 가 자동으로 뜬다(systemd enable). 설정 튜닝은 재시작이 아니라 **별도 `conf.d` 파일 + `pg_ctlcluster 17 main reload`**.

## 🔴 전환(7-B) 절차 — Neon → EC2 (점검 창, 사용자 승인)

**시각**: 00:00(레이트 리밋 초기화) · 03:30/09:30/15:30/21:30(백업) · 09:00(메일) KST 를 피한다. prod Fly 는 **머신 1대**라 롤링 분기는 없다(7-0).
**사전**: ① TASK-16(사용자) — Neon **앱이 쓰는** 접속 문자열을 SSM **`/devquest/ops/neon-app-url`** 에(중단·복귀용). EC2 역할은 `/devquest/prod/db/*` 만 읽으므로 이 경로는 못 읽는다 — 쓰기 권한 자격증명을 EC2 에 두지 않는다(B6-12).
② SSM **`/devquest/prod/db/neon-prod-coords`**(String, `호스트/DB`, 비밀 아님) — 대기 복원 가드의 좌표(전환 뒤 source-url 을 지워도 가드가 산다). ✅ 2026-10-21 생성. 다시 만들 땐 source-url 에서 **호스트/DB 만** 뽑아(비밀번호 출력 금지):
   `aws ssm get-parameter --name /devquest/prod/db/neon-source-url --with-decryption --query Parameter.Value --output text | sed -nE 's#^postgres(ql)?://[^@]+@([^:/?]+)(:[0-9]+)?/([^?]+).*#\2/\4#p'` → `aws ssm put-parameter --name /devquest/prod/db/neon-prod-coords --type String --value <결과>`

| # | 무엇 | 어디서 | 확인 |
|:-:|---|---|---|
| 0 | `gh workflow disable be-cd.yml` — 전환 중 BE 배포 금지(B7-7) | 로컬 | `gh workflow list` 에 disabled |
| 1 | **Neon 쓰기 동결**: `ALTER ROLE <앱 role> SET default_transaction_read_only = on;` → `SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE usename = '<앱 role>' AND pid <> pg_backend_pid();` (앱 role 확인: `SELECT DISTINCT usename, client_addr FROM pg_stat_activity WHERE datname = 'neondb';`) | Neon SQL Editor(`production`) | 이 순간부터 **쓰기 실패**(몇 분) — 읽기는 된다 |
| 1b | **동결 검증**(QA 7-A F-7) — 앱 자격증명으로 **새 세션**을 열어 `SHOW transaction_read_only;` 가 `on` 인지(`/devquest/ops/neon-app-url` 을 파이프로, 값 출력 금지). `off` 면 동결이 안 됐다 → 2번으로 가지 마라 | 로컬(docker psql) | `on` |
| 2 | `/opt/devquest-db/neon-import.sh --reset` | EC2(SSM) | `✅ … 판정 통과`(행 수·Flyway·**시퀀스**) |
| 2b | **유실 검증** — 읽기 전용 role 로 **지금의** Neon 행 수(`counts_sql`)를 다시 뽑아 EC2 와 비교. 다르면 동결 사이로 쓰기가 샜다 → 2번을 다시 | EC2(SSM) | 차이 0 |
| 3 | `fly secrets import -a devquest-api` ← stdin: `DB_HOST=[<피어 IPv6>]` · `DB_NAME=devquest` · `DB_USERNAME=devquest` · `DB_PASSWORD=<SSM password>` · `STORAGE_DATASOURCE_CORE_MINIMUMIDLE=1` · `STORAGE_DATASOURCE_CORE_KEEPALIVETIME=120000`(B7-26) — **값은 파이프로**(출력 금지). 머신 재시작만, release 는 안 돈다(7-0) | 로컬 | `fly status` started |
| 4 | EC2 에 Fly 가 붙었나: `SELECT client_addr, state FROM pg_stat_activity WHERE client_addr::text LIKE 'fdaa:%';` · 쓰기 확인(QA 7-A F-11): `SELECT sum(n_tup_ins + n_tup_upd + n_tup_del) FROM pg_stat_user_tables;` 를 앱 사용(로그인 1회) **전후**로 — 늘었으면 Fly 가 EC2 에 쓴다 | EC2 | ≥1 행 · 수치 증가 |
| 5 | **그 뒤에** `deploy.sh --cutover-done` — 표식(neon-import 영구 거부) + 대기 timer + `health/cutover/` | 로컬 | `[install] 전환 헬스 키 기록` |
| 6 | 대기 복원 1회 `systemctl start devquest-db-standby.service` · `gh workflow enable be-cd.yml` · `gh workflow run prod-smoke-daily.yml` | EC2 · 로컬 | `✅ Neon 대기 복원 판정 통과` · 스모크 초록 |

**중단(4번 전에 실패하면)** — 3번 뒤엔 앱이 이미 EC2 에 썼을 수 있다: 위 `n_tup_*` 합이 2번 직후보다 늘었으면 그 쓰기는 Neon 으로 돌아가면 잃는다(그 경우 되돌리지 말고 EC2 에서 고친다). 늘지 않았으면 유실 0: ① `fly secrets import -a devquest-api` ← `/devquest/ops/neon-app-url` 을 `DB_*` 4개로(파이프) ② Neon: `SET default_transaction_read_only = off; ALTER ROLE <앱 role> RESET default_transaction_read_only;` — 🔴 앱 role 이 SQL Editor 의 role 과 같으면 새 세션이 읽기 전용이라 **`SET … off` 를 먼저** 쳐야 `ALTER ROLE` 이 된다 ③ `gh workflow enable be-cd.yml`. 표식(5번)을 안 남겼으므로 다시 시도할 수 있다.
**4번 뒤**(EC2 에 새 쓰기가 생긴 뒤)엔 Neon 으로 되돌리면 그 쓰기를 잃는다 — 되돌리지 말고 EC2 에서 고친다.

## 전환(7번) 뒤

- Neon `production` 브랜치는 **읽기 전용으로 동결된 전환 시점 사본** — 지우지 않는다(01-15 이후 탈출 경로 판단 때까지).
- Neon 읽기 전용 role(`devquest_ro`)과 SSM `neon-source-url` 은 쓸 일이 없어진다 — 삭제. 가드 좌표는 `neon-prod-coords` 가 맡는다.
- **계정이 닫혔을 때(복귀)**: Neon 콘솔에서 `devquest_standby` 비밀번호 재설정 → Fly `DB_*` 를 대기 브랜치로 → 스키마가 이미지보다 낮으면 `fly deploy --image <현재>` 로 migrate(시크릿 교체로는 release 가 안 돈다, 7-0). AWS 에 의존하는 단계가 없다(B7-11).

## 테스트

```bash
bash infra/aws-eks/scripts/prod-db/tests/prod-db-test.sh     # docker — 진짜 Postgres 17 + 목 aws, $0
```
